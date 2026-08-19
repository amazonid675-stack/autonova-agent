import { AIChatBox } from "@/components/AIChatBox";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle, DialogTrigger } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Progress } from "@/components/ui/progress";
import { trpc } from "@/lib/trpc";
import { CheckCircle2, ChevronRight, CircleDotDashed, FileText, FolderPlus, Loader2, Plus, ShieldCheck, Sparkles, XCircle } from "lucide-react";
import { useEffect, useState } from "react";
import { toast } from "sonner";

const taskTone: Record<string, string> = { RUNNING: "bg-amber-400", PLANNING: "bg-blue-400", VERIFYING: "bg-violet-400", COMPLETED: "bg-emerald-400", FAILED: "bg-rose-400", CANCELLED: "bg-slate-400", QUEUED: "bg-slate-400" };

export default function Home() {
  const utils = trpc.useUtils();
  const [selectedProjectId, setSelectedProjectId] = useState<number | undefined>();
  const [conversationId, setConversationId] = useState<number | undefined>();
  const [attachments, setAttachments] = useState<string[]>([]);
  const [taskRequest, setTaskRequest] = useState("");
  const [newProjectName, setNewProjectName] = useState("");
  const [showNewProject, setShowNewProject] = useState(false);
  const [streaming, setStreaming] = useState(false);
  const [pendingUserMessage, setPendingUserMessage] = useState("");
  const [streamedMessage, setStreamedMessage] = useState("");
  const projectsQuery = trpc.agent.projects.list.useQuery();
  const messagesQuery = trpc.agent.conversations.messages.useQuery({ conversationId: conversationId ?? -1 }, { enabled: Boolean(conversationId) });
  const tasksQuery = trpc.agent.tasks.list.useQuery(selectedProjectId ? { projectId: selectedProjectId } : undefined);
  const activityQuery = trpc.agent.activity.list.useQuery();
  const usageQuery = trpc.agent.usage.summary.useQuery();
  const createProject = trpc.agent.projects.create.useMutation({ onSuccess: async project => { await utils.agent.projects.list.invalidate(); setSelectedProjectId(project.id); setShowNewProject(false); setNewProjectName(""); toast.success("Workspace created"); } });
  const sendChat = trpc.agent.chat.send.useMutation({ onSuccess: async result => { setConversationId(result.conversationId); setAttachments([]); await Promise.all([utils.agent.conversations.messages.invalidate(), utils.agent.conversations.list.invalidate(), utils.agent.activity.list.invalidate(), utils.agent.usage.summary.invalidate()]); }, onError: error => toast.error(error.message) });
  const uploadFile = trpc.agent.files.upload.useMutation({ onSuccess: async file => { setAttachments(current => [...current, file.name]); toast.success(`${file.name} is available to the agent`); await utils.agent.files.list.invalidate(); }, onError: error => toast.error(error.message) });
  const createTask = trpc.agent.tasks.create.useMutation({ onSuccess: async () => { setTaskRequest(""); await Promise.all([utils.agent.tasks.list.invalidate(), utils.agent.activity.list.invalidate(), utils.agent.usage.summary.invalidate()]); toast.success("Agent task is running"); }, onError: error => toast.error(error.message) });

  const streamChat = async (content: string) => {
    setStreaming(true); setPendingUserMessage(content); setStreamedMessage("");
    try {
      const response = await fetch("/api/agent/stream", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ content, projectId: selectedProjectId, conversationId }) });
      if (!response.ok || !response.body) throw new Error("The agent stream could not be started.");
      const reader = response.body.getReader(); const decoder = new TextDecoder(); let buffer = "";
      for (;;) {
        const chunk = await reader.read(); if (chunk.done) break;
        buffer += decoder.decode(chunk.value, { stream: true }); const events = buffer.split("\n\n"); buffer = events.pop() ?? "";
        events.forEach(event => { const payload = event.split("\n").find(line => line.startsWith("data: "))?.slice(6); if (!payload) return; try { const data = JSON.parse(payload) as { type: string; conversationId?: number; content?: string; message?: string }; if (data.type === "metadata" && data.conversationId) setConversationId(data.conversationId); if (data.type === "message") setStreamedMessage(data.content ?? ""); if (data.type === "error") toast.error(data.message ?? "Unable to complete the agent response."); } catch { /* Ignore malformed SSE frames. */ } });
      }
      await Promise.all([utils.agent.conversations.messages.invalidate(), utils.agent.conversations.list.invalidate(), utils.agent.activity.list.invalidate(), utils.agent.usage.summary.invalidate()]);
    } catch (error) { toast.error(error instanceof Error ? error.message : "Unable to complete the agent response."); }
    finally { setStreaming(false); setPendingUserMessage(""); setStreamedMessage(""); }
  };

  useEffect(() => { if (!selectedProjectId && projectsQuery.data?.[0]) setSelectedProjectId(projectsQuery.data[0].id); }, [projectsQuery.data, selectedProjectId]);
  const tasks = tasksQuery.data ?? [];
  const activeTask = tasks.find(task => ["RUNNING", "PLANNING", "VERIFYING"].includes(task.status)) ?? tasks[0];
  const progress = activeTask ? Math.round((activeTask.steps.filter(step => step.status === "COMPLETED").length / Math.max(activeTask.steps.length, 1)) * 100) : 0;
  const handleFile = (file: File) => { const reader = new FileReader(); reader.onload = () => { const base64 = String(reader.result).split(",")[1]; if (base64) uploadFile.mutate({ name: file.name, mimeType: file.type || "application/octet-stream", contentBase64: base64, projectId: selectedProjectId }); }; reader.readAsDataURL(file); };

  return <div className="min-h-screen px-4 pb-6 pt-4 sm:px-6 lg:px-8 lg:pt-7">
    <header className="mb-6 flex flex-col gap-4 xl:flex-row xl:items-center xl:justify-between">
      <div><p className="eyebrow">Personal agent workspace</p><h1 className="mt-1 text-2xl font-semibold tracking-tight sm:text-3xl">Command center</h1><p className="mt-1 text-sm text-muted-foreground">A calm surface for complex work, with every action visible and under your control.</p></div>
      <div className="flex flex-wrap items-center gap-2">
        <Badge variant="outline" className="gap-1.5 rounded-full border-emerald-500/25 bg-emerald-500/10 px-3 py-1 text-emerald-700 dark:text-emerald-300"><span className="size-1.5 rounded-full bg-emerald-500" />Secure session</Badge>
        <Dialog open={showNewProject} onOpenChange={setShowNewProject}>
          <DialogTrigger asChild><Button variant="outline" className="rounded-xl"><FolderPlus className="mr-2 size-4" />New workspace</Button></DialogTrigger>
          <DialogContent><DialogHeader><DialogTitle>Create workspace</DialogTitle><DialogDescription>Give this context a clear name. Conversations, files, tasks, and memory can stay grouped here.</DialogDescription></DialogHeader><Input value={newProjectName} onChange={event => setNewProjectName(event.target.value)} placeholder="e.g. Product launch" /><DialogFooter><Button onClick={() => createProject.mutate({ name: newProjectName })} disabled={newProjectName.trim().length < 2 || createProject.isPending}>{createProject.isPending && <Loader2 className="mr-2 size-4 animate-spin" />}Create workspace</Button></DialogFooter></DialogContent>
        </Dialog>
        <Dialog>
          <DialogTrigger asChild><Button className="rounded-xl"><Plus className="mr-2 size-4" />New task</Button></DialogTrigger>
          <DialogContent><DialogHeader><DialogTitle>Start an agent task</DialogTitle><DialogDescription>Autonova will build a reviewable plan before it attempts the work.</DialogDescription></DialogHeader><Input value={taskRequest} onChange={event => setTaskRequest(event.target.value)} placeholder="What should the agent accomplish?" /><DialogFooter><Button onClick={() => createTask.mutate({ request: taskRequest, projectId: selectedProjectId, conversationId })} disabled={taskRequest.trim().length < 4 || createTask.isPending}>{createTask.isPending && <Loader2 className="mr-2 size-4 animate-spin" />}Plan task</Button></DialogFooter></DialogContent>
        </Dialog>
      </div>
    </header>

    <div className="grid gap-5 xl:grid-cols-[minmax(0,1fr)_330px]">
      <section className="glass-panel min-h-[650px] overflow-hidden rounded-[1.5rem]">
        <div className="flex items-center gap-2 overflow-x-auto border-b border-border/65 px-4 py-3"><span className="mr-1 text-xs font-medium uppercase tracking-[0.14em] text-muted-foreground">Context</span>{projectsQuery.data?.map(project => <button key={project.id} onClick={() => { setSelectedProjectId(project.id); setConversationId(undefined); }} className={`whitespace-nowrap rounded-full px-3 py-1.5 text-xs transition ${selectedProjectId === project.id ? "bg-foreground text-background" : "bg-muted/70 text-muted-foreground hover:text-foreground"}`}>{project.name}</button>)}{!projectsQuery.isLoading && !projectsQuery.data?.length && <button onClick={() => setShowNewProject(true)} className="rounded-full bg-primary/10 px-3 py-1.5 text-xs text-primary hover:bg-primary/15">Create your first workspace</button>}</div>
        <div className="grid min-h-[596px] grid-rows-[minmax(0,1fr)_auto]"><AIChatBox messages={[...(messagesQuery.data ?? []).map(message => ({ role: message.role, content: message.content })), ...(pendingUserMessage ? [{ role: "user" as const, content: pendingUserMessage }] : []), ...(streamedMessage ? [{ role: "assistant" as const, content: streamedMessage }] : [])]} onSendMessage={streamChat} onSendContext={input => input.url || input.attachmentNames?.length ? sendChat.mutate({ ...input, projectId: selectedProjectId, conversationId }) : streamChat(input.content)} onFileSelected={handleFile} attachments={attachments} isLoading={streaming || sendChat.isPending || uploadFile.isPending} height="100%" className="rounded-none border-0 shadow-none" placeholder="Give Autonova a task, a question, or a file to work with…" emptyStateMessage="What would you like to move forward today?" suggestedPrompts={["Turn my rough idea into a plan", "Review this project and identify the next step", "Summarize the files I attach"]} /></div>
      </section>

      <aside className="space-y-5">
        <section className="glass-panel rounded-[1.5rem] p-5"><div className="flex items-center justify-between"><div><p className="eyebrow">Live task</p><h2 className="mt-1 font-semibold">{activeTask ? activeTask.status.replaceAll("_", " ") : "Ready when you are"}</h2></div>{activeTask ? <span className={`size-2.5 rounded-full ${taskTone[activeTask.status] ?? "bg-slate-400"}`} /> : <CircleDotDashed className="size-5 text-muted-foreground" />}</div>{activeTask ? <><p className="mt-4 line-clamp-2 text-sm leading-6 text-muted-foreground">{activeTask.request}</p><div className="mt-5 flex items-center justify-between text-xs text-muted-foreground"><span>{activeTask.steps.filter(step => step.status === "COMPLETED").length} of {activeTask.steps.length} steps</span><span>{progress}%</span></div><Progress className="mt-2 h-1.5" value={progress} /><div className="mt-5 space-y-3">{activeTask.steps.slice(0, 4).map(step => <div key={step.id} className="flex gap-2.5"><span className="mt-0.5">{step.status === "COMPLETED" ? <CheckCircle2 className="size-4 text-emerald-500" /> : step.status === "FAILED" ? <XCircle className="size-4 text-rose-500" /> : <CircleDotDashed className={`size-4 ${step.status === "RUNNING" ? "text-primary" : "text-muted-foreground"}`} />}</span><div><p className="text-sm leading-5">{step.title}</p>{step.detail && <p className="mt-0.5 line-clamp-1 text-xs text-muted-foreground">{step.detail}</p>}</div></div>)}</div><Button variant="ghost" className="mt-4 w-full justify-between rounded-xl text-xs" onClick={() => window.location.assign("/tasks")}>Open task center <ChevronRight className="size-3.5" /></Button></> : <><p className="mt-3 text-sm leading-6 text-muted-foreground">New tasks start with a visible plan, then report concise action summaries as they progress.</p><Button className="mt-5 w-full rounded-xl" onClick={() => window.location.assign("/tasks")}><Sparkles className="mr-2 size-4" />Plan work</Button></>}</section>
        <section className="glass-panel rounded-[1.5rem] p-5"><div className="flex items-center justify-between"><div><p className="eyebrow">System view</p><h2 className="mt-1 font-semibold">Private by default</h2></div><ShieldCheck className="size-5 text-primary" /></div><div className="mt-4 grid grid-cols-2 gap-3"><div className="rounded-xl bg-muted/55 p-3"><p className="text-lg font-semibold">{usageQuery.data?.totals.inputTokens ?? 0}</p><p className="mt-0.5 text-[11px] text-muted-foreground">Input tokens</p></div><div className="rounded-xl bg-muted/55 p-3"><p className="text-lg font-semibold">{usageQuery.data?.totals.outputTokens ?? 0}</p><p className="mt-0.5 text-[11px] text-muted-foreground">Output tokens</p></div></div><div className="mt-4 border-t border-border/60 pt-4"><p className="text-xs font-medium text-muted-foreground">RECENT ACTIVITY</p>{activityQuery.data?.slice(0, 2).map(event => <p key={event.id} className="mt-2 text-xs leading-5 text-foreground/80">{event.title}</p>)}{!activityQuery.data?.length && <p className="mt-2 text-xs leading-5 text-muted-foreground">Agent actions will appear here after you begin a task.</p>}</div></section>
      </aside>
    </div>
  </div>;
}
