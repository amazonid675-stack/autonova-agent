import { and, desc, eq, gt, isNull } from "drizzle-orm";
import { drizzle } from "drizzle-orm/mysql2";
import {
  activityEvents,
  agentTasks,
  capabilityGrants,
  conversations,
  githubConnections,
  githubOperationRequests,
  improvementRecords,
  InsertUser,
  learningCandidates,
  memories,
  messages,
  mobileAuthGrants,
  mobileDevices,
  projects,
  providerConfigs,
  researchSessions,
  researchSources,
  taskEvidenceRecords,
  taskSteps,
  toolPermissions,
  uploadedFiles,
  usageRecords,
  users,
} from "../drizzle/schema";
import { ENV } from "./_core/env";

let _db: ReturnType<typeof drizzle> | null = null;

export async function getDb() {
  if (!_db && process.env.DATABASE_URL) {
    try {
      _db = drizzle(process.env.DATABASE_URL);
    } catch {
      _db = null;
    }
  }
  return _db;
}

export async function upsertUser(user: InsertUser): Promise<void> {
  if (!user.openId) throw new Error("User openId is required for upsert");
  const db = await getDb();
  if (!db) return;
  const values: InsertUser = { openId: user.openId, lastSignedIn: user.lastSignedIn ?? new Date() };
  const updateSet: Record<string, unknown> = { lastSignedIn: values.lastSignedIn };
  (["name", "email", "loginMethod"] as const).forEach(field => {
    if (user[field] !== undefined) {
      values[field] = user[field] ?? null;
      updateSet[field] = user[field] ?? null;
    }
  });
  values.role = user.role ?? (user.openId === ENV.ownerOpenId ? "admin" : "user");
  updateSet.role = values.role;
  await db.insert(users).values(values).onDuplicateKeyUpdate({ set: updateSet });
}

export async function getUserByOpenId(openId: string) {
  const db = await getDb();
  if (!db) return undefined;
  const result = await db.select().from(users).where(eq(users.openId, openId)).limit(1);
  return result[0];
}

export async function createMobileAuthGrant(input: { userId: number; codeHash: string; verifierHash: string; encryptedSessionToken: string; expiresAt: Date }) {
  const db = await getDb();
  if (!db) return null;
  await db.insert(mobileAuthGrants).values(input);
  const rows = await db.select().from(mobileAuthGrants).where(eq(mobileAuthGrants.codeHash, input.codeHash)).limit(1);
  return rows[0] ?? null;
}

export async function consumeMobileAuthGrant(codeHash: string, verifierHash: string) {
  const db = await getDb();
  if (!db) return null;
  const rows = await db.select().from(mobileAuthGrants).where(eq(mobileAuthGrants.codeHash, codeHash)).limit(1);
  const grant = rows[0];
  if (!grant || grant.verifierHash !== verifierHash || grant.expiresAt <= new Date() || grant.consumedAt) return null;
  const result = await db.update(mobileAuthGrants).set({ consumedAt: new Date() }).where(and(eq(mobileAuthGrants.id, grant.id), isNull(mobileAuthGrants.consumedAt), gt(mobileAuthGrants.expiresAt, new Date())));
  if ((result as unknown as [{ affectedRows?: number }])[0]?.affectedRows !== 1) return null;
  return grant;
}

export async function listProjects(userId: number) {
  const db = await getDb();
  if (!db) return [];
  return db.select().from(projects).where(eq(projects.userId, userId)).orderBy(desc(projects.updatedAt));
}

export async function createProject(userId: number, name: string, description?: string, color = "violet") {
  const db = await getDb();
  if (!db) return null;
  await db.insert(projects).values({ userId, name, description: description || null, color });
  const rows = await db.select().from(projects).where(and(eq(projects.userId, userId), eq(projects.name, name))).orderBy(desc(projects.id)).limit(1);
  return rows[0] ?? null;
}

export async function getProject(userId: number, projectId: number) {
  const db = await getDb();
  if (!db) return null;
  const rows = await db.select().from(projects).where(and(eq(projects.id, projectId), eq(projects.userId, userId))).limit(1);
  return rows[0] ?? null;
}

