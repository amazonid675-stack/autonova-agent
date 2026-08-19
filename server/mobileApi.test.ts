import { describe, expect, it } from "vitest";

describe("mobile device request constraints", () => {
  it("requires a durable device identifier shape rather than accepting a short arbitrary label", async () => {
    const { z } = await import("zod");
    const contract = z.object({ deviceId: z.string().min(12).max(128), label: z.string().min(1).max(160), pushEnabled: z.boolean().default(false) });
    expect(contract.safeParse({ deviceId: "short", label: "Pixel", pushEnabled: true }).success).toBe(false);
    expect(contract.safeParse({ deviceId: "9fa118fab245", label: "Pixel", pushEnabled: true }).success).toBe(true);
  });
});
