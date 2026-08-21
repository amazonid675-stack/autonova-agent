import crypto from "crypto";
import type { Express } from "express";
import { lookup } from "node:dns/promises";
import { isIP } from "node:net";
import { z } from "zod";
import { createContext } from "./_core/context";
import * as db from "./db";
import { storagePut } from "./storage";
import { generateImage } from "./_core/imageGeneration";
import { decryptSecret, encryptSecret } from "./security";
import { encodeOAuthState, OAUTH_STATE_COOKIE } from "../shared/const";
import { ENV } from "./_core/env";
import { invokeLLM } from "./_core/llm";

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
const publicSourceSchema = z.string().trim().url().max(2048).refine(value => new URL(value).protocol === "https:", "Research sources must use HTTPS.");
export const researchSchema = z.object({ query: z.string().trim().min(3).max(600), sources: z.array(publicSourceSchema).min(1).max(5) });
const learningCandidateSchema = z.object({ title: z.string().trim().min(1).max(200), content: z.string().trim().min(1).max(12000), layer: z.enum(["TASK", "PROJECT", "PERSONAL", "DOCUMENT"]).default("PERSONAL"), source: z.string().trim().min(1).max(160).default("ANDROID_LOCAL") });
const learningDecisionSchema = z.object({ status: z.enum(["APPROVED", "DISMISSED"]), title: z.string().trim().min(1).max(200).optional(), content: z.string().trim().min(1).max(12000).optional(), layer: z.enum(["TASK", "PROJECT", "PERSONAL", "DOCUMENT"]).optional() });
const capabilityGrantSchema = z.object({ capability: z.string().trim().regex(/^[a-z0-9_.-]{2,100}$/i), scope: z.string().trim().min(1).max(4000), rationale: z.string().trim().max(2000).optional(), expiresAt: z.string().datetime().optional() });
const capabilityGrantDecisionSchema = z.object({ status: z.enum(["APPROVED", "DECLINED", "REVOKED"]) });
export const deviceAuditSchema = z.object({ capability: z.string().trim().regex(/^[a-z0-9_.-]{2,100}$/i), scope: z.string().trim().min(1).max(4000), detail: z.string().trim().min(1).max(2000), outcome: z.enum(["APPROVED", "COMPLETED", "FAILED", "REVOKED"]) });
const githubConnectionSchema = z.object({ token: z.string().trim().min(20).max(4000), scopes: z.string().trim().min(1).max(1000) });
export const githubOperationSchema = z.object({ repository: githubRepositorySchema, operation: z.enum(["CREATE_ISSUE", "CREATE_BRANCH", "CREATE_PULL_REQUEST"]), title: z.string().trim().min(1).max(240).optional(), body: z.string().trim().max(6000).optional(), branch: z.string().trim().regex(/^[A-Za-z0-9._/-]{1,120}$/).optional(), fromBranch: z.string().trim().regex(/^[A-Za-z0-9._/-]{1,120}$/).optional(), head: z.string().trim().regex(/^[A-Za-z0-9._/-]{1,160}$/).optional(), base: z.string().trim().regex(/^[A-Za-z0-9._/-]{1,120}$/).optional() }).superRefine((value, context) => {
  if (value.operation === "CREATE_ISSUE" && !value.title) context.addIssue({ code: z.ZodIssueCode.custom, message: "Issue title is required." });
  if (value.operation === "CREATE_BRANCH" && (!value.branch || !value.fromBranch)) context.addIssue({ code: z.ZodIssueCode.custom, message: "New and source branch names are required." });
  if (value.operation === "CREATE_PULL_REQUEST" && (!value.title || !value.head || !value.base)) context.addIssue({ code: z.ZodIssueCode.custom, message: "Pull-request title, head, and base branches are required." });
});
const mobileLoginSchema = z.object({ serverOrigin: z.string().url(), codeChallenge: z.string().regex(/^[a-f0-9]{64}$/i) });
const mobileExchangeSchema = z.object({ code: z.string().min(32).max(128), codeVerifier: z.string().min(32).max(256) });
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

