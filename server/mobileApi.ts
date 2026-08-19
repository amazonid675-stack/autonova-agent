import type { Express } from "express";
import { z } from "zod";
import { createContext } from "./_core/context";
import * as db from "./db";

const deviceSchema = z.object({ deviceId: z.string().min(12).max(128), label: z.string().min(1).max(160), pushEnabled: z.boolean().default(false) });

export function registerMobileApi(app: Express) {
  app.get("/api/mobile/bootstrap", async (req, res) => {
    const ctx = await createContext({ req, res } as Parameters<typeof createContext>[0]);
    if (!ctx.user) return res.status(401).json({ error: "Authentication required" });
    return res.json(await db.mobileBootstrap(ctx.user.id));
  });
  app.post("/api/mobile/devices", async (req, res) => {
    const ctx = await createContext({ req, res } as Parameters<typeof createContext>[0]);
    if (!ctx.user) return res.status(401).json({ error: "Authentication required" });
    const parsed = deviceSchema.safeParse(req.body);
    if (!parsed.success) return res.status(400).json({ error: "Invalid mobile device registration" });
    const device = await db.registerMobileDevice(ctx.user.id, parsed.data);
    return res.status(201).json({ id: device?.id, deviceId: device?.deviceId, label: device?.label, pushEnabled: Boolean(device?.pushEnabled) });
  });
}
