import { TRPCError } from "@trpc/server";
import { z } from "zod";
import * as db from "../db";
import { generateImage } from "../_core/imageGeneration";
import { invokeLLM, listLLMModels } from "../_core/llm";
import { storageGetSignedUrl, storagePut } from "../storage";
import { assertSafeProviderUrl, decryptSecret, encryptSecret, redactSecrets } from "../security";
import { protectedProcedure, router } from "../_core/trpc";

const toolDefinitions = [
  { key: "web_search", label: "Web research", description: "Search and compare public web sources.", category: "Research", risk: "Low", version: "1", inputSchema: "query, public HTTPS sources", outputSchema: "cited source summary", networkRequired: true, timeoutSeconds: 45, retryStrategy: "bounded source retry", auditPolicy: "record query and source URLs" },
  { key: "github", label: "GitHub", description: "Read repository context and prepare confirmation-gated changes.", category: "Engineering", risk: "Medium", version: "1", inputSchema: "repository and proposed operation", outputSchema: "repository context or operation record", networkRequired: true, timeoutSeconds: 45, retryStrategy: "no automatic writes", auditPolicy: "record proposal, confirmation, and outcome" },
  { key: "code_executor", label: "Code executor", description: "Describe isolated code workflows after approval; a general production sandbox is not yet exposed.", category: "Engineering", risk: "High", version: "1", inputSchema: "approved workspace action", outputSchema: "verified execution result or unavailable state", networkRequired: false, timeoutSeconds: 60, retryStrategy: "user-approved retry only", auditPolicy: "record plan, evidence, and failure" },
  { key: "document_reader", label: "Document reader", description: "Read user-selected or uploaded document context.", category: "Knowledge", risk: "Low", version: "1", inputSchema: "authorized document reference", outputSchema: "excerpt with source label", networkRequired: false, timeoutSeconds: 30, retryStrategy: "no content retry without consent", auditPolicy: "record document reference, not sensitive content" },
  { key: "image_generation", label: "Image studio", description: "Create images through a configured provider.", category: "Creative", risk: "Medium", version: "1", inputSchema: "image prompt", outputSchema: "provider-generated asset URL", networkRequired: true, timeoutSeconds: 90, retryStrategy: "one provider retry", auditPolicy: "record provider and generation outcome" },
  { key: "http_request", label: "API request", description: "Call an approved API endpoint with scoped credentials.", category: "Connectivity", risk: "High", version: "1", inputSchema: "approved endpoint and request", outputSchema: "redacted response summary", networkRequired: true, timeoutSeconds: 30, retryStrategy: "idempotent requests only", auditPolicy: "record endpoint host and outcome without secrets" },
] as const;

const taskPlanSchema = z.object({
  steps: z.array(z.object({
    title: z.string().min(3).max(160),
    detail: z.string().max(400).optional(),
    toolKey: z.string().max(80).optional(),
  })).min(2).max(6),
});

const fallbackPlan = [
  { title: "Clarify the objective", detail: "Establish the requested outcome and required context.", toolKey: "web_search" },
  { title: "Execute the scoped work", detail: "Use approved tools and preserve an auditable record.", toolKey: "code_executor" },
  { title: "Verify the result", detail: "Review the outcome and surface any user decision needed.", toolKey: "document_reader" },
];

async function resolveModel(preferred?: string | null) {
  const catalog = await listLLMModels();
  const models = catalog.data ?? [];
  return models.find(model => model.id === preferred)?.id ?? models.find(model => model.id.includes("gpt-5-mini"))?.id ?? models[0]?.id;
}

