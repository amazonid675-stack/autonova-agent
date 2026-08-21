import { describe, expect, it } from "vitest";
import { githubOperationSchema, isPrivateAddress, researchSchema } from "./mobileApi";

describe("mobile autonomy request contracts", () => {
  it("rejects local, non-HTTPS, and over-broad source batches before research work begins", () => {
    expect(researchSchema.safeParse({ query: "Compare sources", sources: ["http://example.com"] }).success).toBe(false);
    expect(researchSchema.safeParse({ query: "Compare sources", sources: ["https://example.com"] }).success).toBe(true);
    expect(researchSchema.safeParse({ query: "Compare sources", sources: Array.from({ length: 6 }, () => "https://example.com") }).success).toBe(false);
    expect(isPrivateAddress("127.0.0.1")).toBe(true);
    expect(isPrivateAddress("10.0.0.1")).toBe(true);
    expect(isPrivateAddress("8.8.8.8")).toBe(false);
  });

  it("requires complete details before a GitHub write can reach the confirmation queue", () => {
    expect(githubOperationSchema.safeParse({ repository: "owner/repo", operation: "CREATE_ISSUE" }).success).toBe(false);
    expect(githubOperationSchema.safeParse({ repository: "owner/repo", operation: "CREATE_ISSUE", title: "Track offline sync" }).success).toBe(true);
    expect(githubOperationSchema.safeParse({ repository: "owner/repo", operation: "CREATE_BRANCH", branch: "agent/fix", fromBranch: "main" }).success).toBe(true);
    expect(githubOperationSchema.safeParse({ repository: "owner/repo", operation: "CREATE_PULL_REQUEST", title: "Agent proposal", head: "agent/fix", base: "main" }).success).toBe(true);
  });
});
