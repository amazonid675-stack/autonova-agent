import { describe, expect, it } from "vitest";
import { executeWorkspaceFileWrite } from "./githubWorkspace";

describe("confirmed scoped workspace GitHub write", () => {
  const input = { repository: "owner/repo", filePath: "notes/plan.md", content: "Selected local text", commitMessage: "Sync plan", token: "encrypted-at-rest-only-in-production" };

  it("creates an explicit contents request only when the approved execution helper runs", async () => {
    let request: RequestInit | undefined;
    const summary = await executeWorkspaceFileWrite({ ...input, fetchImpl: async (_url, init) => { request = init; return { ok: true, status: 201, json: async () => ({ content: { path: "notes/plan.md", sha: "abc" }, commit: { sha: "def" } }) }; } });
    expect(request?.method).toBe("PUT");
    expect(String(request?.body)).toContain("U2VsZWN0ZWQgbG9jYWwgdGV4dA==");
    expect(summary).toContain("Wrote notes/plan.md");
  });

  it("reports a changed-file conflict without retrying or overwriting", async () => {
    await expect(executeWorkspaceFileWrite({ ...input, fetchImpl: async () => ({ ok: false, status: 409, json: async () => ({}) }) })).rejects.toThrow("file or branch changed");
  });
});
