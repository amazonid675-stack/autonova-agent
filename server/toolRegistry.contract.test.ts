import { describe, expect, it } from "vitest";
import { toolDefinitions } from "./routers/agent";

describe("modular tool contract", () => {
  it("requires complete metadata and a transmission disclosure for every registered tool", () => {
    for (const tool of toolDefinitions) {
      expect(tool.version.trim().length).toBeGreaterThan(0);
      expect(tool.inputSchema.trim().length).toBeGreaterThan(0);
      expect(tool.outputSchema.trim().length).toBeGreaterThan(0);
      expect(tool.transmissionDisclosure.trim().length).toBeGreaterThan(0);
      expect(tool.timeoutSeconds).toBeGreaterThan(0);
      expect(tool.retryStrategy.trim().length).toBeGreaterThan(0);
      expect(tool.auditPolicy.trim().length).toBeGreaterThan(0);
      if (tool.networkRequired) expect(tool.transmissionDisclosure).toMatch(/sends/i);
    }
  });
});