async function invokeSelectedProvider(provider: Awaited<ReturnType<typeof db.latestProvider>>, model: string | undefined, messages: unknown[], responseFormat?: Record<string, unknown>) {
  if (provider?.costMode === "LOCAL_ONLY") throw new TRPCError({ code: "PRECONDITION_FAILED", message: "Local Only mode is selected, but an on-device model runtime has not been connected to this deployment." });
  if (provider?.providerType === "OPENAI_COMPATIBLE") {
    if (!provider.baseUrl || !provider.encryptedApiKey || !provider.activeModel) throw new TRPCError({ code: "PRECONDITION_FAILED", message: "Complete the endpoint, key, and model fields for the selected external provider." });
    let baseUrl: string;
    try { baseUrl = assertSafeProviderUrl(provider.baseUrl); } catch { throw new TRPCError({ code: "BAD_REQUEST", message: "The configured provider endpoint is not a permitted public HTTPS URL." }); }
    const response = await fetch(`${baseUrl}/chat/completions`, { method: "POST", headers: { "Content-Type": "application/json", Authorization: `Bearer ${decryptSecret(provider.encryptedApiKey)}` }, body: JSON.stringify({ model: provider.activeModel, messages, ...(responseFormat ? { response_format: responseFormat } : {}) }) });
    if (!response.ok) throw new TRPCError({ code: "BAD_GATEWAY", message: "The configured provider rejected the request. Check its endpoint, model, and credential in Settings." });
    return { response: await response.json() as { choices?: Array<{ message?: { content?: string | unknown[] } }> }, model: provider.activeModel };
  }
  if (!model) throw new TRPCError({ code: "PRECONDITION_FAILED", message: "No language model is currently available." });
  const response = await invokeLLM({ model, messages: messages as never, ...(responseFormat ? { response_format: responseFormat as never } : {}) });
  return { response, model };
}

async function documentContext(userId: number, attachmentNames?: string[]) {
  if (!attachmentNames?.length) return { text: "", files: [] as Array<{ type: "file_url"; file_url: { url: string; mime_type: string } }> };
  const files = await db.findUploadedFilesByNames(userId, attachmentNames);
  const supported = files.filter(file => /^(text\/|application\/(json|javascript|xml))/.test(file.mimeType) || /\.(md|txt|csv|json|ts|tsx|js|jsx|py|html|css|sql)$/i.test(file.name));
  const fragments = await Promise.all(supported.slice(0, 3).map(async file => {
    try {
      const signedUrl = await storageGetSignedUrl(file.storageKey);
      const response = await fetch(signedUrl);
      return response.ok ? `Attached document: ${file.name}\n${(await response.text()).slice(0, 12000)}` : `Attached document: ${file.name} (content unavailable)`;
    } catch { return `Attached document: ${file.name} (content unavailable)`; }
  }));
  const pdfFiles = files.filter(file => file.mimeType === "application/pdf");
  const signedFiles = await Promise.all(pdfFiles.slice(0, 2).map(async file => {
    try { return { type: "file_url" as const, file_url: { url: await storageGetSignedUrl(file.storageKey), mime_type: file.mimeType } }; } catch { return null; }
  }));
  return { text: fragments.join("\n\n"), files: signedFiles.filter((file): file is { type: "file_url"; file_url: { url: string; mime_type: string } } => Boolean(file)) };
}

function requireRecord<T>(record: T | null | undefined, message = "The requested record was not found.") {
  if (!record) throw new TRPCError({ code: "NOT_FOUND", message });
  return record;
}

function safeProvider(provider: Awaited<ReturnType<typeof db.latestProvider>>) {
  if (!provider) return null;
  const { encryptedApiKey: _encryptedApiKey, ...safe } = provider;
  return { ...safe, hasApiKey: Boolean(_encryptedApiKey) };
}

