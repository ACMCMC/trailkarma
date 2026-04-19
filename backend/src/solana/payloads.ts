import crypto from "node:crypto";
import bs58 from "bs58";

export enum EventType {
  Hazard = 0,
  Water = 1,
  Species = 2,
  Relay = 3,
  Verification = 4,
}

export enum VerificationTier {
  Tier1AutoVerified = 0,
  Tier2ModelAssisted = 1,
  Tier3CommunityVerified = 2,
}

export enum BadgeCode {
  TrailScout = 0,
  RelayRanger = 1,
  SpeciesSpotter = 2,
  WaterGuardian = 3,
  HazardHerald = 4,
}

export type RelayIntent = {
  jobId: Uint8Array;
  senderWallet: string;
  destinationHash: Uint8Array;
  payloadHash: Uint8Array;
  expiryTs: number;
  rewardAmount: number;
  nonce: number;
};

export type TipIntent = {
  tipId: Uint8Array;
  senderWallet: string;
  recipientWallet: string;
  amount: number;
  nonce: number;
};

export function sha256Bytes(value: string): Uint8Array {
  return new Uint8Array(crypto.createHash("sha256").update(value).digest());
}

export function bytesToHex(value: Uint8Array): string {
  return Buffer.from(value).toString("hex");
}

export function hexToBytes(value: string): Uint8Array {
  return new Uint8Array(Buffer.from(value, "hex"));
}

export function base64ToBytes(value: string): Uint8Array {
  return new Uint8Array(Buffer.from(value, "base64"));
}

export function bytesToBase64(value: Uint8Array): string {
  return Buffer.from(value).toString("base64");
}

export function relayIntentMessage(intent: RelayIntent): Buffer {
  const buffer = Buffer.alloc(154);
  let offset = 0;
  buffer.writeUInt8(1, offset++);
  buffer.writeUInt8(1, offset++);
  copy(intent.jobId, buffer, offset); offset += 32;
  copy(bs58.decode(intent.senderWallet), buffer, offset); offset += 32;
  copy(intent.destinationHash, buffer, offset); offset += 32;
  copy(intent.payloadHash, buffer, offset); offset += 32;
  buffer.writeBigInt64LE(BigInt(intent.expiryTs), offset); offset += 8;
  buffer.writeBigUInt64LE(BigInt(intent.rewardAmount), offset); offset += 8;
  buffer.writeBigUInt64LE(BigInt(intent.nonce), offset);
  return buffer;
}

export function tipIntentMessage(intent: TipIntent): Buffer {
  const buffer = Buffer.alloc(114);
  let offset = 0;
  buffer.writeUInt8(2, offset++);
  buffer.writeUInt8(1, offset++);
  copy(intent.tipId, buffer, offset); offset += 32;
  copy(bs58.decode(intent.senderWallet), buffer, offset); offset += 32;
  copy(bs58.decode(intent.recipientWallet), buffer, offset); offset += 32;
  buffer.writeBigUInt64LE(BigInt(intent.amount), offset); offset += 8;
  buffer.writeBigUInt64LE(BigInt(intent.nonce), offset);
  return buffer;
}

function copy(input: Uint8Array, target: Buffer, offset: number) {
  if (input.length !== 32) {
    throw new Error("Expected 32-byte array");
  }
  Buffer.from(input).copy(target, offset);
}
