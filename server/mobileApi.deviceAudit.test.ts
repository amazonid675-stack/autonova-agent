import { beforeEach, describe, expect, it, vi } from "vitest";
import * as db from "./db";
import { deviceAuditSchema, persistDeviceAudit } from "./mobileApi";

describe("protected mobile device-audit persistence", () => {
  beforeEach(() => vi.restoreAllMocks());

  it.each([
    ["APPROVED", "APPROVED"],
    ["COMPLETED", "APPROVED"],
    ["FAILED", "APPROVED"],
    ["REVOKED", "REVOKED"],
  ] as const)("persists a %s outcome with the expected grant status", async (outcome, expectedStatus) => {
    const input = { capability: "background.review", scope: "Android WorkManager", detail: "Review result", outcome };
    vi.spyOn(db, "createCapabilityGrant").mockResolvedValue({ id: 91, capability: input.capability, scope: input.scope } as never);
    const updateGrant = vi.spyOn(db, "updateCapabilityGrant").mockResolvedValue();
    const createActivity = vi.spyOn(db, "createActivity").mockResolvedValue({} as never);

    expect(deviceAuditSchema.safeParse(input).success).toBe(true);
    await expect(persistDeviceAudit(7, input)).resolves.toEqual({ id: 91, capability: input.capability, scope: input.scope, status: expectedStatus, outcome });
    expect(db.createCapabilityGrant).toHaveBeenCalledWith(7, { capability: input.capability, scope: input.scope, rationale: input.detail, outcome });
    expect(updateGrant).toHaveBeenCalledWith(7, 91, expectedStatus, outcome);
    expect(createActivity).toHaveBeenCalledWith(7, { eventType: "DEVICE_CAPABILITY_AUDIT", title: `background.review ${outcome.toLowerCase()}`, detail: input.detail, visibility: "STANDARD" });
  });

  it("rejects malformed audit payloads and does not persist when grant storage is unavailable", async () => {
    expect(deviceAuditSchema.safeParse({ capability: "bad space", scope: "", detail: "", outcome: "UNKNOWN" }).success).toBe(false);
    vi.spyOn(db, "createCapabilityGrant").mockResolvedValue(null);
    const updateGrant = vi.spyOn(db, "updateCapabilityGrant").mockResolvedValue();
    const createActivity = vi.spyOn(db, "createActivity").mockResolvedValue({} as never);
    await expect(persistDeviceAudit(7, { capability: "storage.read", scope: "content://folder", detail: "Revoked by user", outcome: "REVOKED" })).resolves.toBeNull();
    expect(updateGrant).not.toHaveBeenCalled();
    expect(createActivity).not.toHaveBeenCalled();
  });
});
