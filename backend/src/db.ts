import Database from "better-sqlite3";
import { config } from "./config.js";

export const db = new Database(config.sqlitePath);

db.exec(`
CREATE TABLE IF NOT EXISTS users (
  app_user_id TEXT PRIMARY KEY,
  display_name TEXT NOT NULL,
  wallet_public_key TEXT NOT NULL,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS wallet_mappings (
  wallet_public_key TEXT PRIMARY KEY,
  app_user_id TEXT NOT NULL,
  last_known_karma_balance TEXT,
  last_badges_json TEXT,
  updated_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS contribution_claims (
  contribution_id TEXT PRIMARY KEY,
  app_user_id TEXT NOT NULL,
  report_id TEXT,
  event_type TEXT NOT NULL,
  verification_tier TEXT NOT NULL,
  metadata_hash TEXT NOT NULL,
  reward_amount INTEGER NOT NULL,
  tx_signature TEXT NOT NULL,
  created_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS relay_jobs (
  job_id TEXT PRIMARY KEY,
  sender_app_user_id TEXT NOT NULL,
  sender_wallet TEXT NOT NULL,
  destination_hash TEXT NOT NULL,
  payload_hash TEXT NOT NULL,
  expiry_ts INTEGER NOT NULL,
  status TEXT NOT NULL,
  open_tx_signature TEXT,
  fulfill_tx_signature TEXT,
  fulfiller_app_user_id TEXT,
  updated_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS tx_audit (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  app_user_id TEXT,
  wallet_public_key TEXT,
  operation TEXT NOT NULL,
  tx_signature TEXT NOT NULL,
  payload_json TEXT NOT NULL,
  created_at TEXT NOT NULL
);
`);
