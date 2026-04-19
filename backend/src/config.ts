import dotenv from "dotenv";
import path from "node:path";
import { fileURLToPath } from "node:url";

dotenv.config();

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const repoRoot = path.resolve(__dirname, "..", "..");

export const config = {
  port: Number(process.env.PORT ?? 3000),
  solanaRpcUrl: process.env.SOLANA_RPC_URL ?? "https://api.devnet.solana.com",
  programId: process.env.PROGRAM_ID ?? null,
  sponsorSecretKey: process.env.SPONSOR_SECRET_KEY ?? null,
  attestorSecretKey: process.env.ATTESTOR_SECRET_KEY ?? null,
  elevenLabsApiKey: process.env.ELEVENLABS_API_KEY ?? null,
  elevenLabsAgentId: process.env.ELEVENLABS_AGENT_ID ?? null,
  elevenLabsPhoneNumberId: process.env.ELEVENLABS_PHONE_NUMBER_ID ?? null,
  twilioAccountSid: process.env.TWILIO_ACCOUNT_SID ?? null,
  twilioAuthToken: process.env.TWILIO_AUTH_TOKEN ?? null,
  twilioPhoneNumber: process.env.TWILIO_PHONE_NUMBER ?? null,
  publicBaseUrl: process.env.PUBLIC_BASE_URL ?? null,
  idlPath:
    process.env.ANCHOR_IDL_PATH ??
    path.join(repoRoot, "solana", "target", "idl", "trail_karma_rewards.json"),
  sqlitePath: process.env.SQLITE_PATH ?? path.join(repoRoot, "backend", "trailkarma.db"),
  configSeed: "config",
  relayEncryptionPrivateKey: process.env.RELAY_ENCRYPTION_PRIVATE_KEY ?? null,
};

export function missingSolanaEnv(): string[] {
  const missing: string[] = [];
  if (!config.programId) missing.push("PROGRAM_ID");
  if (!config.sponsorSecretKey) missing.push("SPONSOR_SECRET_KEY");
  if (!config.attestorSecretKey) missing.push("ATTESTOR_SECRET_KEY");
  return missing;
}

export function requireSolanaEnv() {
  const missing = missingSolanaEnv();
  if (missing.length > 0) {
    throw new Error(
      `Missing required Solana environment variable${missing.length > 1 ? "s" : ""}: ${missing.join(", ")}`,
    );
  }

  return {
    programId: config.programId!,
    sponsorSecretKey: config.sponsorSecretKey!,
    attestorSecretKey: config.attestorSecretKey!,
  };
}
