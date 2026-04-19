import cors from "cors";
import express from "express";
import { z } from "zod";
import {
  biodiversityUpload,
  handleAudioSync,
  handlePhotoLink,
} from "./biodiversity.js";
import { config } from "./config.js";
import { db } from "./db.js";
import {
  claimContribution,
  ensureUserRegistered,
  fetchRewardsActivity,
  fetchRelayJob,
  fetchWalletState,
  fulfillRelayJob,
  openRelayJob,
  prepareTip,
  submitTip,
} from "./solana/client.js";
import {
  acknowledgeRelayInbox,
  listRelayInbox,
  listPendingMeshRelayReplies,
  listVoiceRelayJobs,
  openVoiceRelayJob,
} from "./voiceRelay.js";

const app = express();
app.use(cors());
app.use(express.json({ limit: "1mb" }));

app.get("/health", (_req, res) => {
  res.json({ ok: true, network: "devnet" });
});

app.post(
  "/api/biodiversity/audio-sync",
  biodiversityUpload.single("audio"),
  (req, res) => {
    void handleAudioSync(req, res);
  },
);

app.post(
  "/api/biodiversity/photo-link",
  biodiversityUpload.single("photo"),
  (req, res) => {
    void handlePhotoLink(req, res);
  },
);

app.post("/v1/users/register", async (req, res) => {
  const schema = z.object({
    appUserId: z.string().min(1),
    displayName: z.string().min(1),
    walletPublicKey: z.string().min(32),
  });

  try {
    const body = schema.parse(req.body);
    const result = await ensureUserRegistered(body);
    res.json(result);
  } catch (error) {
    respondError(res, error);
  }
});

app.post("/v1/profile/upsert", async (req, res) => {
  const schema = z.object({
    appUserId: z.string().min(1),
    displayName: z.string().min(1),
    walletPublicKey: z.string().min(32),
    realName: z.string().nullable().optional(),
    phoneNumber: z.string().nullable().optional(),
    defaultRelayPhoneNumber: z.string().nullable().optional(),
  });

  try {
    const body = schema.parse(req.body);
    const now = new Date().toISOString();
    db.prepare(`
      INSERT INTO users (
        app_user_id,
        display_name,
        wallet_public_key,
        real_name,
        phone_number,
        default_relay_phone_number,
        created_at,
        updated_at
      ) VALUES (
        @appUserId,
        @displayName,
        @walletPublicKey,
        @realName,
        @phoneNumber,
        @defaultRelayPhoneNumber,
        @now,
        @now
      )
      ON CONFLICT(app_user_id) DO UPDATE SET
        display_name = excluded.display_name,
        wallet_public_key = excluded.wallet_public_key,
        real_name = excluded.real_name,
        phone_number = excluded.phone_number,
        default_relay_phone_number = excluded.default_relay_phone_number,
        updated_at = excluded.updated_at
    `).run({
      ...body,
      now,
    });

    res.json(body);
  } catch (error) {
    respondError(res, error);
  }
});

app.get("/v1/profile/:appUserId", async (req, res) => {
  try {
    const row = db
      .prepare(`
        SELECT
          app_user_id,
          display_name,
          wallet_public_key,
          real_name,
          phone_number,
          default_relay_phone_number
        FROM users
        WHERE app_user_id = ?
      `)
      .get(req.params.appUserId) as
      | {
          app_user_id: string;
          display_name: string;
          wallet_public_key: string;
          real_name: string | null;
          phone_number: string | null;
          default_relay_phone_number: string | null;
        }
      | undefined;
    if (!row) {
      throw new Error(`Unknown app user: ${req.params.appUserId}`);
    }

    res.json({
      appUserId: row.app_user_id,
      displayName: row.display_name,
      walletPublicKey: row.wallet_public_key,
      realName: row.real_name,
      phoneNumber: row.phone_number,
      defaultRelayPhoneNumber: row.default_relay_phone_number,
    });
  } catch (error) {
    respondError(res, error);
  }
});

app.get("/v1/users/:appUserId/wallet", async (req, res) => {
  try {
    const result = await fetchWalletState(req.params.appUserId);
    res.json(result);
  } catch (error) {
    respondError(res, error);
  }
});

app.get("/v1/users/:appUserId/rewards/activity", async (req, res) => {
  try {
    const result = await fetchRewardsActivity(req.params.appUserId);
    res.json(result);
  } catch (error) {
    respondError(res, error);
  }
});