export async function listConversations(userId: number, projectId?: number) {
  const db = await getDb();
  if (!db) return [];
  const condition = projectId ? and(eq(conversations.userId, userId), eq(conversations.projectId, projectId)) : eq(conversations.userId, userId);
  return db.select().from(conversations).where(condition).orderBy(desc(conversations.updatedAt));
}

export async function createConversation(userId: number, title: string, projectId?: number) {
  const db = await getDb();
  if (!db) return null;
  await db.insert(conversations).values({ userId, projectId: projectId ?? null, title });
  const rows = await db.select().from(conversations).where(and(eq(conversations.userId, userId), eq(conversations.title, title))).orderBy(desc(conversations.id)).limit(1);
  return rows[0] ?? null;
}

export async function getConversation(userId: number, conversationId: number) {
  const db = await getDb();
  if (!db) return null;
  const rows = await db.select().from(conversations).where(and(eq(conversations.id, conversationId), eq(conversations.userId, userId))).limit(1);
  return rows[0] ?? null;
}

export async function listMessages(userId: number, conversationId: number) {
  const db = await getDb();
  if (!db) return [];
  return db.select().from(messages).where(and(eq(messages.userId, userId), eq(messages.conversationId, conversationId))).orderBy(messages.createdAt);
}

export async function createMessage(userId: number, conversationId: number, role: "user" | "assistant" | "system", content: string, attachmentNames?: string[]) {
  const db = await getDb();
  if (!db) return;
  await db.insert(messages).values({ userId, conversationId, role, content, attachmentNames: attachmentNames?.length ? JSON.stringify(attachmentNames) : null });
  await db.update(conversations).set({ updatedAt: new Date() }).where(eq(conversations.id, conversationId));
}

export async function listTasks(userId: number, projectId?: number) {
  const db = await getDb();
  if (!db) return [];
  const condition = projectId ? and(eq(agentTasks.userId, userId), eq(agentTasks.projectId, projectId)) : eq(agentTasks.userId, userId);
  return db.select().from(agentTasks).where(condition).orderBy(desc(agentTasks.updatedAt));
}

export async function getTask(userId: number, taskId: number) {
  const db = await getDb();
  if (!db) return null;
  const rows = await db.select().from(agentTasks).where(and(eq(agentTasks.id, taskId), eq(agentTasks.userId, userId))).limit(1);
  return rows[0] ?? null;
}

export async function createTask(userId: number, request: string, options: { projectId?: number; conversationId?: number; model?: string }) {
  const db = await getDb();
  if (!db) return null;
  await db.insert(agentTasks).values({ userId, request, projectId: options.projectId ?? null, conversationId: options.conversationId ?? null, model: options.model ?? null, status: "QUEUED" });
  const rows = await db.select().from(agentTasks).where(and(eq(agentTasks.userId, userId), eq(agentTasks.request, request))).orderBy(desc(agentTasks.id)).limit(1);
  return rows[0] ?? null;
}

export async function updateTask(userId: number, taskId: number, values: Partial<{ status: "QUEUED" | "PLANNING" | "RUNNING" | "WAITING_FOR_USER" | "WAITING_FOR_TOOL" | "VERIFYING" | "FAILED" | "COMPLETED" | "CANCELLED"; finalResult: string | null; errorSummary: string | null; completedAt: Date | null; retryCount: number; nextRetryAt: Date | null }>) {
  const db = await getDb();
  if (!db) return;
  await db.update(agentTasks).set({ ...values, updatedAt: new Date() }).where(and(eq(agentTasks.id, taskId), eq(agentTasks.userId, userId)));
}

export async function listTaskSteps(taskId: number) {
  const db = await getDb();
  if (!db) return [];
  return db.select().from(taskSteps).where(eq(taskSteps.taskId, taskId)).orderBy(taskSteps.position);
}

export async function createTaskSteps(taskId: number, steps: Array<{ title: string; detail?: string; toolKey?: string }>) {
  const db = await getDb();
  if (!db || !steps.length) return;
  await db.insert(taskSteps).values(steps.map((step, index) => ({ taskId, position: index + 1, title: step.title, detail: step.detail ?? null, toolKey: step.toolKey ?? null })));
}

export async function appendTaskSteps(taskId: number, startPosition: number, steps: Array<{ title: string; detail?: string; toolKey?: string }>) {
  const db = await getDb();
  if (!db || !steps.length) return;
  await db.insert(taskSteps).values(steps.map((step, index) => ({ taskId, position: startPosition + index + 1, title: step.title, detail: step.detail ?? null, toolKey: step.toolKey ?? null })));
}

