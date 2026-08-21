import { describe, expect, it } from "vitest";
import { describeProviderCapabilities } from "./db";

describe("provider-neutral capability matrix", () => {
  it("reports local requirements without treating optional remote services as configured", () => {
    const capabilities = describeProviderCapabilities(null);
    expect(capabilities.find(item => item.modality === "TEXT")).toMatchObject({ route: "LOCAL_DEVICE", availability: "REQUIRES_LOCAL_MODEL" });
    expect(capabilities.find(item => item.modality === "IMAGE")).toMatchObject({ availability: "NOT_CONFIGURED" });
  });

  it("labels configured remote text and image paths as optional and provider-dependent", () => {
    const capabilities = describeProviderCapabilities({ providerType: "OPENAI_COMPATIBLE", activeModel: "example-model", costMode: "BALANCED" });
    expect(capabilities.find(item => item.modality === "TEXT")).toMatchObject({ route: "OPTIONAL_REMOTE", availability: "CONFIGURED" });
    expect(capabilities.find(item => item.modality === "IMAGE")).toMatchObject({ availability: "PROVIDER_DEPENDENT" });
  });
});
