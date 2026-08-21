import {
  int,
  mysqlEnum,
  mysqlTable,
  text,
  timestamp,
  uniqueIndex,
  varchar,
} from "drizzle-orm/mysql-core";

export const users = mysqlTable("users", {
  id: int("id").autoincrement().primaryKey(),
  openId: varchar("openId", { length: 64 }).notNull().unique(),
  name: text("name"),
  email: varchar("email", { length: 320 }),
  loginMethod: varchar("loginMethod", { length: 64 }),
  role: mysqlEnum("role", ["user", "admin"]).default("user").notNull(),
  createdAt: timestamp("createdAt").defaultNow().notNull(),
  updatedAt: timestamp("updatedAt").defaultNow().onUpdateNow().notNull(),
  lastSignedIn: timestamp("lastSignedIn").defaultNow().notNull(),
});

export const projects = mysqlTable("projects", {
  id: int("id").autoincrement().primaryKey(),
  userId: int("userId").notNull().references(() => users.id, { onDelete: "cascade" }),
  name: varchar("name", { length: 160 }).notNull(),
  description: text("description"),
  color: varchar("color", { length: 16 }).default("violet").notNull(),
  createdAt: timestamp("createdAt").defaultNow().notNull(),
  updatedAt: timestamp("updatedAt").defaultNow().onUpdateNow().notNull(),
});

export const conversations = mysqlTable("conversations", {
  id: int("id").autoincrement().primaryKey(),
  userId: int("userId").notNull().references(() => users.id, { onDelete: "cascade" }),
  projectId: int("projectId").references(() => projects.id, { onDelete: "set null" }),
  title: varchar("title", { length: 240 }).notNull(),
  createdAt: timestamp("createdAt").defaultNow().notNull(),
  updatedAt: timestamp("updatedAt").defaultNow().onUpdateNow().notNull(),
});

export const messages = mysqlTable("messages", {
  id: int("id").autoincrement().primaryKey(),
  conversationId: int("conversationId").notNull().references(() => conversations.id, { onDelete: "cascade" }),
  userId: int("userId").notNull().references(() => users.id, { onDelete: "cascade" }),
  role: mysqlEnum("role", ["user", "assistant", "system"]).notNull(),
  content: text("content").notNull(),
  attachmentNames: text("attachmentNames"),
  createdAt: timestamp("createdAt").defaultNow().notNull(),
});

export const agentTasks = mysqlTable("agentTasks", {
  id: int("id").autoincrement().primaryKey(),
  userId: int("userId").notNull().references(() => users.id, { onDelete: "cascade" }),
  projectId: int("projectId").references(() => projects.id, { onDelete: "set null" }),
  conversationId: int("conversationId").references(() => conversations.id, { onDelete: "set null" }),
  request: text("request").notNull(),
  status: mysqlEnum("status", ["QUEUED", "PLANNING", "RUNNING", "WAITING_FOR_USER", "WAITING_FOR_TOOL", "VERIFYING", "FAILED", "COMPLETED", "CANCELLED"]).default("QUEUED").notNull(),
  model: varchar("model", { length: 160 }),
  finalResult: text("finalResult"),
  errorSummary: text("errorSummary"),
  retryCount: int("retryCount").default(0).notNull(),
  nextRetryAt: timestamp("nextRetryAt"),
  createdAt: timestamp("createdAt").defaultNow().notNull(),
  updatedAt: timestamp("updatedAt").defaultNow().onUpdateNow().notNull(),
  completedAt: timestamp("completedAt"),
});

export const taskSteps = mysqlTable("taskSteps", {
  id: int("id").autoincrement().primaryKey(),
  taskId: int("taskId").notNull().references(() => agentTasks.id, { onDelete: "cascade" }),
  position: int("position").notNull(),
  title: varchar("title", { length: 320 }).notNull(),
  detail: text("detail"),
  status: mysqlEnum("status", ["PENDING", "RUNNING", "COMPLETED", "FAILED", "SKIPPED"]).default("PENDING").notNull(),
  toolKey: varchar("toolKey", { length: 80 }),
  createdAt: timestamp("createdAt").defaultNow().notNull(),
  updatedAt: timestamp("updatedAt").defaultNow().onUpdateNow().notNull(),
});