export async function updateTaskStep(taskId: number, stepId: number, status: "PENDING" | "RUNNING" | "COMPLETED" | "FAILED" | "SKIPPED") {
  const db = await getDb();
  if (!db) return;
  await db.update(taskSteps).set({ status, updatedAt: new Date() }).where(and(eq(taskSteps.id, stepId), eq(taskSteps.taskId, taskId)));
}

export async function createTaskEvidence(userId: number, input: { taskId: number; kind: "OBSERVATION" | "TOOL_SELECTION" | "TOOL_APPROVAL" | "TOOL_OUTCOME" | "VERIFICATION" | "REPAIR" | "ESCALATION"; toolKey?: string; summary: string; evidence?: string; outcome?: "PENDING" | "APPROVED" | "COMPLETED" | "FAILED" | "DECLINED" }) {
  const db = await getDb();
  if (!db) return null;
  await db.insert(taskEvidenceRecords).values({ userId, taskId: input.taskId, kind: input.kind, toolKey: input.toolKey ?? null, summary: input.summary, evidence: input.evidence ?? null, outcome: input.outcome ?? "PENDING" });
  const rows = await db.select().from(taskEvidenceRecords).where(and(eq(taskEvidenceRecords.userId, userId), eq(taskEvidenceRecords.taskId, input.taskId))).orderBy(desc(taskEvidenceRecords.id)).limit(1);
  return rows[0] ?? null;
}

export async function listTaskEvidence(userId: number, taskId?: number) {
  const db = await getDb();
  if (!db) return [];
  const condition = taskId ? and(eq(taskEvidenceRecords.userId, userId), eq(taskEvidenceRecords.taskId, taskId)) : eq(taskEvidenceRecords.userId, userId);
  return db.select().from(taskEvidenceRecords).where(condition).orderBy(desc(taskEvidenceRecords.createdAt)).limit(160);
}

export async function listMemories(userId: number, projectId?: number) {
  const db = await getDb();
  if (!db) return [];
  const condition = projectId ? and(eq(memories.userId, userId), eq(memories.projectId, projectId)) : eq(memories.userId, userId);
  return db.select().from(memories).where(condition).orderBy(desc(memories.updatedAt));
}

export async function createMemory(userId: number, input: { layer: "SHORT_TERM" | "TASK" | "PROJECT" | "PERSONAL" | "DOCUMENT"; title: string; content: string; projectId?: number; taskId?: number }) {
  const db = await getDb();
  if (!db) return null;
  await db.insert(memories).values({ userId, ...input, projectId: input.projectId ?? null, taskId: input.taskId ?? null });
  const rows = await db.select().from(memories).where(and(eq(memories.userId, userId), eq(memories.title, input.title))).orderBy(desc(memories.id)).limit(1);
  return rows[0] ?? null;
}

export async function updateMemory(userId: number, memoryId: number, values: { title?: string; content?: string; layer?: "SHORT_TERM" | "TASK" | "PROJECT" | "PERSONAL" | "DOCUMENT" }) {
  const db = await getDb();
  if (!db) return;
  await db.update(memories).set({ ...values, updatedAt: new Date() }).where(and(eq(memories.id, memoryId), eq(memories.userId, userId)));
}

export async function deleteMemory(userId: number, memoryId: number) {
  const db = await getDb();
  if (!db) return;
  await db.delete(memories).where(and(eq(memories.id, memoryId), eq(memories.userId, userId)));
}

export async function listToolPermissions(userId: number) {
  const db = await getDb();
  if (!db) return [];
  return db.select().from(toolPermissions).where(eq(toolPermissions.userId, userId));
}

export async function setToolPermission(userId: number, toolKey: string, policy: "ASK" | "ALLOW" | "DENY") {
  const db = await getDb();
  if (!db) return;
  await db.insert(toolPermissions).values({ userId, toolKey, policy }).onDuplicateKeyUpdate({ set: { policy, updatedAt: new Date() } });
}

