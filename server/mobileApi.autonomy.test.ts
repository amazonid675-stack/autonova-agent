import { describe, expect, it } from "vitest";
import { githubOperationSchema, improvementDecisionSchema, improvementSchema, isPrivateAddress, researchSchema, taskEscalationSchema, taskObservationSchema, taskRepairSchema, taskToolApprovalSchema, taskToolSchema, taskVerificationSchema } from "./mobileApi";

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
    expect(githubOperationSchema.safeParse({ repository: "owner/repo", operation: "WRITE_WORKSPACE_FILE", title: "Sync workspace note", filePath: "../secrets.txt", content: "not allowed" }).success).toBe(false);
    expect(githubOperationSchema.safeParse({ repository: "owner/repo", operation: "WRITE_WORKSPACE_FILE", title: "Sync workspace note", filePath: "notes/plan.md", content: "Selected from Android scoped storage." }).success).toBe(true);
  });

  it("requires evidence, versioning, and an explicit review note for controlled improvements", () => {
    expect(improvementSchema.safeParse({ scope: "WORKFLOW", title: "Better plan", proposedChange: "Add verification" }).success).toBe(false);
    expect(improvementSchema.safeParse({ scope: "WORKFLOW", title: "Better plan", proposedChange: "Add verification before completion", evidence: "The existing task log lacked outcome evidence.", testOutcome: "The lifecycle regression passed.", benchmarkSummary: "Completion evidence coverage improved from missing to required.", versionLabel: "1.1" }).success).toBe(true);
    expect(improvementDecisionSchema.safeParse({ status: "APPROVED", note: "ok" }).success).toBe(false);
    expect(improvementDecisionSchema.safeParse({ status: "ROLLED_BACK", note: "The benchmark regressed." }).success).toBe(true);
  });

  it("rejects incomplete observations, unregistered tools, empty repairs, and evidence-free verification", () => {
    expect(taskObservationSchema.safeParse({ summary: "no" }).success).toBe(false);
    expect(taskToolSchema.safeParse({ toolKey: "unbounded_shell", rationale: "Unsafe" }).success).toBe(false);
    expect(taskToolApprovalSchema.safeParse({ toolKey: "github", approved: true, note: "yes" }).success).toBe(true);
    expect(taskRepairSchema.safeParse({ diagnosis: "A failure happened", repairSteps: [] }).success).toBe(false);
    expect(taskEscalationSchema.safeParse({ level: "HUMAN_REVIEW", summary: "Need owner review" }).success).toBe(true);
    expect(taskVerificationSchema.safeParse({ passed: true, evidence: "no" }).success).toBe(false);
    expect(taskVerificationSchema.safeParse({ passed: true, evidence: "Validated against the requested output." }).success).toBe(true);
  });
});