export const taskEvidenceRecords = mysqlTable("taskEvidenceRecords", {
  id: int("id").autoincrement().primaryKey(),
  taskId: int("taskId").notNull().references(() => agentTasks.id, { onDelete: "cascade" }),
  userId: int("userId").notNull().references(() => users.id, { onDelete: "cascade" }),
  kind: mysqlEnum("kind", ["OBSERVATION", "TOOL_SELECTION", "TOOL_APPROVAL", "TOOL_OUTCOME", "VERIFICATION", "REPAIR", "ESCALATION"]).notNull(),
  toolKey: varchar("toolKey", { length: 80 }),
  summary: text("summary").notNull(),
  evidence: text("evidence"),
  outcome: mysqlEnum("outcome", ["PENDING", "APPROVED", "COMPLETED", "FAILED", "DECLINED"]).default("PENDING").notNull(),
  createdAt: timestamp("createdAt").defaultNow().notNull(),
});

export const memories = mysqlTable("memories", {
  id: int("id").autoincrement().primaryKey(),
  userId: int("userId").notNull().references(() => users.id, { onDelete: "cascade" }),
  projectId: int("projectId").references(() => projects.id, { onDelete: "set null" }),
  taskId: int("taskId").references(() => agentTasks.id, { onDelete: "set null" }),
  layer: mysqlEnum("layer", ["SHORT_TERM", "TASK", "PROJECT", "PERSONAL", "DOCUMENT"]).notNull(),
  title: varchar("title", { length: 200 }).notNull(),
  content: text("content").notNull(),
  createdAt: timestamp("createdAt").defaultNow().notNull(),
  updatedAt: timestamp("updatedAt").defaultNow().onUpdateNow().notNull(),
});

export const toolPermissions = mysqlTable("toolPermissions", {
  id: int("id").autoincrement().primaryKey(),
  userId: int("userId").notNull().references(() => users.id, { onDelete: "cascade" }),
  toolKey: varchar("toolKey", { length: 80 }).notNull(),
  policy: mysqlEnum("policy", ["ASK", "ALLOW", "DENY"]).default("ASK").notNull(),
  updatedAt: timestamp("updatedAt").defaultNow().onUpdateNow().notNull(),
}, table => ({
  userToolUnique: uniqueIndex("tool_permissions_user_tool_unique").on(table.userId, table.toolKey),
}));

export const activityEvents = mysqlTable("activityEvents", {
  id: int("id").autoincrement().primaryKey(),
  userId: int("userId").notNull().references(() => users.id, { onDelete: "cascade" }),
  taskId: int("taskId").references(() => agentTasks.id, { onDelete: "set null" }),
  eventType: varchar("eventType", { length: 80 }).notNull(),
  title: varchar("title", { length: 240 }).notNull(),
  detail: text("detail"),
  visibility: mysqlEnum("visibility", ["STANDARD", "ADVANCED"]).default("STANDARD").notNull(),
  createdAt: timestamp("createdAt").defaultNow().notNull(),
});

export const uploadedFiles = mysqlTable("uploadedFiles", {
  id: int("id").autoincrement().primaryKey(),
  userId: int("userId").notNull().references(() => users.id, { onDelete: "cascade" }),
  projectId: int("projectId").references(() => projects.id, { onDelete: "set null" }),
  name: varchar("name", { length: 255 }).notNull(),
  mimeType: varchar("mimeType", { length: 160 }).notNull(),
  sizeBytes: int("sizeBytes").notNull(),
  storageKey: varchar("storageKey", { length: 500 }).notNull(),
  storageUrl: varchar("storageUrl", { length: 700 }).notNull(),
  createdAt: timestamp("createdAt").defaultNow().notNull(),
});

export const providerConfigs = mysqlTable("providerConfigs", {
  id: int("id").autoincrement().primaryKey(),
  userId: int("userId").notNull().references(() => users.id, { onDelete: "cascade" }),
  name: varchar("name", { length: 120 }).notNull(),
  providerType: mysqlEnum("providerType", ["BUILT_IN", "OPENAI_COMPATIBLE"]).default("BUILT_IN").notNull(),
  baseUrl: varchar("baseUrl", { length: 500 }),
  activeModel: varchar("activeModel", { length: 160 }),
  encryptedApiKey: text("encryptedApiKey"),
  costMode: mysqlEnum("costMode", ["LOCAL_ONLY", "BALANCED", "POWER"]).default("BALANCED").notNull(),
  isActive: int("isActive").default(1).notNull(),
  createdAt: timestamp("createdAt").defaultNow().notNull(),
  updatedAt: timestamp("updatedAt").defaultNow().onUpdateNow().notNull(),
});