export async function createActivity(userId: number, input: { taskId?: number; eventType: string; title: string; detail?: string; visibility?: "STANDARD" | "ADVANCED" }) {
  const db = await getDb();
  if (!db) return;
  await db.insert(activityEvents).values({ userId, taskId: input.taskId ?? null, eventType: input.eventType, title: input.title, detail: input.detail ?? null, visibility: input.visibility ?? "STANDARD" });
}

export async function listActivity(userId: number, advanced = false) {
  const db = await getDb();
  if (!db) return [];
  const condition = advanced ? eq(activityEvents.userId, userId) : and(eq(activityEvents.userId, userId), eq(activityEvents.visibility, "STANDARD"));
  return db.select().from(activityEvents).where(condition).orderBy(desc(activityEvents.createdAt)).limit(80);
}

export async function createUploadedFile(userId: number, input: { projectId?: number; name: string; mimeType: string; sizeBytes: number; storageKey: string; storageUrl: string }) {
  const db = await getDb();
  if (!db) return null;
  await db.insert(uploadedFiles).values({ userId, ...input, projectId: input.projectId ?? null });
  const rows = await db.select().from(uploadedFiles).where(and(eq(uploadedFiles.userId, userId), eq(uploadedFiles.storageKey, input.storageKey))).limit(1);
  return rows[0] ?? null;
}

export async function listUploadedFiles(userId: number, projectId?: number) {
  const db = await getDb();
  if (!db) return [];
  const condition = projectId ? and(eq(uploadedFiles.userId, userId), eq(uploadedFiles.projectId, projectId)) : eq(uploadedFiles.userId, userId);
  return db.select().from(uploadedFiles).where(condition).orderBy(desc(uploadedFiles.createdAt));
}

export async function findUploadedFilesByNames(userId: number, names: string[]) {
  const files = await listUploadedFiles(userId);
  return files.filter(file => names.includes(file.name));
}

export async function latestProvider(userId: number) {
  const db = await getDb();
  if (!db) return null;
  const rows = await db.select().from(providerConfigs).where(and(eq(providerConfigs.userId, userId), eq(providerConfigs.isActive, 1))).orderBy(desc(providerConfigs.updatedAt)).limit(1);
  return rows[0] ?? null;
}

export function describeProviderCapabilities(provider: { providerType: "BUILT_IN" | "OPENAI_COMPATIBLE"; activeModel: string | null; costMode: "LOCAL_ONLY" | "BALANCED" | "POWER" } | null) {
  const remoteConfigured = Boolean(provider && provider.costMode !== "LOCAL_ONLY" && provider.activeModel);
  return [
    { modality: "TEXT", route: remoteConfigured ? "OPTIONAL_REMOTE" : "LOCAL_DEVICE", availability: remoteConfigured ? "CONFIGURED" : "REQUIRES_LOCAL_MODEL", note: remoteConfigured ? "Configured remote text model is optional; Android local model remains the offline path." : "Requires an imported compatible Android local model." },
    { modality: "VISION", route: "LOCAL_OR_REMOTE", availability: "REQUIRES_COMPATIBLE_MODEL", note: "Camera and screenshot capture are device-native; understanding requires a compatible local vision model or explicit optional remote analysis." },
    { modality: "EMBEDDINGS", route: "LOCAL_DEVICE", availability: "LEXICAL_FALLBACK", note: "Local document retrieval uses lexical chunks; vector embeddings require a compatible user-supplied embedding provider." },
    { modality: "SPEECH", route: "ANDROID_SERVICE", availability: "DEVICE_DEPENDENT", note: "Speech recognition and TTS use Android-installed services and may be offline or provider-dependent." },
    { modality: "IMAGE", route: "OPTIONAL_REMOTE", availability: remoteConfigured ? "PROVIDER_DEPENDENT" : "NOT_CONFIGURED", note: "Image generation requires an explicitly connected provider; no image model is bundled in the APK." },
  ];
}

export async function saveProvider(userId: number, input: { name: string; providerType: "BUILT_IN" | "OPENAI_COMPATIBLE"; baseUrl?: string; activeModel?: string; encryptedApiKey?: string; costMode: "LOCAL_ONLY" | "BALANCED" | "POWER" }) {
  const db = await getDb();
  if (!db) return null;
  await db.update(providerConfigs).set({ isActive: 0 }).where(eq(providerConfigs.userId, userId));
  await db.insert(providerConfigs).values({ userId, name: input.name, providerType: input.providerType, baseUrl: input.baseUrl || null, activeModel: input.activeModel || null, encryptedApiKey: input.encryptedApiKey || null, costMode: input.costMode, isActive: 1 });
  return latestProvider(userId);
}

