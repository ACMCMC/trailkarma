import dotenv from "dotenv";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { PublicKey } from "@solana/web3.js";

dotenv.config();

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const repoRoot = path.resolve(__dirname, "..", "..");

function required(name: string): string {
  const value = process.env[name];
  if (!value) {
    throw new Error(`Missing required environment variable: ${name}`);
  }
  return value;
}

export const config = {
  port: Number(process.env.PORT ?? 3000),
  solanaRpcUrl: process.env.SOLANA_RPC_URL ?? "https://api.devnet.solana.com",
  programId: new PublicKey(required("PROGRAM_ID")),
  sponsorSecretKey: required("SPONSOR_SECRET_KEY"),
  attestorSecretKey: required("ATTESTOR_SECRET_KEY"),
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
};