function requestOrigin(req: { protocol: string; get(name: string): string | undefined; headers: Record<string, unknown> }) {
  const forwarded = req.headers["x-forwarded-proto"];
  const protocol = typeof forwarded === "string" ? forwarded.split(",")[0].trim() : req.protocol;
  return `${protocol}://${req.get("host")}`;
}

export function isPrivateAddress(address: string) {
  const kind = isIP(address);
  if (kind === 4) {
    const [a, b] = address.split(".").map(Number);
    return a === 10 || a === 127 || a === 0 || a === 169 && b === 254 || a === 172 && b >= 16 && b <= 31 || a === 192 && b === 168;
  }
  if (kind === 6) {
    const normalized = address.toLowerCase();
    return normalized === "::1" || normalized.startsWith("fe80:") || normalized.startsWith("fc") || normalized.startsWith("fd");
  }
  return true;
}

async function assertPublicHttpsUrl(input: string) {
  const url = new URL(input);
  if (url.protocol !== "https:" || url.username || url.password || url.port) throw new Error("Research sources must be public HTTPS URLs without embedded credentials or custom ports.");
  if (url.hostname === "localhost" || isIP(url.hostname) && isPrivateAddress(url.hostname)) throw new Error("Private or local network addresses cannot be used as research sources.");
  const addresses = await lookup(url.hostname, { all: true, verbatim: true });
  if (!addresses.length || addresses.some(({ address }) => isPrivateAddress(address))) throw new Error("Research sources must resolve to public internet addresses.");
  return url;
}

function extractPublicText(html: string) {
  const title = html.match(/<title[^>]*>([\s\S]{0,500}?)<\/title>/i)?.[1]?.replace(/<[^>]+>/g, " ").replace(/\s+/g, " ").trim() || "Untitled source";
  const text = html.replace(/<script[\s\S]*?<\/script>/gi, " ").replace(/<style[\s\S]*?<\/style>/gi, " ").replace(/<noscript[\s\S]*?<\/noscript>/gi, " ").replace(/<[^>]+>/g, " ").replace(/&nbsp;/gi, " ").replace(/&amp;/gi, "&").replace(/&lt;/gi, "<").replace(/&gt;/gi, ">").replace(/\s+/g, " ").trim();
  return { title: title.slice(0, 500), excerpt: text.slice(0, 8000) };
}

async function fetchPublicResearchSource(input: string, redirects = 0): Promise<{ url: URL; title: string; excerpt: string }> {
  if (redirects > 2) throw new Error("Source redirected too many times.");
  const url = await assertPublicHttpsUrl(input);
  const response = await fetch(url, { redirect: "manual", signal: AbortSignal.timeout(10_000), headers: { "User-Agent": "Autonova-Research/1.0", Accept: "text/html,application/xhtml+xml,text/plain;q=0.9" } });
  if ([301, 302, 303, 307, 308].includes(response.status)) {
    const location = response.headers.get("location");
    if (!location) throw new Error("Source redirect did not include a destination.");
    return fetchPublicResearchSource(new URL(location, url).toString(), redirects + 1);
  }
  if (!response.ok) throw new Error(`Source returned HTTP ${response.status}.`);
  const contentType = response.headers.get("content-type") || "";
  if (!contentType.includes("text/html") && !contentType.includes("text/plain")) throw new Error("Only public HTML and text sources can be read in this research request.");
  const body = (await response.text()).slice(0, 200_000);
  const extracted = contentType.includes("text/plain") ? { title: url.hostname, excerpt: body.replace(/\s+/g, " ").trim().slice(0, 8000) } : extractPublicText(body);
  return { url, ...extracted };
}

async function summarizeResearch(query: string, sources: Array<{ citationLabel: string; title: string; sourceUrl: string; excerpt: string }>) {
  const evidence = sources.map(source => `${source.citationLabel} ${source.title}\nURL: ${source.sourceUrl}\nExcerpt: ${source.excerpt}`).join("\n\n");
  const response = await invokeLLM({ model: "gpt-5-mini", maxTokens: 1200, messages: [{ role: "system", content: "Summarize only the supplied public sources. State uncertainty, do not invent facts, and cite factual claims using the provided bracketed source labels." }, { role: "user", content: `Research question: ${query}\n\nSources:\n${evidence}` }] });
  const content = response.choices[0]?.message.content;
  if (typeof content !== "string" || !content.trim()) throw new Error("The research model returned no usable summary.");
  return { summary: content.trim(), usage: response.usage };
}