export const usageRecords = mysqlTable("usageRecords", {
  id: int("id").autoincrement().primaryKey(),
  userId: int("userId").notNull().references(() => users.id, { onDelete: "cascade" }),
  taskId: int("taskId").references(() => agentTasks.id, { onDelete: "set null" }),
  model: varchar("model", { length: 160 }).notNull(),
  inputTokens: int("inputTokens").default(0).notNull(),
  outputTokens: int("outputTokens").default(0).notNull(),
  estimatedCostMicros: int("estimatedCostMicros").default(0).notNull(),
  toolCalls: int("toolCalls").default(0).notNull(),
  createdAt: timestamp("createdAt").defaultNow().notNull(),
});

export const mobileDevices = mysqlTable("mobileDevices", {
  id: int("id").autoincrement().primaryKey(),
  userId: int("userId").notNull().references(() => users.id, { onDelete: "cascade" }),
  deviceId: varchar("deviceId", { length: 128 }).notNull(),
  label: varchar("label", { length: 160 }).notNull(),
  platform: mysqlEnum("platform", ["ANDROID"]).default("ANDROID").notNull(),
  pushEnabled: int("pushEnabled").default(0).notNull(),
  lastSyncedAt: timestamp("lastSyncedAt"),
  createdAt: timestamp("createdAt").defaultNow().notNull(),
  updatedAt: timestamp("updatedAt").defaultNow().onUpdateNow().notNull(),
}, table => ({
  userDeviceUnique: uniqueIndex("mobile_devices_user_device_unique").on(table.userId, table.deviceId),
}));

export const mobileAuthGrants = mysqlTable("mobileAuthGrants", {
  id: int("id").autoincrement().primaryKey(),
  userId: int("userId").notNull().references(() => users.id, { onDelete: "cascade" }),
  codeHash: varchar("codeHash", { length: 64 }).notNull(),
  verifierHash: varchar("verifierHash", { length: 64 }).notNull(),
  encryptedSessionToken: text("encryptedSessionToken").notNull(),
  expiresAt: timestamp("expiresAt").notNull(),
  consumedAt: timestamp("consumedAt"),
  createdAt: timestamp("createdAt").defaultNow().notNull(),
}, table => ({
  codeHashUnique: uniqueIndex("mobile_auth_grants_code_hash_unique").on(table.codeHash),
}));

export const researchSessions = mysqlTable("researchSessions", {
  id: int("id").autoincrement().primaryKey(),
  userId: int("userId").notNull().references(() => users.id, { onDelete: "cascade" }),
  capabilityGrantId: int("capabilityGrantId").references(() => capabilityGrants.id, { onDelete: "set null" }),
  query: varchar("query", { length: 600 }).notNull(),
  status: mysqlEnum("status", ["DRAFT", "RUNNING", "COMPLETED", "FAILED"]).default("DRAFT").notNull(),
  summary: text("summary"),
  errorSummary: text("errorSummary"),
  createdAt: timestamp("createdAt").defaultNow().notNull(),
  updatedAt: timestamp("updatedAt").defaultNow().onUpdateNow().notNull(),
});

export const researchSources = mysqlTable("researchSources", {
  id: int("id").autoincrement().primaryKey(),
  sessionId: int("sessionId").notNull().references(() => researchSessions.id, { onDelete: "cascade" }),
  userId: int("userId").notNull().references(() => users.id, { onDelete: "cascade" }),
  sourceUrl: varchar("sourceUrl", { length: 2048 }).notNull(),
  host: varchar("host", { length: 255 }).notNull(),
  title: varchar("title", { length: 500 }),
  excerpt: text("excerpt"),
  citationLabel: varchar("citationLabel", { length: 32 }).notNull(),
  fetchStatus: mysqlEnum("fetchStatus", ["PENDING", "FETCHED", "REJECTED", "FAILED"]).default("PENDING").notNull(),
  createdAt: timestamp("createdAt").defaultNow().notNull(),
});

