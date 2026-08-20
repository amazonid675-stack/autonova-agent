import type { Express } from "express";
import { z } from "zod";
import { createContext } from "./_core/context";
import * as db from "./db";
import { storagePut } from "./storage";
import { generateImage } from "./_core/imageGeneration";
import { encryptSecret } from "./security";

const deviceSchema = z.object({ deviceId: z.string().min(12).max(128), label: z.string().min(1).max(160), pushEnabled: z.boolean().default(false) });
const projectSchema = z.object({ name: z.string().trim().min(1).max(120), description: z.string().trim().max(2000).optional() });
const taskSchema = z.object({ request: z.string().trim().min(1).max(6000), projectId: z.number().int().positive().optional() });
const memorySchema = z.object({ title: z.string().trim().min(1).max(180), content: z.string().trim().min(1).max(12000), layer: z.enum(["SHORT_TERM", "TASK", "PROJECT", "PERSONAL", "DOCUMENT"]).default("PERSONAL") });
const toolSchema = z.object({ policy: z.enum(["ASK", "ALLOW", "DENY"]) });
const fileSchema = z.object({ name: z.string().trim().min(1).max(180), mimeType: z.string().trim().min(1).max(160), dataBase64: z.string().min(4).max(14_000_000) });
const taskActionSchema = z.object({ status: z.enum(["QUEUED", "CANCELLED"]) });
const providerSchema = z.object({ name: z.string().trim().min(1).max(120), providerType: z.enum(["BUILT_IN", "OPENAI_COMPATIBLE"]), baseUrl: z.string().trim().max(500).optional(), activeModel: z.string().trim().max(160).optional(), apiKey: z.string().trim().min(8).max(4000).optional(), costMode: z.enum(["LOCAL_ONLY", "BALANCED", "POWER"]) });
const imageSchema = z.object({ prompt: z.string().trim().min(3).max(1200) });
const githubRepositorySchema = z.string().trim().regex(/^[A-Za-z0-9_.-]+\/[A-Za-z0-9_.-]+$/);
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
  app.put("/api/mobile/tasks/:taskId", async (req, res) => {
    const ctx = await requireMobileUser(req, res); if (!ctx) return res.status(401).json({ error: "Authentication required" });
    const taskId = Number(req.params.taskId); const parsed = taskActionSchema.safeParse(req.body);
    if (!Number.isInteger(taskId) || !parsed.success || !await db.getTask(ctx.user.id, taskId)) return res.status(400).json({ error: "Invalid task action" });
    await db.updateTask(ctx.user.id, taskId, { status: parsed.data.status });
    await db.createActivity(ctx.user.id, { taskId, eventType: "MOBILE_TASK", title: parsed.data.status === "CANCELLED" ? "Task cancelled from Android" : "Task retried from Android", visibility: "STANDARD" });
    return res.status(204).end();
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
  app.put("/api/mobile/provider", async (req, res) => {
    const ctx = await requireMobileUser(req, res); if (!ctx) return res.status(401).json({ error: "Authentication required" });
    const parsed = providerSchema.safeParse(req.body); if (!parsed.success) return res.status(400).json({ error: "Invalid provider configuration" });
    const input = parsed.data;
    if (input.providerType === "OPENAI_COMPATIBLE" && (!input.baseUrl || !input.activeModel || !input.apiKey)) return res.status(400).json({ error: "External providers require endpoint, model, and API key." });
    const provider = await db.saveProvider(ctx.user.id, { ...input, encryptedApiKey: input.apiKey ? encryptSecret(input.apiKey) : undefined });
    if (!provider) return res.status(503).json({ error: "Provider configuration could not be saved" });
    await db.createActivity(ctx.user.id, { eventType: "PROVIDER_UPDATED", title: "Provider updated from Android", detail: "Credential material remains encrypted and is never returned to clients.", visibility: "ADVANCED" });
    return res.json({ name: provider.name, providerType: provider.providerType, activeModel: provider.activeModel, costMode: provider.costMode, hasApiKey: Boolean(provider.encryptedApiKey) });
  });
  app.get("/api/mobile/usage", async (req, res) => {
    const ctx = await requireMobileUser(req, res); if (!ctx) return res.status(401).json({ error: "Authentication required" });
    const records = await db.listUsageRecords(ctx.user.id);
    const totals = records.reduce((sum, record) => ({ inputTokens: sum.inputTokens + record.inputTokens, outputTokens: sum.outputTokens + record.outputTokens, toolCalls: sum.toolCalls + record.toolCalls, estimatedCostMicros: sum.estimatedCostMicros + record.estimatedCostMicros }), { inputTokens: 0, outputTokens: 0, toolCalls: 0, estimatedCostMicros: 0 });
    return res.json({ totals, records: records.slice(0, 20).map(record => ({ id: record.id, model: record.model, inputTokens: record.inputTokens, outputTokens: record.outputTokens, toolCalls: record.toolCalls, estimatedCostMicros: record.estimatedCostMicros })) });
  });
  app.post("/api/mobile/images", async (req, res) => {
    const ctx = await requireMobileUser(req, res); if (!ctx) return res.status(401).json({ error: "Authentication required" });
    const parsed = imageSchema.safeParse(req.body); if (!parsed.success) return res.status(400).json({ error: "Invalid image prompt" });
    try {
      const image = await generateImage({ prompt: parsed.data.prompt });
      await db.createActivity(ctx.user.id, { eventType: "IMAGE_GENERATED", title: "Image generated from Android", detail: "Generated asset is available in the current session.", visibility: "ADVANCED" });
      await db.createUsageRecord(ctx.user.id, { model: "image-generation", inputTokens: 0, outputTokens: 0, toolCalls: 1 });
      return res.status(201).json(image);
    } catch (error) { return mobileError(res, error); }
  });
  app.get("/api/mobile/github", async (req, res) => {
    const ctx = await requireMobileUser(req, res); if (!ctx) return res.status(401).json({ error: "Authentication required" });
    const parsed = githubRepositorySchema.safeParse(req.query.repository); if (!parsed.success) return res.status(400).json({ error: "Use an owner/repository public GitHub name." });
    const repository = parsed.data; const headers = { Accept: "application/vnd.github+json", "User-Agent": "Autonova-Mobile" };
    try {
      const [repoResponse, branches, issues, pulls, commits] = await Promise.all([fetch(`https://api.github.com/repos/${repository}`, { headers }), fetch(`https://api.github.com/repos/${repository}/branches?per_page=6`, { headers }), fetch(`https://api.github.com/repos/${repository}/issues?state=open&per_page=6`, { headers }), fetch(`https://api.github.com/repos/${repository}/pulls?state=open&per_page=6`, { headers }), fetch(`https://api.github.com/repos/${repository}/commits?per_page=5`, { headers })]);
      if (!repoResponse.ok) return res.status(404).json({ error: "This public repository could not be found or is unavailable." });
      return res.json({ repo: await repoResponse.json(), branches: branches.ok ? await branches.json() : [], issues: issues.ok ? await issues.json() : [], pulls: pulls.ok ? await pulls.json() : [], commits: commits.ok ? await commits.json() : [] });
    } catch (error) { return mobileError(res, error); }
  });
}