/** Stores a device-side consent outcome as the same server-side grant model used by research and GitHub operations. */
export async function persistDeviceAudit(userId: number, input: z.infer<typeof deviceAuditSchema>) {
  const status = input.outcome === "REVOKED" ? "REVOKED" as const : "APPROVED" as const;
  const grant = await db.createCapabilityGrant(userId, { capability: input.capability, scope: input.scope, rationale: input.detail, outcome: input.outcome });
  if (!grant) return null;
  await db.updateCapabilityGrant(userId, grant.id, status, input.outcome);
  await db.createActivity(userId, { eventType: "DEVICE_CAPABILITY_AUDIT", title: `${input.capability} ${input.outcome.toLowerCase()}`, detail: input.detail, visibility: "STANDARD" });
  return { id: grant.id, capability: grant.capability, scope: grant.scope, status, outcome: input.outcome };
}

export function registerMobileApi(app: Express) {
  app.get("/api/mobile/auth/start", async (req, res) => {
    const parsed = mobileLoginSchema.safeParse(req.query);
    if (!parsed.success || !ENV.oAuthPortalUrl || !ENV.appId) return res.status(400).json({ error: "Mobile sign-in is unavailable." });
    if (new URL(parsed.data.serverOrigin).origin !== requestOrigin(req)) return res.status(400).json({ error: "The mobile server origin must match this Autonova workspace." });
    const nonce = crypto.randomUUID();
    const callbackUrl = `${new URL(parsed.data.serverOrigin).origin}/api/oauth/callback`;
    const state = encodeOAuthState({ redirectUri: callbackUrl, nonce, mobileVerifierHash: parsed.data.codeChallenge });
    res.cookie(OAUTH_STATE_COOKIE, nonce, { httpOnly: true, secure: true, sameSite: "none", path: "/", maxAge: 10 * 60 * 1000 });
    const loginUrl = new URL("app-auth", `${ENV.oAuthPortalUrl.replace(/\/$/, "")}/`);
    loginUrl.searchParams.set("appId", ENV.appId);
    loginUrl.searchParams.set("redirectUri", callbackUrl);
    loginUrl.searchParams.set("state", state);
    loginUrl.searchParams.set("type", "signIn");
    return res.redirect(302, loginUrl.toString());
  });
  app.post("/api/mobile/auth/exchange", async (req, res) => {
    const parsed = mobileExchangeSchema.safeParse(req.body);
    if (!parsed.success) return res.status(400).json({ error: "Invalid mobile sign-in exchange." });
    const grant = await db.consumeMobileAuthGrant(
      crypto.createHash("sha256").update(parsed.data.code).digest("hex"),
      crypto.createHash("sha256").update(parsed.data.codeVerifier).digest("hex"),
    );
    if (!grant) return res.status(401).json({ error: "This mobile sign-in link has expired or was already used." });
    return res.json({ accessToken: decryptSecret(grant.encryptedSessionToken) });
  });
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
  app.post("/api/mobile/research", async (req, res) => {
    const ctx = await requireMobileUser(req, res); if (!ctx) return res.status(401).json({ error: "Authentication required" });
    const parsed = researchSchema.safeParse(req.body); if (!parsed.success) return res.status(400).json({ error: "Research needs a question and one to five public HTTPS source URLs." });
    const grant = await db.createCapabilityGrant(ctx.user.id, { capability: "research.public_sources", scope: parsed.data.sources.join("\n"), rationale: parsed.data.query, outcome: "RUNNING" });
    if (!grant) return res.status(503).json({ error: "Research approval storage is unavailable." });
    await db.updateCapabilityGrant(ctx.user.id, grant.id, "APPROVED", "RUNNING");
    const session = await db.createResearchSession(ctx.user.id, parsed.data.query, grant.id);
    if (!session) return res.status(503).json({ error: "Research storage is unavailable." });
    const fetched: Array<{ citationLabel: string; title: string; sourceUrl: string; excerpt: string }> = [];
    for (let index = 0; index < parsed.data.sources.length; index += 1) {
      const sourceUrl = parsed.data.sources[index];
      const citationLabel = `[${index + 1}]`;
      try {
        const source = await fetchPublicResearchSource(sourceUrl);
        await db.createResearchSource(ctx.user.id, { sessionId: session.id, sourceUrl: source.url.toString(), host: source.url.hostname, title: source.title, excerpt: source.excerpt, citationLabel, fetchStatus: "FETCHED" });
        fetched.push({ citationLabel, title: source.title, sourceUrl: source.url.toString(), excerpt: source.excerpt });
      } catch (error) {
        const url = new URL(sourceUrl);
        await db.createResearchSource(ctx.user.id, { sessionId: session.id, sourceUrl, host: url.hostname, title: "Unavailable source", excerpt: error instanceof Error ? error.message : "Source could not be retrieved.", citationLabel, fetchStatus: "REJECTED" });
      }
    }
    if (!fetched.length) {
      await db.updateResearchSession(ctx.user.id, session.id, { status: "FAILED", errorSummary: "None of the selected public sources could be read." });
      await db.updateCapabilityGrant(ctx.user.id, grant.id, "APPROVED", "FAILED");
      await db.createActivity(ctx.user.id, { eventType: "RESEARCH_FAILED", title: "Research source retrieval failed", detail: parsed.data.query, visibility: "STANDARD" });
      return res.status(201).json({ session: await db.listResearchSessions(ctx.user.id).then(items => items.find(item => item.id === session.id)), sources: await db.listResearchSources(ctx.user.id, session.id) });
    }
    let summary: string;
    try {
      const result = await summarizeResearch(parsed.data.query, fetched);
      summary = result.summary;
      await db.createUsageRecord(ctx.user.id, { model: "gpt-5-mini", inputTokens: result.usage?.prompt_tokens ?? 0, outputTokens: result.usage?.completion_tokens ?? 0, toolCalls: 1 });
    } catch {
      summary = `Collected ${fetched.length} public source${fetched.length === 1 ? "" : "s"} for “${parsed.data.query}”. Review the cited excerpts before drawing a conclusion.\n\n${fetched.map(source => `${source.citationLabel} ${source.title} — ${source.sourceUrl}`).join("\n")}`;
    }
    await db.updateResearchSession(ctx.user.id, session.id, { status: "COMPLETED", summary });
    await db.updateCapabilityGrant(ctx.user.id, grant.id, "APPROVED", "COMPLETED");
    await db.createActivity(ctx.user.id, { eventType: "RESEARCH_COMPLETED", title: "Research completed from selected public sources", detail: parsed.data.query, visibility: "STANDARD" });
    return res.status(201).json({ session: await db.listResearchSessions(ctx.user.id).then(items => items.find(item => item.id === session.id)), sources: await db.listResearchSources(ctx.user.id, session.id) });
  });
  app.post("/api/mobile/learning-candidates", async (req, res) => {
    const ctx = await requireMobileUser(req, res); if (!ctx) return res.status(401).json({ error: "Authentication required" });
    const parsed = learningCandidateSchema.safeParse(req.body); if (!parsed.success) return res.status(400).json({ error: "Invalid learning candidate." });
    const candidate = await db.createLearningCandidate(ctx.user.id, parsed.data);
    if (!candidate) return res.status(503).json({ error: "Learning candidate storage is unavailable." });
    await db.createActivity(ctx.user.id, { eventType: "LEARNING_CANDIDATE", title: "Learning candidate awaiting review", detail: candidate.title, visibility: "STANDARD" });
    return res.status(201).json(candidate);
  });
  app.put("/api/mobile/learning-candidates/:candidateId", async (req, res) => {
    const ctx = await requireMobileUser(req, res); if (!ctx) return res.status(401).json({ error: "Authentication required" });
    const candidateId = Number(req.params.candidateId); const parsed = learningDecisionSchema.safeParse(req.body);
    const candidate = Number.isInteger(candidateId) ? await db.getLearningCandidate(ctx.user.id, candidateId) : null;
    if (!candidate || !parsed.success || candidate.status !== "PENDING") return res.status(400).json({ error: "This learning candidate is unavailable for review." });
    if (parsed.data.status === "DISMISSED") {
      await db.updateLearningCandidate(ctx.user.id, candidateId, { status: "DISMISSED" });
      await db.createActivity(ctx.user.id, { eventType: "LEARNING_DISMISSED", title: "Learning candidate dismissed", detail: candidate.title, visibility: "STANDARD" });
      return res.status(204).end();
    }
    const title = parsed.data.title ?? candidate.title; const content = parsed.data.content ?? candidate.content; const layer = parsed.data.layer ?? candidate.layer;
    const memory = await db.createMemory(ctx.user.id, { title, content, layer });
    if (!memory) return res.status(503).json({ error: "Memory storage is unavailable." });
    await db.updateLearningCandidate(ctx.user.id, candidateId, { status: "APPROVED", memoryId: memory.id, title, content, layer });
    await db.createActivity(ctx.user.id, { eventType: "LEARNING_APPROVED", title: "Learning candidate saved to memory", detail: title, visibility: "STANDARD" });
    return res.json(memory);
  });
  app.post("/api/mobile/capability-grants", async (req, res) => {
    const ctx = await requireMobileUser(req, res); if (!ctx) return res.status(401).json({ error: "Authentication required" });
    const parsed = capabilityGrantSchema.safeParse(req.body); if (!parsed.success) return res.status(400).json({ error: "Invalid capability grant." });
    const grant = await db.createCapabilityGrant(ctx.user.id, { ...parsed.data, expiresAt: parsed.data.expiresAt ? new Date(parsed.data.expiresAt) : null });
    if (!grant) return res.status(503).json({ error: "Capability grant storage is unavailable." });
    await db.createActivity(ctx.user.id, { eventType: "CAPABILITY_GRANT_PROPOSED", title: "Capability grant awaiting review", detail: grant.capability, visibility: "STANDARD" });
    return res.status(201).json(grant);
  });
  app.put("/api/mobile/capability-grants/:grantId", async (req, res) => {
    const ctx = await requireMobileUser(req, res); if (!ctx) return res.status(401).json({ error: "Authentication required" });
    const grantId = Number(req.params.grantId); const parsed = capabilityGrantDecisionSchema.safeParse(req.body);
    if (!Number.isInteger(grantId) || !parsed.success) return res.status(400).json({ error: "Invalid capability-grant decision." });
    await db.updateCapabilityGrant(ctx.user.id, grantId, parsed.data.status);
    await db.createActivity(ctx.user.id, { eventType: "CAPABILITY_GRANT_UPDATED", title: `Capability grant ${parsed.data.status.toLowerCase()}`, detail: String(grantId), visibility: "STANDARD" });
    return res.status(204).end();
  });
  app.post("/api/mobile/device-audit", async (req, res) => {
    const ctx = await requireMobileUser(req, res); if (!ctx) return res.status(401).json({ error: "Authentication required" });
    const parsed = deviceAuditSchema.safeParse(req.body); if (!parsed.success) return res.status(400).json({ error: "Invalid device capability audit record." });
    const audit = await persistDeviceAudit(ctx.user.id, parsed.data);
    if (!audit) return res.status(503).json({ error: "Capability audit storage is unavailable." });
    return res.status(201).json(audit);
  });
  app.post("/api/mobile/github/connection", async (req, res) => {
    const ctx = await requireMobileUser(req, res); if (!ctx) return res.status(401).json({ error: "Authentication required" });
    const parsed = githubConnectionSchema.safeParse(req.body); if (!parsed.success) return res.status(400).json({ error: "A fine-grained GitHub token and its intended scopes are required." });
    try {
      const token = parsed.data.token;
      const response = await fetch("https://api.github.com/user", { headers: { Accept: "application/vnd.github+json", Authorization: `Bearer ${token}`, "User-Agent": "Autonova-Mobile" }, signal: AbortSignal.timeout(10_000) });
      if (!response.ok) return res.status(400).json({ error: "GitHub rejected this token. Confirm its validity and repository scopes." });
      const account = await response.json() as { login?: string };
      if (!account.login) return res.status(400).json({ error: "GitHub did not return an account identity." });
      const connection = await db.saveGitHubConnection(ctx.user.id, { login: account.login, encryptedToken: encryptSecret(token), scopes: parsed.data.scopes });
      await db.createActivity(ctx.user.id, { eventType: "GITHUB_CONNECTED", title: "GitHub account connected", detail: `${account.login}; token material remains encrypted and is never returned to Android.`, visibility: "STANDARD" });
      return res.status(201).json({ login: connection?.login, scopes: connection?.scopes });
    } catch (error) { return mobileError(res, error); }
  });
  app.delete("/api/mobile/github/connection", async (req, res) => {
    const ctx = await requireMobileUser(req, res); if (!ctx) return res.status(401).json({ error: "Authentication required" });
    await db.deleteGitHubConnection(ctx.user.id);
    await db.createActivity(ctx.user.id, { eventType: "GITHUB_DISCONNECTED", title: "GitHub connection removed", detail: "Encrypted GitHub token material was deleted from Autonova.", visibility: "STANDARD" });
    return res.status(204).end();
  });
  app.post("/api/mobile/github/operations", async (req, res) => {
    const ctx = await requireMobileUser(req, res); if (!ctx) return res.status(401).json({ error: "Authentication required" });
    const parsed = githubOperationSchema.safeParse(req.body); if (!parsed.success) return res.status(400).json({ error: "Invalid GitHub operation request." });
    if (!await db.getGitHubConnection(ctx.user.id)) return res.status(400).json({ error: "Connect GitHub with a fine-grained token before requesting repository changes." });
    const grant = await db.createCapabilityGrant(ctx.user.id, { capability: `github.${parsed.data.operation.toLowerCase()}`, scope: parsed.data.repository, rationale: JSON.stringify({ title: parsed.data.title ?? null, branch: parsed.data.branch ?? null, head: parsed.data.head ?? null, base: parsed.data.base ?? null }), outcome: "PROPOSED" });
    if (!grant) return res.status(503).json({ error: "GitHub approval storage is unavailable." });
    const operation = await db.createGitHubOperationRequest(ctx.user.id, { repository: parsed.data.repository, operation: parsed.data.operation, payload: JSON.stringify(parsed.data), capabilityGrantId: grant.id });
    if (!operation) return res.status(503).json({ error: "GitHub operation storage is unavailable." });
    await db.createActivity(ctx.user.id, { eventType: "GITHUB_OPERATION_PROPOSED", title: "GitHub operation awaiting confirmation", detail: `${operation.operation} on ${operation.repository}`, visibility: "STANDARD" });
    return res.status(201).json(operation);
  });
  app.post("/api/mobile/github/operations/:requestId/approve", async (req, res) => {
    const ctx = await requireMobileUser(req, res); if (!ctx) return res.status(401).json({ error: "Authentication required" });
    const requestId = Number(req.params.requestId); const operation = Number.isInteger(requestId) ? await db.getGitHubOperationRequest(ctx.user.id, requestId) : null;
    const connection = await db.getGitHubConnection(ctx.user.id);
    if (!operation || operation.status !== "PENDING" || !connection) return res.status(400).json({ error: "This GitHub operation cannot be approved." });
    await db.updateGitHubOperationRequest(ctx.user.id, requestId, { status: "APPROVED" });
    if (operation.capabilityGrantId) await db.updateCapabilityGrant(ctx.user.id, operation.capabilityGrantId, "APPROVED", "RUNNING");
    try {
      const payload = githubOperationSchema.parse(JSON.parse(operation.payload)); const headers = { Accept: "application/vnd.github+json", Authorization: `Bearer ${decryptSecret(connection.encryptedToken)}`, "User-Agent": "Autonova-Mobile", "Content-Type": "application/json" };
      let resultSummary = "";
      if (payload.operation === "CREATE_ISSUE") {
        const response = await fetch(`https://api.github.com/repos/${payload.repository}/issues`, { method: "POST", headers, body: JSON.stringify({ title: payload.title, body: payload.body || "" }), signal: AbortSignal.timeout(15_000) });
        if (!response.ok) throw new Error(`GitHub issue creation failed with HTTP ${response.status}.`); const issue = await response.json() as { number?: number; html_url?: string }; resultSummary = `Created issue #${issue.number ?? "?"}: ${issue.html_url ?? "GitHub confirmation returned."}`;
      } else if (payload.operation === "CREATE_BRANCH") {
        const ref = await fetch(`https://api.github.com/repos/${payload.repository}/git/ref/heads/${encodeURIComponent(payload.fromBranch!)}`, { headers, signal: AbortSignal.timeout(15_000) });
        if (!ref.ok) throw new Error(`Source branch could not be read (HTTP ${ref.status}).`); const reference = await ref.json() as { object?: { sha?: string } }; if (!reference.object?.sha) throw new Error("GitHub did not return a source commit SHA.");
        const response = await fetch(`https://api.github.com/repos/${payload.repository}/git/refs`, { method: "POST", headers, body: JSON.stringify({ ref: `refs/heads/${payload.branch}`, sha: reference.object.sha }), signal: AbortSignal.timeout(15_000) });
        if (!response.ok) throw new Error(`Branch creation failed with HTTP ${response.status}.`); resultSummary = `Created branch ${payload.branch} from ${payload.fromBranch}.`;
      } else {
        const response = await fetch(`https://api.github.com/repos/${payload.repository}/pulls`, { method: "POST", headers, body: JSON.stringify({ title: payload.title, body: payload.body || "", head: payload.head, base: payload.base }), signal: AbortSignal.timeout(15_000) });
        if (!response.ok) throw new Error(`Pull-request creation failed with HTTP ${response.status}.`); const pull = await response.json() as { number?: number; html_url?: string }; resultSummary = `Created pull request #${pull.number ?? "?"}: ${pull.html_url ?? "GitHub confirmation returned."}`;
      }
      await db.updateGitHubOperationRequest(ctx.user.id, requestId, { status: "COMPLETED", resultSummary });
      if (operation.capabilityGrantId) await db.updateCapabilityGrant(ctx.user.id, operation.capabilityGrantId, "APPROVED", "COMPLETED");
      await db.createActivity(ctx.user.id, { eventType: "GITHUB_OPERATION_COMPLETED", title: "Confirmed GitHub operation completed", detail: resultSummary, visibility: "STANDARD" });
      return res.json({ resultSummary });
    } catch (error) {
      const message = error instanceof Error ? error.message : "GitHub operation failed.";
      await db.updateGitHubOperationRequest(ctx.user.id, requestId, { status: "FAILED", errorSummary: message });
      if (operation.capabilityGrantId) await db.updateCapabilityGrant(ctx.user.id, operation.capabilityGrantId, "APPROVED", "FAILED");
      await db.createActivity(ctx.user.id, { eventType: "GITHUB_OPERATION_FAILED", title: "Confirmed GitHub operation failed", detail: message, visibility: "STANDARD" });
      return mobileError(res, error);
    }
  });
  app.post("/api/mobile/github/operations/:requestId/cancel", async (req, res) => {
    const ctx = await requireMobileUser(req, res); if (!ctx) return res.status(401).json({ error: "Authentication required" });
    const requestId = Number(req.params.requestId); const operation = Number.isInteger(requestId) ? await db.getGitHubOperationRequest(ctx.user.id, requestId) : null;
    if (!operation || operation.status !== "PENDING") return res.status(400).json({ error: "This GitHub operation cannot be cancelled." });
    await db.updateGitHubOperationRequest(ctx.user.id, requestId, { status: "CANCELLED" });
    if (operation.capabilityGrantId) await db.updateCapabilityGrant(ctx.user.id, operation.capabilityGrantId, "REVOKED", "REVOKED");
    await db.createActivity(ctx.user.id, { eventType: "GITHUB_OPERATION_CANCELLED", title: "GitHub operation cancelled", detail: `${operation.operation} on ${operation.repository}`, visibility: "STANDARD" });
    return res.status(204).end();
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
