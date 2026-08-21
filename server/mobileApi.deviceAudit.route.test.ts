import express from "express";
import type { Server } from "node:http";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("./_core/context", () => ({ createContext: vi.fn() }));

import { createContext } from "./_core/context";
import * as db from "./db";
import { registerMobileApi } from "./mobileApi";

let server: Server | undefined;
let activeUser: { id: number } | null;

async function startServer() {
  const app = express();
  app.use(express.json());
  registerMobileApi(app);
  server = await new Promise<Server>(resolve => {
    const instance = app.listen(0, "127.0.0.1", () => resolve(instance));
  });
  const address = server.address();
  if (!address || typeof address === "string") throw new Error("Test server did not expose a TCP address.");
  return `http://127.0.0.1:${address.port}`;
}

async function postDeviceAudit(baseUrl: string, body: unknown) {
  return fetch(`${baseUrl}/api/mobile/device-audit`, { method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify(body) });
}

describe("POST /api/mobile/device-audit", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    activeUser = { id: 17 };
    vi.mocked(createContext).mockImplementation(async () => ({ user: activeUser } as never));
  });

  afterEach(async () => {
    if (server) await new Promise<void>(resolve => server?.close(() => resolve()));
    server = undefined;
  });

  it("rejects unauthenticated requests before reading or writing audit data", async () => {
    activeUser = null;
    const createGrant = vi.spyOn(db, "createCapabilityGrant");
    const response = await postDeviceAudit(await startServer(), { capability: "clipboard.import", scope: "Android clipboard", detail: "User approved", outcome: "APPROVED" });
    expect(response.status).toBe(401);
    expect(createGrant).not.toHaveBeenCalled();
  });

  it("returns 400 for malformed device-audit payloads", async () => {
    const response = await postDeviceAudit(await startServer(), { capability: "bad space", scope: "", detail: "", outcome: "UNKNOWN" });
    expect(response.status).toBe(400);
    await expect(response.json()).resolves.toEqual({ error: "Invalid device capability audit record." });
  });

  it.each([
    ["APPROVED", "APPROVED"],
    ["COMPLETED", "APPROVED"],
    ["FAILED", "APPROVED"],
    ["REVOKED", "REVOKED"],
  ] as const)("returns 201 and persists the %s outcome", async (outcome, expectedStatus) => {
    vi.spyOn(db, "createCapabilityGrant").mockResolvedValue({ id: 43, capability: "background.review", scope: "Android WorkManager" } as never);
    const updateGrant = vi.spyOn(db, "updateCapabilityGrant").mockResolvedValue();
    const createActivity = vi.spyOn(db, "createActivity").mockResolvedValue({} as never);
    const response = await postDeviceAudit(await startServer(), { capability: "background.review", scope: "Android WorkManager", detail: "Outcome observed", outcome });
    expect(response.status).toBe(201);
    await expect(response.json()).resolves.toEqual({ id: 43, capability: "background.review", scope: "Android WorkManager", status: expectedStatus, outcome });
    expect(updateGrant).toHaveBeenCalledWith(17, 43, expectedStatus, outcome);
    expect(createActivity).toHaveBeenCalledOnce();
  });

  it("returns 503 when the capability-grant store is unavailable", async () => {
    vi.spyOn(db, "createCapabilityGrant").mockResolvedValue(null);
    const response = await postDeviceAudit(await startServer(), { capability: "storage.read", scope: "content://folder", detail: "User revoked folder access", outcome: "REVOKED" });
    expect(response.status).toBe(503);
    await expect(response.json()).resolves.toEqual({ error: "Capability audit storage is unavailable." });
  });
});
