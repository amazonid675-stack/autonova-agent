import type { Express, Response } from "express";
import { createContext } from "./_core/context";
import { invokeLLM, listLLMModels } from "./_core/llm";
import * as db from "./db";
import { assertSafeProviderUrl, decryptSecret, redactSecrets } from "./security";

type StreamEvent = { type: "status"; label: string } | { type: "metadata"; conversationId: number } | { type: "message"; content: string } | { type: "error"; message: string } | { type: "done" };

function writeEvent(res: Response, event: StreamEvent) {
  res.write(`data: ${JSON.stringify(event)}\n\n`);
}

export function registerAgentStream(app: Express) {
  app.post("/api/agent/stream", async (req, res) => {
    const ctx = await createContext({ req, res } as Parameters<typeof createContext>[0]);
    if (!ctx.user) { res.status(401).json({ error: "Authentication required" }); return; }
    const content = typeof req.body?.content === "string" ? req.body.content.trim() : "";
    const projectId = Number.isInteger(req.body?.projectId) ? req.body.projectId as number : undefined;
    const conversationId = Number.isInteger(req.body?.conversationId) ? req.body.conversationId as number : undefined;
    if (!content || content.length > 6000) { res.status(400).json({ error: "A valid message is required" }); return; }
    if (projectId && !await db.getProject(ctx.user.id, projectId)) { res.status(404).json({ error: "Project not found" }); return; }
    const conversation = conversationId ? await db.getConversation(ctx.user.id, conversationId) : await db.createConversation(ctx.user.id, content.slice(0, 72), projectId);
    if (!conversation) { res.status(404).json({ error: "Conversation not found" }); return; }
    res.status(200).set({ "Content-Type": "text/event-stream", "Cache-Control": "no-cache, no-transform", Connection: "keep-alive", "X-Accel-Buffering": "no" });
    res.flushHeaders();
    let closed = false;
    res.on("close", () => { closed = true; });
    try {
      await db.createMessage(ctx.user.id, conversation.id, "user", content);
      writeEvent(res, { type: "metadata", conversationId: conversation.id });
      writeEvent(res, { type: "status", label: "Understanding your request" });
      writeEvent(res, { type: "status", label: "Reviewing approved context" });
      const history = (await db.listMessages(ctx.user.id, conversation.id)).slice(-12).map(message => ({ role: message.role, content: message.content }));
      const messages = [{ role: "system", content: "You are Autonova. Return concise Markdown. Report only safe action summaries, never hidden reasoning or secrets." }, ...history];
      const provider = await db.latestProvider(ctx.user.id);
      let model = provider?.activeModel ?? "";
      let assistantContent = "";
      if (provider?.providerType === "OPENAI_COMPATIBLE") {
        if (!provider.baseUrl || !provider.encryptedApiKey || !provider.activeModel) throw new Error("Complete the endpoint, key, and model fields for the selected external provider.");
        const upstream = await fetch(`${assertSafeProviderUrl(provider.baseUrl)}/chat/completions`, { method: "POST", headers: { "Content-Type": "application/json", Authorization: `Bearer ${decryptSecret(provider.encryptedApiKey)}` }, body: JSON.stringify({ model: provider.activeModel, messages, stream: true }) });
        if (!upstream.ok || !upstream.body) throw new Error("The configured external provider rejected the streaming request.");
        const reader = upstream.body.getReader(); const decoder = new TextDecoder(); let buffer = "";
        for (;;) {
          const chunk = await reader.read(); if (chunk.done) break;
          buffer += decoder.decode(chunk.value, { stream: true }); const lines = buffer.split("\n"); buffer = lines.pop() ?? "";
          for (const line of lines) {
            if (!line.startsWith("data: ")) continue;
            const payload = line.slice(6).trim(); if (payload === "[DONE]") continue;
            try { const data = JSON.parse(payload) as { choices?: Array<{ delta?: { content?: string } }> }; const delta = data.choices?.[0]?.delta?.content; if (delta) { assistantContent += delta; if (!closed) writeEvent(res, { type: "message", content: assistantContent }); } } catch { /* Ignore provider keepalive frames. */ }
          }
        }
      } else {
        const catalog = await listLLMModels();
        model = catalog.data.find(item => item.id.includes("gpt-5-mini"))?.id ?? catalog.data[0]?.id ?? "";
        if (!model) throw new Error("No language model is currently available.");
        const response = await invokeLLM({ model, messages: messages as Parameters<typeof invokeLLM>[0]["messages"] });
        const raw = response.choices?.[0]?.message?.content;
        assistantContent = typeof raw === "string" ? raw : "";
      }
      assistantContent = redactSecrets(assistantContent.trim() || "I could not produce a response. Please try again.");
      if (!closed) writeEvent(res, { type: "status", label: "Composing a verified response" });
      await db.createMessage(ctx.user.id, conversation.id, "assistant", assistantContent);
      await db.createUsageRecord(ctx.user.id, { model, inputTokens: Math.ceil(history.reduce((total, message) => total + message.content.length, 0) / 4), outputTokens: Math.ceil(assistantContent.length / 4) });
      await db.createActivity(ctx.user.id, { eventType: "CHAT_STREAM", title: "Agent stream completed", detail: `Model: ${model}`, visibility: "ADVANCED" });
      if (!closed) { writeEvent(res, { type: "message", content: assistantContent }); writeEvent(res, { type: "done" }); }
    } catch (error) {
      if (!closed) { writeEvent(res, { type: "error", message: error instanceof Error ? error.message : "Unable to complete the response." }); writeEvent(res, { type: "done" }); }
    } finally { if (!closed) res.end(); }
  });
}
