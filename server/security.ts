import crypto from "crypto";
import { ENV } from "./_core/env";

function encryptionKey() {
  const secret = ENV.cookieSecret || process.env.JWT_SECRET;
  if (!secret) throw new Error("Secure configuration is unavailable.");
  return crypto.createHash("sha256").update(secret).digest();
}

export function encryptSecret(value: string) {
  const iv = crypto.randomBytes(12);
  const cipher = crypto.createCipheriv("aes-256-gcm", encryptionKey(), iv);
  const ciphertext = Buffer.concat([cipher.update(value, "utf8"), cipher.final()]);
  const tag = cipher.getAuthTag();
  return [iv, tag, ciphertext].map(part => part.toString("base64url")).join(".");
}

export function decryptSecret(value: string) {
  const [ivValue, tagValue, ciphertextValue] = value.split(".");
  if (!ivValue || !tagValue || !ciphertextValue) throw new Error("Secure configuration is unavailable.");
  const decipher = crypto.createDecipheriv("aes-256-gcm", encryptionKey(), Buffer.from(ivValue, "base64url"));
  decipher.setAuthTag(Buffer.from(tagValue, "base64url"));
  return Buffer.concat([decipher.update(Buffer.from(ciphertextValue, "base64url")), decipher.final()]).toString("utf8");
}

export function assertSafeProviderUrl(value: string) {
  const url = new URL(value);
  const host = url.hostname.toLowerCase();
  const privateIpv4 = /^(127\.|10\.|192\.168\.|169\.254\.|172\.(1[6-9]|2\d|3[0-1])\.)/.test(host);
  if (url.protocol !== "https:" || host === "localhost" || host.endsWith(".local") || privateIpv4 || host === "::1") {
    throw new Error("Only public HTTPS provider endpoints are permitted.");
  }
  return url.toString().replace(/\/$/, "");
}

export function redactSecrets(value: string) {
  return value
    .replace(/(sk-[a-zA-Z0-9_-]{10,})/g, "[REDACTED]")
    .replace(/(gh[pousr]_[a-zA-Z0-9]{10,})/g, "[REDACTED]")
    .replace(/(Bearer\s+)[^\s]+/gi, "$1[REDACTED]");
}