export async function createUsageRecord(userId: number, input: { taskId?: number; model: string; inputTokens: number; outputTokens: number; estimatedCostMicros?: number; toolCalls?: number }) {
  const db = await getDb();
  if (!db) return;
  await db.insert(usageRecords).values({ userId, taskId: input.taskId ?? null, model: input.model, inputTokens: input.inputTokens, outputTokens: input.outputTokens, estimatedCostMicros: input.estimatedCostMicros ?? 0, toolCalls: input.toolCalls ?? 0 });
}

export async function listUsageRecords(userId: number) {
  const db = await getDb();
  if (!db) return [];
  return db.select().from(usageRecords).where(eq(usageRecords.userId, userId)).orderBy(desc(usageRecords.createdAt)).limit(100);
}

export async function registerMobileDevice(userId: number, input: { deviceId: string; label: string; pushEnabled: boolean }) {
  const db = await getDb();
  if (!db) return null;
  await db.insert(mobileDevices).values({ userId, deviceId: input.deviceId, label: input.label, pushEnabled: input.pushEnabled ? 1 : 0 }).onDuplicateKeyUpdate({ set: { label: input.label, pushEnabled: input.pushEnabled ? 1 : 0, lastSyncedAt: new Date(), updatedAt: new Date() } });
  const rows = await db.select().from(mobileDevices).where(and(eq(mobileDevices.userId, userId), eq(mobileDevices.deviceId, input.deviceId))).limit(1);
  return rows[0] ?? null;
}

export async function createResearchSession(userId: number, query: string, capabilityGrantId?: number) {
  const db = await getDb();
  if (!db) return null;
  await db.insert(researchSessions).values({ userId, query, capabilityGrantId: capabilityGrantId ?? null, status: "RUNNING" });
  const rows = await db.select().from(researchSessions).where(and(eq(researchSessions.userId, userId), eq(researchSessions.query, query))).orderBy(desc(researchSessions.id)).limit(1);
  return rows[0] ?? null;
}

export async function updateResearchSession(userId: number, sessionId: number, values: Partial<{ status: "DRAFT" | "RUNNING" | "COMPLETED" | "FAILED"; summary: string | null; errorSummary: string | null }>) {
  const db = await getDb();
  if (!db) return;
  await db.update(researchSessions).set({ ...values, updatedAt: new Date() }).where(and(eq(researchSessions.id, sessionId), eq(researchSessions.userId, userId)));
}

export async function createResearchSource(userId: number, input: { sessionId: number; sourceUrl: string; host: string; title?: string; excerpt?: string; citationLabel: string; fetchStatus: "PENDING" | "FETCHED" | "REJECTED" | "FAILED" }) {
  const db = await getDb();
  if (!db) return null;
  await db.insert(researchSources).values({ userId, ...input, title: input.title ?? null, excerpt: input.excerpt ?? null });
  const rows = await db.select().from(researchSources).where(and(eq(researchSources.userId, userId), eq(researchSources.sessionId, input.sessionId), eq(researchSources.citationLabel, input.citationLabel))).limit(1);
  return rows[0] ?? null;
}

export async function listResearchSessions(userId: number) {
  const db = await getDb();
  if (!db) return [];
  return db.select().from(researchSessions).where(eq(researchSessions.userId, userId)).orderBy(desc(researchSessions.updatedAt)).limit(40);
}

export async function listResearchSources(userId: number, sessionId: number) {
  const db = await getDb();
  if (!db) return [];
  return db.select().from(researchSources).where(and(eq(researchSources.userId, userId), eq(researchSources.sessionId, sessionId))).orderBy(researchSources.id);
}

export async function createLearningCandidate(userId: number, input: { title: string; content: string; layer?: "TASK" | "PROJECT" | "PERSONAL" | "DOCUMENT"; source?: string }) {
  const db = await getDb();
  if (!db) return null;
  await db.insert(learningCandidates).values({ userId, title: input.title, content: input.content, layer: input.layer ?? "PERSONAL", source: input.source ?? "ANDROID_LOCAL" });
  const rows = await db.select().from(learningCandidates).where(and(eq(learningCandidates.userId, userId), eq(learningCandidates.title, input.title), eq(learningCandidates.content, input.content))).orderBy(desc(learningCandidates.id)).limit(1);
  return rows[0] ?? null;
}

