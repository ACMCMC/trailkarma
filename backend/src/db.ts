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

CREATE TABLE IF NOT EXISTS app_settings (
  key TEXT PRIMARY KEY,
  value TEXT NOT NULL,
  updated_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS voice_relay_jobs (
  job_id TEXT PRIMARY KEY,
  sender_app_user_id TEXT NOT NULL,
  carrier_app_user_id TEXT NOT NULL,
  sender_wallet TEXT NOT NULL,
  recipient_name TEXT NOT NULL,
  recipient_phone_number TEXT NOT NULL,
  message_body TEXT NOT NULL,
  context_summary TEXT NOT NULL,
  context_json TEXT NOT NULL,
  destination_hash TEXT NOT NULL,
  payload_hash TEXT NOT NULL,
  status TEXT NOT NULL,
  opened_tx_signature TEXT,
  fulfilled_tx_signature TEXT,
  call_sid TEXT,
  conversation_id TEXT,
  transcript_summary TEXT,
  reply_id TEXT,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  last_checked_at TEXT
);

CREATE TABLE IF NOT EXISTS voice_relay_inbox (
  reply_id TEXT PRIMARY KEY,
  original_job_id TEXT NOT NULL,
  user_app_id TEXT NOT NULL,
  sender_label TEXT NOT NULL,
  sender_phone_number TEXT NOT NULL,
  message_summary TEXT NOT NULL,
  message_body TEXT NOT NULL,
  context_json TEXT NOT NULL,
  status TEXT NOT NULL,
  acknowledged INTEGER NOT NULL DEFAULT 0,
  conversation_id TEXT,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL
);
`);

ensureColumn("users", "real_name", "TEXT");
ensureColumn("users", "phone_number", "TEXT");
ensureColumn("users", "default_relay_phone_number", "TEXT");

function ensureColumn(tableName: string, columnName: string, definition: string) {
  const columns = db.prepare(`PRAGMA table_info(${tableName})`).all() as Array<{ name: string }>;
  if (columns.some((column) => column.name === columnName)) return;
  db.exec(`ALTER TABLE ${tableName} ADD COLUMN ${columnName} ${definition}`);
}
