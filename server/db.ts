import { and, desc, eq } from "drizzle-orm";
import { drizzle } from "drizzle-orm/mysql2";
import {
  activityEvents,
  agentTasks,
  conversations,
  InsertUser,
  memories,
  messages,
  mobileDevices,
  projects,
  providerConfigs,
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

export async function updateTaskStep(taskId: number, stepId: number, status: "PENDING" | "RUNNING" | "COMPLETED" | "FAILED" | "SKIPPED") {
  const db = await getDb();
  if (!db) return;
  await db.update(taskSteps).set({ status, updatedAt: new Date() }).where(and(eq(taskSteps.id, stepId), eq(taskSteps.taskId, taskId)));
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

export async function mobileBootstrap(userId: number) {
  const [projectList, taskList, memoryList, activityList] = await Promise.all([listProjects(userId), listTasks(userId), listMemories(userId), listActivity(userId)]);
  return { projects: projectList, tasks: taskList, memories: memoryList, activity: activityList };
}
