import cors from "cors";
import express from "express";
import { z } from "zod";
import { config } from "./config.js";
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

const app = express();
app.use(cors());
app.use(express.json({ limit: "1mb" }));

app.get("/health", (_req, res) => {
  res.json({ ok: true, network: "devnet" });
});

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