export async function listLearningCandidates(userId: number) {
  const db = await getDb();
  if (!db) return [];
  return db.select().from(learningCandidates).where(eq(learningCandidates.userId, userId)).orderBy(desc(learningCandidates.updatedAt)).limit(80);
}

export async function getLearningCandidate(userId: number, candidateId: number) {
  const db = await getDb();
  if (!db) return null;
  const rows = await db.select().from(learningCandidates).where(and(eq(learningCandidates.id, candidateId), eq(learningCandidates.userId, userId))).limit(1);
  return rows[0] ?? null;
}

export async function updateLearningCandidate(userId: number, candidateId: number, values: Partial<{ status: "PENDING" | "APPROVED" | "DISMISSED"; memoryId: number | null; title: string; content: string; layer: "TASK" | "PROJECT" | "PERSONAL" | "DOCUMENT" }>) {
  const db = await getDb();
  if (!db) return;
  await db.update(learningCandidates).set({ ...values, updatedAt: new Date() }).where(and(eq(learningCandidates.id, candidateId), eq(learningCandidates.userId, userId)));
}

export async function createImprovementRecord(userId: number, input: { scope: "PROMPT" | "TOOL" | "WORKFLOW" | "MODEL_ROUTING"; title: string; proposedChange: string; evidence: string; testOutcome?: string; benchmarkSummary?: string; versionLabel: string }) {
  const db = await getDb();
  if (!db) return null;
  await db.insert(improvementRecords).values({ userId, ...input, testOutcome: input.testOutcome ?? null, benchmarkSummary: input.benchmarkSummary ?? null });
  const rows = await db.select().from(improvementRecords).where(and(eq(improvementRecords.userId, userId), eq(improvementRecords.title, input.title), eq(improvementRecords.versionLabel, input.versionLabel))).orderBy(desc(improvementRecords.id)).limit(1);
  return rows[0] ?? null;
}

export async function listImprovementRecords(userId: number) {
  const db = await getDb();
  if (!db) return [];
  return db.select().from(improvementRecords).where(eq(improvementRecords.userId, userId)).orderBy(desc(improvementRecords.updatedAt)).limit(80);
}

export async function getImprovementRecord(userId: number, recordId: number) {
  const db = await getDb();
  if (!db) return null;
  const rows = await db.select().from(improvementRecords).where(and(eq(improvementRecords.id, recordId), eq(improvementRecords.userId, userId))).limit(1);
  return rows[0] ?? null;
}

export async function updateImprovementRecord(userId: number, recordId: number, values: Partial<{ status: "PENDING" | "APPROVED" | "REJECTED" | "ROLLED_BACK"; reviewNote: string | null; approvedAt: Date | null; rolledBackAt: Date | null }>) {
  const db = await getDb();
  if (!db) return;
  await db.update(improvementRecords).set({ ...values, updatedAt: new Date() }).where(and(eq(improvementRecords.id, recordId), eq(improvementRecords.userId, userId)));
}

export async function createCapabilityGrant(userId: number, input: { capability: string; scope: string; rationale?: string; outcome?: string; expiresAt?: Date | null }) {
  const db = await getDb();
  if (!db) return null;
  await db.insert(capabilityGrants).values({ userId, capability: input.capability, scope: input.scope, rationale: input.rationale ?? null, outcome: input.outcome ?? null, expiresAt: input.expiresAt ?? null });
  const rows = await db.select().from(capabilityGrants).where(and(eq(capabilityGrants.userId, userId), eq(capabilityGrants.capability, input.capability), eq(capabilityGrants.scope, input.scope))).orderBy(desc(capabilityGrants.id)).limit(1);
  return rows[0] ?? null;
}

export async function listCapabilityGrants(userId: number) {
  const db = await getDb();
  if (!db) return [];
  return db.select().from(capabilityGrants).where(eq(capabilityGrants.userId, userId)).orderBy(desc(capabilityGrants.updatedAt)).limit(80);
}

