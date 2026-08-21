type FetchLike = (input: string, init?: RequestInit) => Promise<{ ok: boolean; status: number; json(): Promise<unknown> }>;

export async function executeWorkspaceFileWrite(input: { repository: string; filePath: string; content: string; commitMessage: string; branch?: string; expectedSha?: string; token: string; fetchImpl?: FetchLike }) {
  const fetchImpl = input.fetchImpl ?? fetch as unknown as FetchLike;
  const path = input.filePath.split("/").map(encodeURIComponent).join("/");
  const response = await fetchImpl(`https://api.github.com/repos/${input.repository}/contents/${path}`, {
    method: "PUT",
    headers: { Accept: "application/vnd.github+json", Authorization: `Bearer ${input.token}`, "X-GitHub-Api-Version": "2022-11-28", "Content-Type": "application/json" },
    body: JSON.stringify({ message: input.commitMessage, content: Buffer.from(input.content, "utf8").toString("base64"), branch: input.branch || undefined, sha: input.expectedSha || undefined }),
    signal: AbortSignal.timeout(20_000),
  });
  if (!response.ok) {
    const conflict = response.status === 409 || response.status === 422;
    throw new Error(conflict ? `GitHub rejected the workspace sync because the file or branch changed (HTTP ${response.status}). Refresh repository context, review the conflict, and prepare a new explicit write.` : `GitHub workspace file write failed with HTTP ${response.status}.`);
  }
  const result = await response.json() as { content?: { path?: string; sha?: string }; commit?: { sha?: string; html_url?: string } };
  return `Wrote ${result.content?.path ?? input.filePath} at ${result.content?.sha ?? "unknown SHA"}; commit ${result.commit?.sha ?? "created"}${result.commit?.html_url ? `: ${result.commit.html_url}` : ""}.`;
}
