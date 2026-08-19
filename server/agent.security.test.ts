import { describe, expect, it } from "vitest";
import { appRouter } from "./routers";
import { redactSecrets } from "./security";
import type { TrpcContext } from "./_core/context";

function context(user: TrpcContext["user"]): TrpcContext {
  return {
    user,
    req: { protocol: "https", headers: {} } as TrpcContext["req"],
    res: { clearCookie: () => undefined } as TrpcContext["res"],
  };
}

describe("agent security boundaries", () => {
  it("does not permit unauthenticated access to protected agent routes", async () => {
    const caller = appRouter.createCaller(context(null));
    await expect(caller.agent.projects.list()).rejects.toMatchObject({ code: "UNAUTHORIZED" });
  });

  it("rejects underspecified task requests before persistence or model execution", async () => {
    const caller = appRouter.createCaller(context({
      id: 1,
      openId: "test-user",
      name: "Test User",
      email: "test@example.com",
      loginMethod: "manus",
      role: "user",
      createdAt: new Date(),
      updatedAt: new Date(),
      lastSignedIn: new Date(),
    }));
    await expect(caller.agent.tasks.create({ request: "no" })).rejects.toMatchObject({ code: "BAD_REQUEST" });
  });

  it("rejects unknown tools before a policy can be written", async () => {
    const caller = appRouter.createCaller(context({
      id: 1,
      openId: "test-user",
      name: "Test User",
      email: "test@example.com",
      loginMethod: "manus",
      role: "user",
      createdAt: new Date(),
      updatedAt: new Date(),
      lastSignedIn: new Date(),
    }));
    await expect(caller.agent.tools.setPermission({ toolKey: "unknown_tool" as never, policy: "ALLOW" })).rejects.toMatchObject({ code: "BAD_REQUEST" });
  });
});

describe("secret redaction", () => {
  it("removes common API keys and bearer values from persisted response text", () => {
    const unsafe = "token sk-super-secret-123456 and Authorization: Bearer private-value ghp_abcdefghijklmnopqrst";
    const safe = redactSecrets(unsafe);
    expect(safe).not.toContain("super-secret");
    expect(safe).not.toContain("private-value");
    expect(safe).not.toContain("abcdefghijklmnopqrst");
    expect(safe).toContain("[REDACTED]");
  });
});