export const learningCandidates = mysqlTable("learningCandidates", {
  id: int("id").autoincrement().primaryKey(),
  userId: int("userId").notNull().references(() => users.id, { onDelete: "cascade" }),
  title: varchar("title", { length: 200 }).notNull(),
  content: text("content").notNull(),
  layer: mysqlEnum("layer", ["TASK", "PROJECT", "PERSONAL", "DOCUMENT"]).default("PERSONAL").notNull(),
  source: varchar("source", { length: 160 }).default("ANDROID_LOCAL").notNull(),
  status: mysqlEnum("status", ["PENDING", "APPROVED", "DISMISSED"]).default("PENDING").notNull(),
  memoryId: int("memoryId").references(() => memories.id, { onDelete: "set null" }),
  createdAt: timestamp("createdAt").defaultNow().notNull(),
  updatedAt: timestamp("updatedAt").defaultNow().onUpdateNow().notNull(),
});

export const improvementRecords = mysqlTable("improvementRecords", {
  id: int("id").autoincrement().primaryKey(),
  userId: int("userId").notNull().references(() => users.id, { onDelete: "cascade" }),
  scope: mysqlEnum("scope", ["PROMPT", "TOOL", "WORKFLOW", "MODEL_ROUTING"]).notNull(),
  title: varchar("title", { length: 200 }).notNull(),
  proposedChange: text("proposedChange").notNull(),
  evidence: text("evidence").notNull(),
  testOutcome: text("testOutcome"),
  benchmarkSummary: text("benchmarkSummary"),
  versionLabel: varchar("versionLabel", { length: 120 }).notNull(),
  status: mysqlEnum("status", ["PENDING", "APPROVED", "REJECTED", "ROLLED_BACK"]).default("PENDING").notNull(),
  reviewNote: text("reviewNote"),
  approvedAt: timestamp("approvedAt"),
  rolledBackAt: timestamp("rolledBackAt"),
  createdAt: timestamp("createdAt").defaultNow().notNull(),
  updatedAt: timestamp("updatedAt").defaultNow().onUpdateNow().notNull(),
});

export const capabilityGrants = mysqlTable("capabilityGrants", {
  id: int("id").autoincrement().primaryKey(),
  userId: int("userId").notNull().references(() => users.id, { onDelete: "cascade" }),
  capability: varchar("capability", { length: 100 }).notNull(),
  scope: text("scope").notNull(),
  rationale: text("rationale"),
  outcome: text("outcome"),
  status: mysqlEnum("status", ["PENDING", "APPROVED", "DECLINED", "REVOKED"]).default("PENDING").notNull(),
  expiresAt: timestamp("expiresAt"),
  createdAt: timestamp("createdAt").defaultNow().notNull(),
  updatedAt: timestamp("updatedAt").defaultNow().onUpdateNow().notNull(),
});

export const githubConnections = mysqlTable("githubConnections", {
  id: int("id").autoincrement().primaryKey(),
  userId: int("userId").notNull().references(() => users.id, { onDelete: "cascade" }),
  login: varchar("login", { length: 160 }).notNull(),
  encryptedToken: text("encryptedToken").notNull(),
  scopes: varchar("scopes", { length: 1000 }).notNull(),
  createdAt: timestamp("createdAt").defaultNow().notNull(),
  updatedAt: timestamp("updatedAt").defaultNow().onUpdateNow().notNull(),
}, table => ({
  userConnectionUnique: uniqueIndex("github_connections_user_unique").on(table.userId),
}));

export const githubOperationRequests = mysqlTable("githubOperationRequests", {
  id: int("id").autoincrement().primaryKey(),
  userId: int("userId").notNull().references(() => users.id, { onDelete: "cascade" }),
  capabilityGrantId: int("capabilityGrantId").references(() => capabilityGrants.id, { onDelete: "set null" }),
  repository: varchar("repository", { length: 255 }).notNull(),
  operation: mysqlEnum("operation", ["CREATE_ISSUE", "CREATE_BRANCH", "CREATE_PULL_REQUEST", "WRITE_WORKSPACE_FILE"]).notNull(),
  payload: text("payload").notNull(),
  status: mysqlEnum("status", ["PENDING", "APPROVED", "COMPLETED", "FAILED", "CANCELLED"]).default("PENDING").notNull(),
  resultSummary: text("resultSummary"),
  errorSummary: text("errorSummary"),
  createdAt: timestamp("createdAt").defaultNow().notNull(),
  updatedAt: timestamp("updatedAt").defaultNow().onUpdateNow().notNull(),
});

export type User = typeof users.$inferSelect;
export type InsertUser = typeof users.$inferInsert;
export type Project = typeof projects.$inferSelect;
export type Conversation = typeof conversations.$inferSelect;
export type AgentTask = typeof agentTasks.$inferSelect;
export type Memory = typeof memories.$inferSelect;