export const agentRouter = router({
  projects: router({
    list: protectedProcedure.query(({ ctx }) => db.listProjects(ctx.user.id)),
    create: protectedProcedure.input(z.object({ name: z.string().trim().min(2).max(160), description: z.string().trim().max(1000).optional(), color: z.string().max(16).optional() })).mutation(async ({ ctx, input }) => {
      const project = requireRecord(await db.createProject(ctx.user.id, input.name, input.description, input.color));
      await db.createActivity(ctx.user.id, { eventType: "PROJECT_CREATED", title: `Created workspace: ${project.name}`, detail: "A project workspace was created." });
      return project;
    }),
  }),
  conversations: router({
    list: protectedProcedure.input(z.object({ projectId: z.number().int().positive().optional() }).optional()).query(({ ctx, input }) => db.listConversations(ctx.user.id, input?.projectId)),
    messages: protectedProcedure.input(z.object({ conversationId: z.number().int().positive() })).query(async ({ ctx, input }) => {
      requireRecord(await db.getConversation(ctx.user.id, input.conversationId));
      return db.listMessages(ctx.user.id, input.conversationId);
    }),
  }),
  chat: router({
    send: protectedProcedure.input(z.object({
      content: z.string().trim().min(1).max(6000),
      conversationId: z.number().int().positive().optional(),
      projectId: z.number().int().positive().optional(),
      attachmentNames: z.array(z.string().max(255)).max(8).optional(),
      url: z.string().url().optional(),
    })).mutation(async ({ ctx, input }) => {
      if (input.projectId) requireRecord(await db.getProject(ctx.user.id, input.projectId), "The selected project is unavailable.");
      const provider = await db.latestProvider(ctx.user.id);
      const model = provider?.providerType === "OPENAI_COMPATIBLE" ? provider.activeModel ?? undefined : await resolveModel(provider?.activeModel);
      const conversation = input.conversationId
        ? requireRecord(await db.getConversation(ctx.user.id, input.conversationId), "The selected conversation is unavailable.")
        : requireRecord(await db.createConversation(ctx.user.id, input.content.slice(0, 72), input.projectId));
      const userContent = [input.content, input.url ? `\nReference URL: ${input.url}` : ""].join("");
      await db.createMessage(ctx.user.id, conversation.id, "user", userContent, input.attachmentNames);
      const history = (await db.listMessages(ctx.user.id, conversation.id)).slice(-12).map(message => ({ role: message.role, content: message.content }));
      const documents = await documentContext(ctx.user.id, input.attachmentNames);
      const latestMessage = history.pop();
      const userMessage = `${latestMessage?.content ?? userContent}${documents.text ? `\n\n${documents.text}` : ""}`;
      const userContentWithFiles = documents.files.length ? [{ type: "text", text: userMessage }, ...documents.files] : userMessage;
      const completion = await invokeSelectedProvider(provider, model, [{ role: "system", content: "You are Autonova, a precise personal AI agent. Provide concise, useful answers in Markdown. If a request could be high impact, explain the proposed action and ask for confirmation. Never reveal hidden reasoning, secrets, access tokens, or private configuration." }, ...history, { role: "user", content: userContentWithFiles }]);
      const rawAssistantContent = completion.response.choices?.[0]?.message?.content;
      const assistantContent = redactSecrets(typeof rawAssistantContent === "string" && rawAssistantContent.trim() ? rawAssistantContent.trim() : "I could not produce a response. Please try again.");
      await db.createMessage(ctx.user.id, conversation.id, "assistant", assistantContent);
      const inputTokens = Math.ceil(history.reduce((total, message) => total + message.content.length, 0) / 4);
      const outputTokens = Math.ceil(assistantContent.length / 4);
      await db.createUsageRecord(ctx.user.id, { model: completion.model, inputTokens, outputTokens });
      await db.createActivity(ctx.user.id, { eventType: "CHAT_RESPONSE", title: "Agent response completed", detail: `Model: ${completion.model}`, visibility: "ADVANCED" });
      return { conversationId: conversation.id, content: assistantContent, model: completion.model, usage: { inputTokens, outputTokens } };
    }),
  }),
  tasks: router({
    list: protectedProcedure.input(z.object({ projectId: z.number().int().positive().optional() }).optional()).query(async ({ ctx, input }) => {
      const tasks = await db.listTasks(ctx.user.id, input?.projectId);
      const withSteps = await Promise.all(tasks.map(async task => ({ ...task, steps: await db.listTaskSteps(task.id) })));
      return withSteps;
    }),
    create: protectedProcedure.input(z.object({ request: z.string().trim().min(4).max(5000), projectId: z.number().int().positive().optional(), conversationId: z.number().int().positive().optional() })).mutation(async ({ ctx, input }) => {
      if (input.projectId) requireRecord(await db.getProject(ctx.user.id, input.projectId), "The selected project is unavailable.");
      const provider = await db.latestProvider(ctx.user.id);
      const model = provider?.providerType === "OPENAI_COMPATIBLE" ? provider.activeModel ?? undefined : await resolveModel(provider?.activeModel);
      const task = requireRecord(await db.createTask(ctx.user.id, input.request, { projectId: input.projectId, conversationId: input.conversationId, model }));
      await db.updateTask(ctx.user.id, task.id, { status: "PLANNING" });
      await db.createActivity(ctx.user.id, { taskId: task.id, eventType: "TASK_CREATED", title: "Task received", detail: "Autonova began assembling an executable plan." });
      let steps: Array<{ title: string; detail?: string; toolKey?: string }> = fallbackPlan;
      try {
        const planResponse = await invokeSelectedProvider(provider, model, [{ role: "system", content: "Create a safe, high-level execution plan. Never propose destructive actions without an explicit confirmation step." }, { role: "user", content: input.request }], { type: "json_schema", json_schema: { name: "agent_task_plan", strict: true, schema: { type: "object", properties: { steps: { type: "array", items: { type: "object", properties: { title: { type: "string" }, detail: { type: "string" }, toolKey: { type: "string" } }, required: ["title"], additionalProperties: false }, minItems: 2, maxItems: 6 } }, required: ["steps"], additionalProperties: false } } });
        const rawPlanContent = planResponse.response.choices?.[0]?.message?.content;
        const parsed = taskPlanSchema.safeParse(JSON.parse(typeof rawPlanContent === "string" ? rawPlanContent : "{}"));
        if (parsed.success) steps = parsed.data.steps;
      } catch {
        // The task remains useful with a conservative fallback plan; no sensitive error is persisted.
      }
      await db.createTaskSteps(task.id, steps);
      await db.updateTask(ctx.user.id, task.id, { status: "RUNNING" });
      const createdSteps = await db.listTaskSteps(task.id);
      if (createdSteps[0]) await db.updateTaskStep(task.id, createdSteps[0].id, "RUNNING");
      await db.createActivity(ctx.user.id, { taskId: task.id, eventType: "PLAN_READY", title: "Execution plan ready", detail: `${steps.length} steps are now visible in the task panel.` });
      await db.createUsageRecord(ctx.user.id, { taskId: task.id, model: model || "unavailable", inputTokens: Math.ceil(input.request.length / 4), outputTokens: Math.ceil(JSON.stringify(steps).length / 4) });
      return { ...task, status: "RUNNING" as const, steps };
    }),
    advance: protectedProcedure.input(z.object({ taskId: z.number().int().positive() })).mutation(async ({ ctx, input }) => {
      const task = requireRecord(await db.getTask(ctx.user.id, input.taskId));
      const steps = await db.listTaskSteps(task.id);
      if (task.status === "RUNNING") {
        const current = steps.find(step => step.status === "RUNNING");
        if (current) await db.updateTaskStep(task.id, current.id, "COMPLETED");
        const next = steps.find(step => step.status === "PENDING");
        if (next) { await db.updateTaskStep(task.id, next.id, "RUNNING"); await db.createActivity(ctx.user.id, { taskId: task.id, eventType: "STEP_ADVANCED", title: `Progressed: ${next.title}`, visibility: "ADVANCED" }); return { status: "RUNNING" as const }; }
        await db.updateTask(ctx.user.id, task.id, { status: "VERIFYING" });
        await db.createActivity(ctx.user.id, { taskId: task.id, eventType: "VERIFYING", title: "Task entered verification", detail: "All planned steps are complete; result review is ready." });
        return { status: "VERIFYING" as const };
      }
      if (task.status === "VERIFYING") throw new TRPCError({ code: "PRECONDITION_FAILED", message: "Record verification evidence before completing this task." });
      throw new TRPCError({ code: "BAD_REQUEST", message: "This task cannot be advanced from its current state." });
    }),
    verify: protectedProcedure.input(z.object({ taskId: z.number().int().positive(), passed: z.boolean(), evidence: z.string().trim().min(3).max(2000), nextAction: z.string().trim().min(3).max(400).optional() })).mutation(async ({ ctx, input }) => {
      const task = requireRecord(await db.getTask(ctx.user.id, input.taskId));
      if (task.status !== "VERIFYING") throw new TRPCError({ code: "BAD_REQUEST", message: "Only a task awaiting verification evidence can be verified." });
      const evidence = redactSecrets(input.evidence);
      if (input.passed) {
        await db.updateTask(ctx.user.id, task.id, { status: "COMPLETED", completedAt: new Date(), finalResult: evidence });
        await db.createActivity(ctx.user.id, { taskId: task.id, eventType: "TASK_VERIFIED", title: "Verification passed", detail: evidence, visibility: "ADVANCED" });
        return { status: "COMPLETED" as const };
      }
      await db.updateTask(ctx.user.id, task.id, { status: "FAILED", errorSummary: evidence });
      await db.createActivity(ctx.user.id, { taskId: task.id, eventType: "TASK_VERIFICATION_FAILED", title: "Verification failed", detail: `${evidence}${input.nextAction ? ` Next action: ${redactSecrets(input.nextAction)}` : ""}`, visibility: "ADVANCED" });
      return { status: "FAILED" as const };
    }),
    fail: protectedProcedure.input(z.object({ taskId: z.number().int().positive(), summary: z.string().trim().min(3).max(500) })).mutation(async ({ ctx, input }) => {
      requireRecord(await db.getTask(ctx.user.id, input.taskId));
      await db.updateTask(ctx.user.id, input.taskId, { status: "FAILED", errorSummary: redactSecrets(input.summary) });
      await db.createActivity(ctx.user.id, { taskId: input.taskId, eventType: "TASK_FAILED", title: "Task marked as failed", detail: "A user-provided failure summary was recorded." });
      return { success: true };
    }),
    waitForTool: protectedProcedure.input(z.object({ taskId: z.number().int().positive(), toolKey: z.string().trim().min(2).max(80), summary: z.string().trim().min(3).max(500) })).mutation(async ({ ctx, input }) => {
      requireRecord(await db.getTask(ctx.user.id, input.taskId));
      await db.updateTask(ctx.user.id, input.taskId, { status: "WAITING_FOR_TOOL" });
      await db.createActivity(ctx.user.id, { taskId: input.taskId, eventType: "WAITING_FOR_TOOL", title: `Awaiting approved tool: ${input.toolKey}`, detail: redactSecrets(input.summary) });
      return { success: true };
    }),
    retry: protectedProcedure.input(z.object({ taskId: z.number().int().positive() })).mutation(async ({ ctx, input }) => {
      const task = requireRecord(await db.getTask(ctx.user.id, input.taskId));
      if (task.status !== "FAILED") throw new TRPCError({ code: "BAD_REQUEST", message: "Only a failed task can be retried." });
      await db.updateTask(ctx.user.id, input.taskId, { status: "PLANNING", retryCount: task.retryCount + 1, errorSummary: null, nextRetryAt: null });
      await db.createActivity(ctx.user.id, { taskId: input.taskId, eventType: "TASK_RETRY", title: "Task retry requested", detail: `Retry ${task.retryCount + 1} will be planned again with the current permissions.` });
      return { success: true, status: "PLANNING" as const };
    }),
    cancel: protectedProcedure.input(z.object({ taskId: z.number().int().positive() })).mutation(async ({ ctx, input }) => {
      await db.updateTask(ctx.user.id, input.taskId, { status: "CANCELLED" });
      await db.createActivity(ctx.user.id, { taskId: input.taskId, eventType: "TASK_CANCELLED", title: "Task cancelled by user" });
      return { success: true };
    }),
  }),
  memory: router({
    list: protectedProcedure.input(z.object({ projectId: z.number().int().positive().optional() }).optional()).query(({ ctx, input }) => db.listMemories(ctx.user.id, input?.projectId)),
    create: protectedProcedure.input(z.object({ layer: z.enum(["SHORT_TERM", "TASK", "PROJECT", "PERSONAL", "DOCUMENT"]), title: z.string().trim().min(2).max(200), content: z.string().trim().min(2).max(5000), projectId: z.number().int().positive().optional(), taskId: z.number().int().positive().optional() })).mutation(async ({ ctx, input }) => {
      const memory = requireRecord(await db.createMemory(ctx.user.id, input));
      await db.createActivity(ctx.user.id, { eventType: "MEMORY_SAVED", title: `Saved ${input.layer.toLowerCase().replace("_", " ")} memory`, visibility: "ADVANCED" });
      return memory;
    }),
    update: protectedProcedure.input(z.object({ memoryId: z.number().int().positive(), title: z.string().trim().min(2).max(200).optional(), content: z.string().trim().min(2).max(5000).optional(), layer: z.enum(["SHORT_TERM", "TASK", "PROJECT", "PERSONAL", "DOCUMENT"]).optional() })).mutation(async ({ ctx, input }) => {
      const { memoryId, ...values } = input;
      await db.updateMemory(ctx.user.id, memoryId, values);
      return { success: true };
    }),
    delete: protectedProcedure.input(z.object({ memoryId: z.number().int().positive() })).mutation(async ({ ctx, input }) => {
      await db.deleteMemory(ctx.user.id, input.memoryId);
      await db.createActivity(ctx.user.id, { eventType: "MEMORY_DELETED", title: "Memory deleted by user", visibility: "ADVANCED" });
      return { success: true };
    }),
  }),
  tools: router({
    list: protectedProcedure.query(async ({ ctx }) => {
      const permissions = await db.listToolPermissions(ctx.user.id);
      return toolDefinitions.map(tool => ({ ...tool, policy: permissions.find(permission => permission.toolKey === tool.key)?.policy ?? "ASK" }));
    }),
    setPermission: protectedProcedure.input(z.object({ toolKey: z.enum(["web_search", "github", "code_executor", "document_reader", "image_generation", "http_request"]), policy: z.enum(["ASK", "ALLOW", "DENY"]) })).mutation(async ({ ctx, input }) => {
      await db.setToolPermission(ctx.user.id, input.toolKey, input.policy);
      await db.createActivity(ctx.user.id, { eventType: "TOOL_POLICY", title: `Updated ${input.toolKey.replace("_", " ")} policy`, detail: `Policy set to ${input.policy}.`, visibility: "ADVANCED" });
      return { success: true };
    }),
  }),
  activity: router({
    list: protectedProcedure.input(z.object({ advanced: z.boolean().optional() }).optional()).query(({ ctx, input }) => db.listActivity(ctx.user.id, input?.advanced)),
  }),
  files: router({
    list: protectedProcedure.input(z.object({ projectId: z.number().int().positive().optional() }).optional()).query(({ ctx, input }) => db.listUploadedFiles(ctx.user.id, input?.projectId)),
    upload: protectedProcedure.input(z.object({ projectId: z.number().int().positive().optional(), name: z.string().min(1).max(255), mimeType: z.string().min(1).max(160), contentBase64: z.string().min(1).max(14_000_000) })).mutation(async ({ ctx, input }) => {
      if (input.projectId) requireRecord(await db.getProject(ctx.user.id, input.projectId), "The selected project is unavailable.");
      const bytes = Buffer.from(input.contentBase64, "base64");
      if (bytes.length > 10 * 1024 * 1024) throw new TRPCError({ code: "PAYLOAD_TOO_LARGE", message: "Uploads are limited to 10 MB." });
      const safeName = input.name.replace(/[^a-zA-Z0-9._-]/g, "_");
      const stored = await storagePut(`${ctx.user.id}/documents/${safeName}`, bytes, input.mimeType);
      const file = requireRecord(await db.createUploadedFile(ctx.user.id, { projectId: input.projectId, name: input.name, mimeType: input.mimeType, sizeBytes: bytes.length, storageKey: stored.key, storageUrl: stored.url }));
      await db.createActivity(ctx.user.id, { eventType: "FILE_UPLOADED", title: `Uploaded ${input.name}`, detail: "File bytes are stored outside the application database.", visibility: "ADVANCED" });
      return file;
    }),
  }),
  settings: router({
    get: protectedProcedure.query(async ({ ctx }) => ({ provider: safeProvider(await db.latestProvider(ctx.user.id)), models: (await listLLMModels()).data.map(model => ({ id: model.id })) })),
    saveProvider: protectedProcedure.input(z.object({ name: z.string().trim().min(2).max(120), providerType: z.enum(["BUILT_IN", "OPENAI_COMPATIBLE"]), baseUrl: z.string().url().optional(), activeModel: z.string().max(160).optional(), apiKey: z.string().min(8).max(1000).optional(), costMode: z.enum(["LOCAL_ONLY", "BALANCED", "POWER"]) })).mutation(async ({ ctx, input }) => {
      if (input.providerType === "OPENAI_COMPATIBLE" && !input.apiKey) throw new TRPCError({ code: "BAD_REQUEST", message: "An API key is required for an OpenAI-compatible provider." });
      const provider = await db.saveProvider(ctx.user.id, { ...input, encryptedApiKey: input.apiKey ? encryptSecret(input.apiKey) : undefined });
      await db.createActivity(ctx.user.id, { eventType: "PROVIDER_UPDATED", title: "AI provider settings updated", detail: "Credential material is encrypted and excluded from activity data.", visibility: "ADVANCED" });
      return safeProvider(provider);
    }),
  }),
  usage: router({
    summary: protectedProcedure.query(async ({ ctx }) => {
      const records = await db.listUsageRecords(ctx.user.id);
      const totals = records.reduce((accumulator, record) => ({ inputTokens: accumulator.inputTokens + record.inputTokens, outputTokens: accumulator.outputTokens + record.outputTokens, toolCalls: accumulator.toolCalls + record.toolCalls, estimatedCostMicros: accumulator.estimatedCostMicros + record.estimatedCostMicros }), { inputTokens: 0, outputTokens: 0, toolCalls: 0, estimatedCostMicros: 0 });
      const tasks = await db.listTasks(ctx.user.id);
      return { totals, records: records.map(record => ({ ...record, taskRequest: record.taskId ? tasks.find(task => task.id === record.taskId)?.request ?? null : null })) };
    }),
  }),
  images: router({
    generate: protectedProcedure.input(z.object({ prompt: z.string().trim().min(3).max(1200) })).mutation(async ({ ctx, input }) => {
      const image = await generateImage({ prompt: input.prompt });
      await db.createActivity(ctx.user.id, { eventType: "IMAGE_GENERATED", title: "Image generation completed", detail: "Generated asset is available in the current session.", visibility: "ADVANCED" });
      await db.createUsageRecord(ctx.user.id, { model: "image-generation", inputTokens: 0, outputTokens: 0, toolCalls: 1 });
      return image;
    }),
  }),
  github: router({
    publicRepository: protectedProcedure.input(z.object({ repository: z.string().trim().regex(/^[A-Za-z0-9_.-]+\/[A-Za-z0-9_.-]+$/) })).query(async ({ input }) => {
      const headers = { Accept: "application/vnd.github+json", "User-Agent": "Autonova-Agent-Workspace" };
      const [repository, branches, issues, pulls, commits] = await Promise.all([fetch(`https://api.github.com/repos/${input.repository}`, { headers }), fetch(`https://api.github.com/repos/${input.repository}/branches?per_page=6`, { headers }), fetch(`https://api.github.com/repos/${input.repository}/issues?state=open&per_page=6`, { headers }), fetch(`https://api.github.com/repos/${input.repository}/pulls?state=open&per_page=6`, { headers }), fetch(`https://api.github.com/repos/${input.repository}/commits?per_page=5`, { headers })]);
      if (!repository.ok) throw new TRPCError({ code: "NOT_FOUND", message: "This public repository could not be found or is unavailable." });
      const repo = await repository.json() as { full_name: string; description: string | null; default_branch: string; html_url: string; stargazers_count: number };
      return { repo, branches: branches.ok ? await branches.json() : [], issues: issues.ok ? await issues.json() : [], pulls: pulls.ok ? await pulls.json() : [], commits: commits.ok ? await commits.json() : [] };
    }),
  }),
});