app.post("/v1/contributions/claim", async (req, res) => {
  const schema = z.object({
    appUserId: z.string().min(1),
    reportId: z.string().min(1),
    title: z.string().min(1),
    description: z.string().default(""),
    type: z.enum(["hazard", "water", "species"]),
    lat: z.number(),
    lng: z.number(),
    timestamp: z.string().min(1),
    speciesName: z.string().nullable().optional(),
    confidence: z.number().nullable().optional(),
    photoUri: z.string().nullable().optional(),
  });

  try {
    const result = await claimContribution(schema.parse(req.body));
    res.json(result);
  } catch (error) {
    respondError(res, error);
  }
});

app.post("/v1/relay-jobs/open", async (req, res) => {
  const schema = z.object({
    appUserId: z.string().min(1),
    signedMessageBase64: z.string().min(1),
    signatureBase64: z.string().min(1),
    jobIdHex: z.string().length(64),
    destinationHashHex: z.string().length(64),
    payloadHashHex: z.string().length(64),
    expiryTs: z.number().int(),
    rewardAmount: z.number().int().positive(),
    nonce: z.number().int().nonnegative(),
  });

  try {
    const result = await openRelayJob(schema.parse(req.body));
    res.json(result);
  } catch (error) {
    respondError(res, error);
  }
});

app.post("/v1/relay-jobs/fulfill", async (req, res) => {
  const schema = z.object({
    appUserId: z.string().min(1),
    jobIdHex: z.string().length(64),
    proofRef: z.string().min(1),
  });

  try {
    const result = await fulfillRelayJob(schema.parse(req.body));
    res.json(result);
  } catch (error) {
    respondError(res, error);
  }
});

app.post("/v1/voice-relay/jobs/open", async (req, res) => {
  const schema = z.object({
    appUserId: z.string().min(1),
    senderWalletPublicKey: z.string().min(32),
    signedMessageBase64: z.string().min(1),
    signatureBase64: z.string().min(1),
    jobIdHex: z.string().length(64),
    destinationHashHex: z.string().length(64),
    payloadHashHex: z.string().length(64),
    expiryTs: z.number().int(),
    rewardAmount: z.number().int().positive(),
    nonce: z.number().int().nonnegative(),
    encryptedBlob: z.string().min(32),
    recipientName: z.string().min(1).optional(),
    recipientPhoneNumber: z.string().min(6).optional(),
    messageBody: z.string().min(1).optional(),
    contextJson: z.string().min(2).optional(),
  });

  try {
    const result = await openVoiceRelayJob(schema.parse(req.body));
    res.json(result);
  } catch (error) {
    respondError(res, error);
  }
});

app.get("/v1/voice-relay/jobs/:appUserId", async (req, res) => {
  try {
    const result = await listVoiceRelayJobs(req.params.appUserId);
    res.json(result);
  } catch (error) {
    respondError(res, error);
  }
});

app.get("/v1/voice-relay/inbox/:appUserId", async (req, res) => {
  try {
    const result = await listRelayInbox(req.params.appUserId);
    res.json(result);
  } catch (error) {
    respondError(res, error);
  }
});

app.get("/v1/voice-relay/mesh/:appUserId", async (req, res) => {
  try {
    const result = await listPendingMeshRelayReplies(req.params.appUserId);
    res.json(result);
  } catch (error) {
    respondError(res, error);
  }
});

app.post("/v1/voice-relay/inbox/:replyId/ack", async (req, res) => {
  try {
    const result = await acknowledgeRelayInbox(req.params.replyId);
    res.json(result);
  } catch (error) {
    respondError(res, error);
  }
});

app.get("/v1/relay-jobs/:jobIdHex", async (req, res) => {
  try {
    const result = await fetchRelayJob(req.params.jobIdHex);
    res.json(result);
  } catch (error) {
    respondError(res, error);
  }
});

app.post("/v1/karma/tip/prepare", async (req, res) => {
  const schema = z.object({
    appUserId: z.string().min(1),
    recipientWallet: z.string().min(32),
    amount: z.number().int().positive(),
  });

  try {
    const body = schema.parse(req.body);
    const result = await prepareTip(body.appUserId, body.recipientWallet, body.amount);
    res.json(result);
  } catch (error) {
    respondError(res, error);
  }
});

app.post("/v1/karma/tip/submit", async (req, res) => {
  const schema = z.object({
    appUserId: z.string().min(1),
    recipientWallet: z.string().min(32),
    amount: z.number().int().positive(),
    nonce: z.number().int().nonnegative(),
    tipIdHex: z.string().length(64),
    signedMessageBase64: z.string().min(1),
    signatureBase64: z.string().min(1),
  });

  try {
    const result = await submitTip(schema.parse(req.body));
    res.json(result);
  } catch (error) {
    respondError(res, error);
  }
});

app.listen(config.port, () => {
  console.log(`TrailKarma rewards backend listening on :${config.port}`);
});

function respondError(res: express.Response, error: unknown) {
  const message = error instanceof Error ? error.message : "Unknown error";
  res.status(400).json({ error: message });
}