export async function updateCapabilityGrant(userId: number, grantId: number, status: "PENDING" | "APPROVED" | "DECLINED" | "REVOKED", outcome?: string) {
  const db = await getDb();
  if (!db) return;
  await db.update(capabilityGrants).set({ status, outcome: outcome ?? null, updatedAt: new Date() }).where(and(eq(capabilityGrants.id, grantId), eq(capabilityGrants.userId, userId)));
}

export async function saveGitHubConnection(userId: number, input: { login: string; encryptedToken: string; scopes: string }) {
  const db = await getDb();
  if (!db) return null;
  await db.insert(githubConnections).values({ userId, ...input }).onDuplicateKeyUpdate({ set: { ...input, updatedAt: new Date() } });
  const rows = await db.select().from(githubConnections).where(eq(githubConnections.userId, userId)).limit(1);
  return rows[0] ?? null;
}

export async function getGitHubConnection(userId: number) {
  const db = await getDb();
  if (!db) return null;
  const rows = await db.select().from(githubConnections).where(eq(githubConnections.userId, userId)).limit(1);
  return rows[0] ?? null;
}

export async function deleteGitHubConnection(userId: number) {
  const db = await getDb();
  if (!db) return;
  await db.delete(githubConnections).where(eq(githubConnections.userId, userId));
}

export async function createGitHubOperationRequest(userId: number, input: { repository: string; operation: "CREATE_ISSUE" | "CREATE_BRANCH" | "CREATE_PULL_REQUEST" | "WRITE_WORKSPACE_FILE"; payload: string; capabilityGrantId?: number }) {
  const db = await getDb();
  if (!db) return null;
  await db.insert(githubOperationRequests).values({ userId, ...input, capabilityGrantId: input.capabilityGrantId ?? null });
  const rows = await db.select().from(githubOperationRequests).where(and(eq(githubOperationRequests.userId, userId), eq(githubOperationRequests.repository, input.repository), eq(githubOperationRequests.payload, input.payload))).orderBy(desc(githubOperationRequests.id)).limit(1);
  return rows[0] ?? null;
}

export async function listGitHubOperationRequests(userId: number) {
  const db = await getDb();
  if (!db) return [];
  return db.select().from(githubOperationRequests).where(eq(githubOperationRequests.userId, userId)).orderBy(desc(githubOperationRequests.updatedAt)).limit(40);
}

export async function getGitHubOperationRequest(userId: number, requestId: number) {
  const db = await getDb();
  if (!db) return null;
  const rows = await db.select().from(githubOperationRequests).where(and(eq(githubOperationRequests.id, requestId), eq(githubOperationRequests.userId, userId))).limit(1);
  return rows[0] ?? null;
}

export async function updateGitHubOperationRequest(userId: number, requestId: number, values: Partial<{ status: "PENDING" | "APPROVED" | "COMPLETED" | "FAILED" | "CANCELLED"; resultSummary: string | null; errorSummary: string | null }>) {
  const db = await getDb();
  if (!db) return;
  await db.update(githubOperationRequests).set({ ...values, updatedAt: new Date() }).where(and(eq(githubOperationRequests.id, requestId), eq(githubOperationRequests.userId, userId)));
}

export async function mobileBootstrap(userId: number) {
  const [projectList, taskList, memoryList, activityList, fileList, permissions, provider, research, candidates, grants, githubConnection, githubOperations, improvements, taskEvidence] = await Promise.all([listProjects(userId), listTasks(userId), listMemories(userId), listActivity(userId), listUploadedFiles(userId), listToolPermissions(userId), latestProvider(userId), listResearchSessions(userId), listLearningCandidates(userId), listCapabilityGrants(userId), getGitHubConnection(userId), listGitHubOperationRequests(userId), listImprovementRecords(userId), listTaskEvidence(userId)]);
  return { projects: projectList, tasks: taskList, memories: memoryList, activity: activityList, files: fileList, toolPermissions: permissions, provider: provider ? { name: provider.name, providerType: provider.providerType, activeModel: provider.activeModel, costMode: provider.costMode, hasApiKey: Boolean(provider.encryptedApiKey) } : null, modelCapabilities: describeProviderCapabilities(provider), research, learningCandidates: candidates, capabilityGrants: grants, githubConnection: githubConnection ? { login: githubConnection.login, scopes: githubConnection.scopes } : null, githubOperations, improvements, taskEvidence };
}
