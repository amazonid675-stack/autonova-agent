import type { Express } from "express";
import { z } from "zod";
import { createContext } from "./_core/context";
import * as db from "./db";
import { storagePut } from "./storage";

const deviceSchema = z.object({ deviceId: z.string().min(12).max(128), label: z.string().min(1).max(160), pushEnabled: z.boolean().default(false) });
const projectSchema = z.object({ name: z.string().trim().min(1).max(120), description: z.string().trim().max(2000).optional() });
const taskSchema = z.object({ request: z.string().trim().min(1).max(6000), projectId: z.number().int().positive().optional() });
const memorySchema = z.object({ title: z.string().trim().min(1).max(180), content: z.string().trim().min(1).max(12000), layer: z.enum(["SHORT_TERM", "TASK", "PROJECT", "PERSONAL", "DOCUMENT"]).default("PERSONAL") });
const toolSchema = z.object({ policy: z.enum(["ASK", "ALLOW", "DENY"]) });
const fileSchema = z.object({ name: z.string().trim().min(1).max(180), mimeType: z.string().trim().min(1).max(160), dataBase64: z.string().min(4).max(14_000_000) });
type RequestContext = Awaited<ReturnType<typeof createContext>>;
type AuthenticatedMobileContext = RequestContext & { user: NonNullable<RequestContext["user"]> };

async function requireMobileUser(req: Parameters<typeof createContext>[0]["req"], res: Parameters<typeof createContext>[0]["res"]): Promise<AuthenticatedMobileContext | null> {
  const ctx = await createContext({ req, res } as Parameters<typeof createContext>[0]);
  return ctx.user ? ctx as AuthenticatedMobileContext : null;
}

function mobileError(res: Parameters<typeof createContext>[0]["res"], error: unknown) {
  const message = error instanceof Error ? error.message : "Unable to complete the mobile request.";
  return res.status(400).json({ error: message });
}

export function registerMobileApi(app: Express) {
  app.get("/api/mobile/bootstrap", async (req, res) => {
    const ctx = await requireMobileUser(req, res);
    if (!ctx) return res.status(401).json({ error: "Authentication required" });
    return res.json(await db.mobileBootstrap(ctx.user.id));
  });
  app.post("/api/mobile/devices", async (req, res) => {
    const ctx = await requireMobileUser(req, res);
    if (!ctx) return res.status(401).json({ error: "Authentication required" });
    const parsed = deviceSchema.safeParse(req.body);
    if (!parsed.success) return res.status(400).json({ error: "Invalid mobile device registration" });
    const device = await db.registerMobileDevice(ctx.user.id, parsed.data);
    return res.status(201).json({ id: device?.id, deviceId: device?.deviceId, label: device?.label, pushEnabled: Boolean(device?.pushEnabled) });
  });
  app.post("/api/mobile/projects", async (req, res) => {
    const ctx = await requireMobileUser(req, res); if (!ctx) return res.status(401).json({ error: "Authentication required" });
    const parsed = projectSchema.safeParse(req.body); if (!parsed.success) return res.status(400).json({ error: "Invalid project" });
    const project = await db.createProject(ctx.user.id, parsed.data.name, parsed.data.description);
    return project ? res.status(201).json(project) : res.status(503).json({ error: "Workspace storage is unavailable" });
  });
  app.post("/api/mobile/tasks", async (req, res) => {
    const ctx = await requireMobileUser(req, res); if (!ctx) return res.status(401).json({ error: "Authentication required" });
    const parsed = taskSchema.safeParse(req.body); if (!parsed.success) return res.status(400).json({ error: "Invalid task request" });
    if (parsed.data.projectId && !await db.getProject(ctx.user.id, parsed.data.projectId)) return res.status(404).json({ error: "Project not found" });
    const task = await db.createTask(ctx.user.id, parsed.data.request, { projectId: parsed.data.projectId });
    if (task) await db.createActivity(ctx.user.id, { taskId: task.id, eventType: "MOBILE_TASK", title: "Task created from Android", visibility: "STANDARD" });
    return task ? res.status(201).json(task) : res.status(503).json({ error: "Task storage is unavailable" });
  });
  app.post("/api/mobile/memories", async (req, res) => {
    const ctx = await requireMobileUser(req, res); if (!ctx) return res.status(401).json({ error: "Authentication required" });
    const parsed = memorySchema.safeParse(req.body); if (!parsed.success) return res.status(400).json({ error: "Invalid memory" });
    const memory = await db.createMemory(ctx.user.id, parsed.data);
    return memory ? res.status(201).json(memory) : res.status(503).json({ error: "Memory storage is unavailable" });
  });
  app.put("/api/mobile/memories/:memoryId", async (req, res) => {
    const ctx = await requireMobileUser(req, res); if (!ctx) return res.status(401).json({ error: "Authentication required" });
    const memoryId = Number(req.params.memoryId); const parsed = memorySchema.safeParse(req.body);
    if (!Number.isInteger(memoryId) || !parsed.success) return res.status(400).json({ error: "Invalid memory" });
    await db.updateMemory(ctx.user.id, memoryId, parsed.data); return res.status(204).end();
  });
  app.delete("/api/mobile/memories/:memoryId", async (req, res) => {
    const ctx = await requireMobileUser(req, res); if (!ctx) return res.status(401).json({ error: "Authentication required" });
    const memoryId = Number(req.params.memoryId); if (!Number.isInteger(memoryId)) return res.status(400).json({ error: "Invalid memory" });
    await db.deleteMemory(ctx.user.id, memoryId); return res.status(204).end();
  });
  app.put("/api/mobile/tools/:toolKey", async (req, res) => {
    const ctx = await requireMobileUser(req, res); if (!ctx) return res.status(401).json({ error: "Authentication required" });
    const parsed = toolSchema.safeParse(req.body); const toolKey = req.params.toolKey;
    if (!parsed.success || !/^[a-z0-9_.-]{2,80}$/i.test(toolKey)) return res.status(400).json({ error: "Invalid tool permission" });
    await db.setToolPermission(ctx.user.id, toolKey, parsed.data.policy); return res.json({ toolKey, policy: parsed.data.policy });
  });
  app.post("/api/mobile/files", async (req, res) => {
    const ctx = await requireMobileUser(req, res); if (!ctx) return res.status(401).json({ error: "Authentication required" });
    const parsed = fileSchema.safeParse(req.body); if (!parsed.success) return res.status(400).json({ error: "Invalid file payload" });
    try {
      if (!/^[A-Za-z0-9+/]*={0,2}$/.test(parsed.data.dataBase64)) throw new Error("File payload must be base64 encoded.");
      const data = Buffer.from(parsed.data.dataBase64, "base64");
      if (!data.length || data.length > 10 * 1024 * 1024) throw new Error("Files must be between 1 byte and 10 MB.");
      const name = parsed.data.name.replace(/[^a-zA-Z0-9._ -]/g, "_");
      const stored = await storagePut(`mobile/${ctx.user.id}/${name}`, data, parsed.data.mimeType);
      const file = await db.createUploadedFile(ctx.user.id, { name, mimeType: parsed.data.mimeType, sizeBytes: data.length, storageKey: stored.key, storageUrl: stored.url });
      if (!file) throw new Error("File metadata could not be saved.");
      await db.createActivity(ctx.user.id, { eventType: "MOBILE_FILE", title: "File added from Android", detail: name, visibility: "STANDARD" });
      return res.status(201).json(file);
    } catch (error) { return mobileError(res, error); }
  });
}
