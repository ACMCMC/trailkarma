import fs from "node:fs";
import crypto from "node:crypto";
import * as anchor from "@anchor-lang/core";
import {
  Connection,
  Ed25519Program,
  Keypair,
  PublicKey,
  SystemProgram,
  Transaction,
} from "@solana/web3.js";
import {
  ASSOCIATED_TOKEN_PROGRAM_ID,
  TOKEN_2022_PROGRAM_ID,
  getAssociatedTokenAddressSync,
} from "@solana/spl-token";
import bs58 from "bs58";
import BN from "bn.js";
import { config } from "../config.js";
import { db } from "../db.js";
import {
  BadgeCode,
  EventType,
  VerificationTier,
  relayIntentMessage,
  sha256Bytes,
  tipIntentMessage,
} from "./payloads.js";

type AnyRecord = Record<string, unknown>;
type AnchorEnumVariant = Record<string, Record<string, never>>;

const connection = new Connection(config.solanaRpcUrl, "confirmed");
const sponsor = Keypair.fromSecretKey(bs58.decode(config.sponsorSecretKey));
const attestor = Keypair.fromSecretKey(bs58.decode(config.attestorSecretKey));
const provider = new anchor.AnchorProvider(
  connection,
  new anchor.Wallet(sponsor),
  anchor.AnchorProvider.defaultOptions(),
);
anchor.setProvider(provider);

function loadProgram(): anchor.Program {
  const idl = JSON.parse(fs.readFileSync(config.idlPath, "utf8")) as anchor.Idl & { address?: string };
  idl.address = config.programId.toBase58();
  return new anchor.Program(idl, provider);
}

export const program = loadProgram() as any;

export const configPda = PublicKey.findProgramAddressSync(
  [Buffer.from(config.configSeed)],
  config.programId,
)[0];

export function userProfilePda(wallet: PublicKey): PublicKey {
  return PublicKey.findProgramAddressSync(
    [Buffer.from("user_profile"), wallet.toBuffer()],
    config.programId,
  )[0];
}

export function contributionReceiptPda(receiptId: Uint8Array): PublicKey {
  return PublicKey.findProgramAddressSync(
    [Buffer.from("contribution_receipt"), Buffer.from(receiptId)],
    config.programId,
  )[0];
}

export function relayJobPda(jobId: Uint8Array): PublicKey {
  return PublicKey.findProgramAddressSync(
    [Buffer.from("relay_job"), Buffer.from(jobId)],
    config.programId,
  )[0];
}

export function badgeClaimPda(wallet: PublicKey, badgeCode: BadgeCode): PublicKey {
  return PublicKey.findProgramAddressSync(
    [Buffer.from("badge_claim"), wallet.toBuffer(), Buffer.from([badgeCode])],
    config.programId,
  )[0];
}

export function tipReceiptPda(tipId: Uint8Array): PublicKey {
  return PublicKey.findProgramAddressSync(
    [Buffer.from("tip_receipt"), Buffer.from(tipId)],
    config.programId,
  )[0];
}

export async function fetchConfigAccount(): Promise<AnyRecord> {
  return (await program.account.config.fetch(configPda)) as AnyRecord;
}

export async function ensureUserRegistered(input: {
  appUserId: string;
  displayName: string;
  walletPublicKey: string;
}) {
  const wallet = new PublicKey(input.walletPublicKey);
  const profilePda = userProfilePda(wallet);
  const profileInfo = await connection.getAccountInfo(profilePda);
  const cfg = await fetchConfigAccount();
  const karmaMint = new PublicKey(String(cfg.karmaMint));
  const karmaAta = getAssociatedTokenAddressSync(
    karmaMint,
    wallet,
    false,
    TOKEN_2022_PROGRAM_ID,
    ASSOCIATED_TOKEN_PROGRAM_ID,
  );

  if (!profileInfo) {
    const txSig = await program.methods
      .registerUser({
        appUserIdHash: Array.from(sha256Bytes(input.appUserId)),
      })
      .accounts({
        payer: sponsor.publicKey,
        config: configPda,
        userProfile: profilePda,
        userWallet: wallet,
        karmaMint,
        userKarmaAta: karmaAta,
        tokenProgram: TOKEN_2022_PROGRAM_ID,
        associatedTokenProgram: ASSOCIATED_TOKEN_PROGRAM_ID,
        systemProgram: SystemProgram.programId,
      })
      .signers([sponsor])
      .rpc();

    audit(input.appUserId, wallet.toBase58(), "register_user", txSig, {
      wallet: wallet.toBase58(),
    });
  }

  const now = new Date().toISOString();
  db.prepare(`
    INSERT INTO users (app_user_id, display_name, wallet_public_key, created_at, updated_at)
    VALUES (@appUserId, @displayName, @walletPublicKey, @now, @now)
    ON CONFLICT(app_user_id) DO UPDATE SET
      display_name = excluded.display_name,
      wallet_public_key = excluded.wallet_public_key,
      updated_at = excluded.updated_at
  `).run({
    appUserId: input.appUserId,
    displayName: input.displayName,
    walletPublicKey: wallet.toBase58(),
    now,
  });

  return fetchWalletState(input.appUserId);
}

export async function fetchWalletState(appUserId: string) {
  const row = db
    .prepare("SELECT app_user_id, display_name, wallet_public_key FROM users WHERE app_user_id = ?")
    .get(appUserId) as { app_user_id: string; display_name: string; wallet_public_key: string } | undefined;
  if (!row) {
    throw new Error(`Unknown app user: ${appUserId}`);
  }

  const wallet = new PublicKey(row.wallet_public_key);
  const cfg = await fetchConfigAccount();
  const karmaMint = new PublicKey(String(cfg.karmaMint));
  const karmaAta = getAssociatedTokenAddressSync(
    karmaMint,
    wallet,
    false,
    TOKEN_2022_PROGRAM_ID,
    ASSOCIATED_TOKEN_PROGRAM_ID,
  );
  const karmaBalance = await connection.getTokenAccountBalance(karmaAta).catch(() => null);
  const profilePda = userProfilePda(wallet);
  const profile = await program.account.userProfile.fetchNullable(profilePda);

  const badgeMap = [
    { code: BadgeCode.TrailScout, mint: String(cfg.trailScoutMint), label: "Trail Scout" },
    { code: BadgeCode.RelayRanger, mint: String(cfg.relayRangerMint), label: "Relay Ranger" },
    { code: BadgeCode.SpeciesSpotter, mint: String(cfg.speciesSpotterMint), label: "Species Spotter" },
    { code: BadgeCode.WaterGuardian, mint: String(cfg.waterGuardianMint), label: "Water Guardian" },
    { code: BadgeCode.HazardHerald, mint: String(cfg.hazardHeraldMint), label: "Hazard Herald" },
  ];

  const ownedBadgeLabels: string[] = [];
  for (const badge of badgeMap) {
    const badgeMint = new PublicKey(badge.mint);
    const badgeAta = getAssociatedTokenAddressSync(
      badgeMint,
      wallet,
      false,
      TOKEN_2022_PROGRAM_ID,
      ASSOCIATED_TOKEN_PROGRAM_ID,
    );
    const balance = await connection.getTokenAccountBalance(badgeAta).catch(() => null);
    if (balance?.value?.uiAmount && balance.value.uiAmount > 0) {
      ownedBadgeLabels.push(badge.label);
    }
  }

  const contributionCounts = db.prepare(`
    SELECT event_type, COUNT(*) AS count
    FROM contribution_claims
    WHERE app_user_id = ?
    GROUP BY event_type
  `).all(appUserId) as Array<{ event_type: string; count: number }>;
  const contributionCountMap = Object.fromEntries(
    contributionCounts.map((row) => [row.event_type, Number(row.count)]),
  );
  const hazardCount = contributionCountMap.hazard ?? 0;
  const waterCount = contributionCountMap.water ?? 0;
  const speciesCount = contributionCountMap.species ?? 0;
  const relayCountRow = db
    .prepare(`
      SELECT COUNT(*) AS count
      FROM relay_jobs
      WHERE fulfiller_app_user_id = ?
        AND status = 'fulfilled'
    `)
    .get(appUserId) as { count: number };
  const relayCount = Number(relayCountRow?.count ?? 0);
  const verifiedContributionCount = hazardCount + waterCount + speciesCount + relayCount;
  const totalKarmaEarnedRow = db
    .prepare(`
      SELECT COALESCE(SUM(reward_amount), 0) AS total
      FROM contribution_claims
      WHERE app_user_id = ?
    `)
    .get(appUserId) as { total: number };
  const totalKarmaEarned = Number(totalKarmaEarnedRow?.total ?? 0) + relayCount * Number(cfg.rewardRelay);

  const badgeDetails = [
    {
      code: "trail_scout",
      label: "Trail Scout",
      description: "Awarded for your first verified contribution.",
      category: "Contribution",
      accentHex: "#E7A64F",
      earned: ownedBadgeLabels.includes("Trail Scout"),
      currentCount: verifiedContributionCount,
      targetCount: 1,
      mint: String(cfg.trailScoutMint),
    },
    {
      code: "relay_ranger",
      label: "Relay Ranger",
      description: "Earned after your first successful relay mission.",
      category: "Relay",
      accentHex: "#4FB7A5",
      earned: ownedBadgeLabels.includes("Relay Ranger"),
      currentCount: relayCount,
      targetCount: 1,
      mint: String(cfg.relayRangerMint),
    },
    {
      code: "species_spotter",
      label: "Species Spotter",
      description: "Unlocked by contributing a verified biodiversity sighting.",
      category: "Biodiversity",
      accentHex: "#6A8E4A",
      earned: ownedBadgeLabels.includes("Species Spotter"),
      currentCount: speciesCount,
      targetCount: 1,
      mint: String(cfg.speciesSpotterMint),
    },
    {
      code: "water_guardian",
      label: "Water Guardian",
      description: "Granted after five verified water condition reports.",
      category: "Trail Safety",
      accentHex: "#3D8DCC",
      earned: ownedBadgeLabels.includes("Water Guardian"),
      currentCount: waterCount,
      targetCount: 5,
      mint: String(cfg.waterGuardianMint),
    },
    {
      code: "hazard_herald",
      label: "Hazard Herald",
      description: "Granted after five verified hazard reports.",
      category: "Trail Safety",
      accentHex: "#C56A4A",
      earned: ownedBadgeLabels.includes("Hazard Herald"),
      currentCount: hazardCount,
      targetCount: 5,
      mint: String(cfg.hazardHeraldMint),
    },
  ];

  db.prepare(`
    INSERT INTO wallet_mappings (wallet_public_key, app_user_id, last_known_karma_balance, last_badges_json, updated_at)
    VALUES (@wallet, @appUserId, @karmaBalance, @badges, @updatedAt)
    ON CONFLICT(wallet_public_key) DO UPDATE SET
      app_user_id = excluded.app_user_id,
      last_known_karma_balance = excluded.last_known_karma_balance,
      last_badges_json = excluded.last_badges_json,
      updated_at = excluded.updated_at
  `).run({
    wallet: wallet.toBase58(),
    appUserId,
    karmaBalance: karmaBalance?.value?.amount ?? "0",
    badges: JSON.stringify(ownedBadgeLabels),
    updatedAt: new Date().toISOString(),
  });

  return {
    appUserId,
    displayName: row.display_name,
    walletPublicKey: wallet.toBase58(),
    karmaBalance: karmaBalance?.value?.uiAmountString ?? "0",
    badges: ownedBadgeLabels,
    badgeDetails,
    rewardStats: {
      totalKarmaEarned,
      verifiedContributionCount,
      hazardCount,
      waterCount,
      speciesCount,
      relayCount,
      badgeCount: badgeDetails.filter((badge) => badge.earned).length,
    },
    profile,
  };
}

export async function fetchRewardsActivity(appUserId: string) {
  const contributionRows = db.prepare(`
    SELECT contribution_id, event_type, verification_tier, reward_amount, tx_signature, created_at
    FROM contribution_claims
    WHERE app_user_id = ?
    ORDER BY created_at DESC
    LIMIT 25
  `).all(appUserId) as Array<{
    contribution_id: string;
    event_type: string;
    verification_tier: string;
    reward_amount: number;
    tx_signature: string;
    created_at: string;
  }>;

  const relayRows = db.prepare(`
    SELECT job_id, status, fulfill_tx_signature, updated_at
    FROM relay_jobs
    WHERE fulfiller_app_user_id = ?
      AND status = 'fulfilled'
    ORDER BY updated_at DESC
    LIMIT 25
  `).all(appUserId) as Array<{
    job_id: string;
    status: string;
    fulfill_tx_signature: string | null;
    updated_at: string;
  }>;

  const auditRows = db.prepare(`
    SELECT id, operation, tx_signature, payload_json, created_at
    FROM tx_audit
    WHERE app_user_id = ?
      AND operation IN ('claim_badge', 'tip_karma')
    ORDER BY created_at DESC
    LIMIT 25
  `).all(appUserId) as Array<{
    id: number;
    operation: string;
    tx_signature: string;
    payload_json: string;
    created_at: string;
  }>;

  const contributionItems = contributionRows.map((row) => ({
    id: `contribution:${row.contribution_id}`,
    kind: "contribution_reward",
    title: `${capitalize(row.event_type)} contribution verified`,
    subtitle: `Verification tier: ${humanizeTier(row.verification_tier)}`,
    occurredAt: row.created_at,
    karmaDelta: Number(row.reward_amount),
    txSignature: row.tx_signature,
    status: "confirmed",
  }));

  const relayItems = relayRows.map((row) => ({
    id: `relay:${row.job_id}`,
    kind: "relay_reward",
    title: "Relay mission fulfilled",
    subtitle: `First valid relay claimant secured the reward for job ${row.job_id.slice(0, 8)}...`,
    occurredAt: row.updated_at,
    karmaDelta: 12,
    txSignature: row.fulfill_tx_signature ?? undefined,
    status: row.status,
  }));

  const auditItems = auditRows
    .map((row) => {
      const payload = parseJson(row.payload_json);
      if (row.operation === "claim_badge") {
        const badgeLabel = badgeLabelFromPayload(payload);
        return {
          id: `audit:${row.id}`,
          kind: "badge_earned",
          title: `${badgeLabel} unlocked`,
          subtitle: "Collectible minted to your wallet on Devnet.",
          occurredAt: row.created_at,
          badgeLabel,
          txSignature: row.tx_signature,
          status: "confirmed",
        };
      }

      if (row.operation === "tip_karma") {
        const amount = Number(payload.amount ?? 0);
        const recipientWallet = String(payload.recipientWallet ?? "");
        return {
          id: `audit:${row.id}`,
          kind: "tip_sent",
          title: "KARMA tip sent",
          subtitle: `Sent ${amount} KARMA to ${shortWallet(recipientWallet)}`,
          occurredAt: row.created_at,
          karmaDelta: amount > 0 ? -amount : amount,
          txSignature: row.tx_signature,
          status: "confirmed",
        };
      }

      return null;
    })
    .filter((item): item is NonNullable<typeof item> => item !== null);

  const items = [...contributionItems, ...relayItems, ...auditItems]
    .sort((a, b) => Date.parse(b.occurredAt) - Date.parse(a.occurredAt))
    .slice(0, 30);

  return { items };
}

export async function claimContribution(input: {
  appUserId: string;
  reportId: string;
  title: string;
  description: string;
  type: "hazard" | "water" | "species";
  lat: number;
  lng: number;
  timestamp: string;
  speciesName?: string | null;
  confidence?: number | null;
  photoUri?: string | null;
}) {
  const user = db.prepare("SELECT wallet_public_key FROM users WHERE app_user_id = ?").get(input.appUserId) as
    | { wallet_public_key: string }
    | undefined;
  if (!user) {
    throw new Error(`Unknown app user: ${input.appUserId}`);
  }

  const wallet = new PublicKey(user.wallet_public_key);
  const profilePda = userProfilePda(wallet);
  const cfg = await fetchConfigAccount();
  const karmaMint = new PublicKey(String(cfg.karmaMint));
  const karmaAta = getAssociatedTokenAddressSync(
    karmaMint,
    wallet,
    false,
    TOKEN_2022_PROGRAM_ID,
    ASSOCIATED_TOKEN_PROGRAM_ID,
  );

  const contributionId = sha256Bytes(`${input.reportId}:${input.timestamp}:${input.type}`);
  const metadataHash = sha256Bytes(
    JSON.stringify({
      reportId: input.reportId,
      title: input.title,
      description: input.description,
      lat: input.lat,
      lng: input.lng,
      speciesName: input.speciesName ?? null,
      photoUri: input.photoUri ?? null,
    }),
  );
  const eventType =
    input.type === "hazard"
      ? EventType.Hazard
      : input.type === "water"
        ? EventType.Water
        : EventType.Species;
  const verificationTier =
    input.type === "species" && (input.confidence ?? 0) >= 0.8
      ? VerificationTier.Tier2ModelAssisted
      : VerificationTier.Tier1AutoVerified;
  const highConfidenceBonus = input.type === "species" && Boolean((input.confidence ?? 0) >= 0.9 && input.photoUri);
  const receiptPda = contributionReceiptPda(contributionId);

  const txSig = await program.methods
    .claimContributionReward({
      contributionId: Array.from(contributionId),
      eventType: anchorEventType(eventType),
      verificationTier: anchorVerificationTier(verificationTier),
      metadataHash: Array.from(metadataHash),
      highConfidenceBonus,
    })
    .accounts({
      payer: sponsor.publicKey,
      attestor: attestor.publicKey,
      config: configPda,
      userProfile: profilePda,
      userWallet: wallet,
      contributionReceipt: receiptPda,
      karmaMint,
      userKarmaAta: karmaAta,
      tokenProgram: TOKEN_2022_PROGRAM_ID,
      associatedTokenProgram: ASSOCIATED_TOKEN_PROGRAM_ID,
      systemProgram: SystemProgram.programId,
    })
    .signers([sponsor, attestor])
    .rpc();

  audit(input.appUserId, wallet.toBase58(), "claim_contribution_reward", txSig, input);

  const rewardAmount =
    input.type === "hazard"
      ? Number(cfg.rewardHazard)
      : input.type === "water"
        ? Number(cfg.rewardWater)
        : Number(cfg.rewardSpecies) + (highConfidenceBonus ? Number(cfg.rewardSpeciesBonus) : 0);

  db.prepare(`
    INSERT INTO contribution_claims
      (contribution_id, app_user_id, report_id, event_type, verification_tier, metadata_hash, reward_amount, tx_signature, created_at)
    VALUES (@contributionId, @appUserId, @reportId, @eventType, @verificationTier, @metadataHash, @rewardAmount, @txSignature, @createdAt)
  `).run({
    contributionId: Buffer.from(contributionId).toString("hex"),
    appUserId: input.appUserId,
    reportId: input.reportId,
    eventType: input.type,
    verificationTier: VerificationTier[verificationTier],
    metadataHash: Buffer.from(metadataHash).toString("hex"),
    rewardAmount,
    txSignature: txSig,
    createdAt: new Date().toISOString(),
  });

  await maybeClaimMilestoneBadge(wallet, input.appUserId, eventType);

  return {
    txSignature: txSig,
    contributionId: Buffer.from(contributionId).toString("hex"),
    rewardAmount,
    verificationTier: VerificationTier[verificationTier],
  };
}

export async function openRelayJob(input: {
  appUserId: string;
  signedMessageBase64: string;
  signatureBase64: string;
  jobIdHex: string;
  destinationHashHex: string;
  payloadHashHex: string;
  expiryTs: number;
  rewardAmount: number;
  nonce: number;
}) {
  const user = db.prepare("SELECT app_user_id, wallet_public_key FROM users WHERE app_user_id = ?").get(input.appUserId) as
    | { app_user_id: string; wallet_public_key: string }
    | undefined;
  if (!user) {
    throw new Error(`Unknown app user: ${input.appUserId}`);
  }

  return createRelayJobForSender({
    auditAppUserId: input.appUserId,
    senderAppUserId: user.app_user_id,
    senderWalletPublicKey: user.wallet_public_key,
    signedMessageBase64: input.signedMessageBase64,
    signatureBase64: input.signatureBase64,
    jobIdHex: input.jobIdHex,
    destinationHashHex: input.destinationHashHex,
    payloadHashHex: input.payloadHashHex,
    expiryTs: input.expiryTs,
    rewardAmount: input.rewardAmount,
    nonce: input.nonce,
  });
}

export async function openRelayJobForSenderWallet(input: {
  auditAppUserId: string;
  senderWalletPublicKey: string;
  signedMessageBase64: string;
  signatureBase64: string;
  jobIdHex: string;
  destinationHashHex: string;
  payloadHashHex: string;
  expiryTs: number;
  rewardAmount: number;
  nonce: number;
}) {
  const user = db.prepare("SELECT app_user_id, wallet_public_key FROM users WHERE wallet_public_key = ?").get(input.senderWalletPublicKey) as
    | { app_user_id: string; wallet_public_key: string }
    | undefined;
  if (!user) {
    throw new Error(`Unknown sender wallet: ${input.senderWalletPublicKey}`);
  }

  return createRelayJobForSender({
    auditAppUserId: input.auditAppUserId,
    senderAppUserId: user.app_user_id,
    senderWalletPublicKey: user.wallet_public_key,
    signedMessageBase64: input.signedMessageBase64,
    signatureBase64: input.signatureBase64,
    jobIdHex: input.jobIdHex,
    destinationHashHex: input.destinationHashHex,
    payloadHashHex: input.payloadHashHex,
    expiryTs: input.expiryTs,
    rewardAmount: input.rewardAmount,
    nonce: input.nonce,
  });
}

async function createRelayJobForSender(input: {
  auditAppUserId: string;
  senderAppUserId: string;
  senderWalletPublicKey: string;
  signedMessageBase64: string;
  signatureBase64: string;
  jobIdHex: string;
  destinationHashHex: string;
  payloadHashHex: string;
  expiryTs: number;
  rewardAmount: number;
  nonce: number;
}) {
  const jobId = Buffer.from(input.jobIdHex, "hex");
  const wallet = new PublicKey(input.senderWalletPublicKey);
  const profilePda = userProfilePda(wallet);
  const relayPda = relayJobPda(jobId);
  const signedMessage = Buffer.from(input.signedMessageBase64, "base64");
  const expected = relayIntentMessage({
    jobId,
    senderWallet: wallet.toBase58(),
    destinationHash: Buffer.from(input.destinationHashHex, "hex"),
    payloadHash: Buffer.from(input.payloadHashHex, "hex"),
    expiryTs: input.expiryTs,
    rewardAmount: input.rewardAmount,
    nonce: input.nonce,
  });

  if (!expected.equals(signedMessage)) {
    throw new Error("Signed relay payload does not match canonical encoding");
  }

  const programIx = await program.methods
    .createRelayJobFromIntent({
      jobId: Array.from(jobId),
      destinationHash: Array.from(Buffer.from(input.destinationHashHex, "hex")),
      payloadHash: Array.from(Buffer.from(input.payloadHashHex, "hex")),
      expiryTs: new BN(input.expiryTs),
      rewardAmount: new BN(input.rewardAmount),
      nonce: new BN(input.nonce),
      signedMessage: Buffer.from(signedMessage),
    })
    .accounts({
      payer: sponsor.publicKey,
      config: configPda,
      userProfile: profilePda,
      userWallet: wallet,
      relayJob: relayPda,
      instructions: anchor.web3.SYSVAR_INSTRUCTIONS_PUBKEY,
      systemProgram: SystemProgram.programId,
    })
    .instruction();

  const tx = new Transaction().add(
    Ed25519Program.createInstructionWithPublicKey({
      publicKey: wallet.toBytes(),
      message: signedMessage,
      signature: Buffer.from(input.signatureBase64, "base64"),
    }),
    programIx,
  );
  tx.feePayer = sponsor.publicKey;

  const signature = await anchor.web3.sendAndConfirmTransaction(connection, tx, [sponsor]);
  audit(input.auditAppUserId, wallet.toBase58(), "create_relay_job_from_intent", signature, input);

  db.prepare(`
    INSERT INTO relay_jobs
      (job_id, sender_app_user_id, sender_wallet, destination_hash, payload_hash, expiry_ts, status, open_tx_signature, updated_at)
    VALUES (@jobId, @senderAppUserId, @senderWallet, @destinationHash, @payloadHash, @expiryTs, 'open', @txSignature, @updatedAt)
    ON CONFLICT(job_id) DO UPDATE SET
      open_tx_signature = excluded.open_tx_signature,
      status = excluded.status,
      updated_at = excluded.updated_at
  `).run({
    jobId: input.jobIdHex,
    senderAppUserId: input.senderAppUserId,
    senderWallet: wallet.toBase58(),
    destinationHash: input.destinationHashHex,
    payloadHash: input.payloadHashHex,
    expiryTs: input.expiryTs,
    txSignature: signature,
    updatedAt: new Date().toISOString(),
  });

  return { txSignature: signature, status: "open" };
}

export async function fulfillRelayJob(input: {
  appUserId: string;
  jobIdHex: string;
  proofRef: string;
}) {
  const user = db.prepare("SELECT wallet_public_key FROM users WHERE app_user_id = ?").get(input.appUserId) as
    | { wallet_public_key: string }
    | undefined;
  if (!user) {
    throw new Error(`Unknown app user: ${input.appUserId}`);
  }

  const wallet = new PublicKey(user.wallet_public_key);
  const jobId = Buffer.from(input.jobIdHex, "hex");
  const relayPda = relayJobPda(jobId);
  const profilePda = userProfilePda(wallet);
  const cfg = await fetchConfigAccount();
  const karmaMint = new PublicKey(String(cfg.karmaMint));
  const karmaAta = getAssociatedTokenAddressSync(
    karmaMint,
    wallet,
    false,
    TOKEN_2022_PROGRAM_ID,
    ASSOCIATED_TOKEN_PROGRAM_ID,
  );
  const proofHash = sha256Bytes(input.proofRef);
  const receiptId = sha256Bytes(`relay:${input.jobIdHex}`);
  const rewardReceipt = contributionReceiptPda(receiptId);

  const txSig = await program.methods
    .fulfillRelayJob({
      receiptId: Array.from(receiptId),
      proofHash: Array.from(proofHash),
      verificationTier: anchorVerificationTier(VerificationTier.Tier1AutoVerified),
    })
    .accounts({
      payer: sponsor.publicKey,
      attestor: attestor.publicKey,
      config: configPda,
      relayJob: relayPda,
      rewardReceipt,
      fulfillerProfile: profilePda,
      fulfillerWallet: wallet,
      karmaMint,
      fulfillerKarmaAta: karmaAta,
      tokenProgram: TOKEN_2022_PROGRAM_ID,
      associatedTokenProgram: ASSOCIATED_TOKEN_PROGRAM_ID,
      systemProgram: SystemProgram.programId,
    })
    .signers([sponsor, attestor])
    .rpc();

  audit(input.appUserId, wallet.toBase58(), "fulfill_relay_job", txSig, input);

  db.prepare(`
    INSERT INTO relay_jobs
      (job_id, sender_app_user_id, sender_wallet, destination_hash, payload_hash, expiry_ts, status, fulfill_tx_signature, fulfiller_app_user_id, updated_at)
    VALUES (@jobId, '', '', '', '', 0, 'fulfilled', @txSignature, @appUserId, @updatedAt)
    ON CONFLICT(job_id) DO UPDATE SET
      status = 'fulfilled',
      fulfill_tx_signature = excluded.fulfill_tx_signature,
      fulfiller_app_user_id = excluded.fulfiller_app_user_id,
      updated_at = excluded.updated_at
  `).run({
    jobId: input.jobIdHex,
    txSignature: txSig,
    appUserId: input.appUserId,
    updatedAt: new Date().toISOString(),
  });

  await maybeClaimBadge(wallet, input.appUserId, BadgeCode.RelayRanger);
  return { txSignature: txSig, rewardAmount: Number(cfg.rewardRelay) };
}

export async function prepareTip(appUserId: string, recipientWallet: string, amount: number) {
  const user = db.prepare("SELECT wallet_public_key FROM users WHERE app_user_id = ?").get(appUserId) as
    | { wallet_public_key: string }
    | undefined;
  if (!user) {
    throw new Error(`Unknown app user: ${appUserId}`);
  }

  const tipId = sha256Bytes(`${appUserId}:${recipientWallet}:${amount}:${Date.now()}:${crypto.randomUUID()}`);
  const nonce = Date.now();
  const payload = tipIntentMessage({
    tipId,
    senderWallet: user.wallet_public_key,
    recipientWallet,
    amount,
    nonce,
  });

  return {
    tipIdHex: Buffer.from(tipId).toString("hex"),
    nonce,
    amount,
    senderWallet: user.wallet_public_key,
    recipientWallet,
    signedMessageBase64: payload.toString("base64"),
  };
}

export async function submitTip(input: {
  appUserId: string;
  recipientWallet: string;
  amount: number;
  nonce: number;
  tipIdHex: string;
  signedMessageBase64: string;
  signatureBase64: string;
}) {
  const user = db.prepare("SELECT wallet_public_key FROM users WHERE app_user_id = ?").get(input.appUserId) as
    | { wallet_public_key: string }
    | undefined;
  if (!user) {
    throw new Error(`Unknown app user: ${input.appUserId}`);
  }

  const cfg = await fetchConfigAccount();
  const karmaMint = new PublicKey(String(cfg.karmaMint));
  const senderWallet = new PublicKey(user.wallet_public_key);
  const recipient = new PublicKey(input.recipientWallet);
  const senderProfile = userProfilePda(senderWallet);
  const recipientProfile = userProfilePda(recipient);
  const senderKarmaAta = getAssociatedTokenAddressSync(
    karmaMint,
    senderWallet,
    false,
    TOKEN_2022_PROGRAM_ID,
    ASSOCIATED_TOKEN_PROGRAM_ID,
  );
  const recipientKarmaAta = getAssociatedTokenAddressSync(
    karmaMint,
    recipient,
    false,
    TOKEN_2022_PROGRAM_ID,
    ASSOCIATED_TOKEN_PROGRAM_ID,
  );
  const tipId = Buffer.from(input.tipIdHex, "hex");
  const tipReceipt = tipReceiptPda(tipId);
  const signedMessage = Buffer.from(input.signedMessageBase64, "base64");
  const expected = tipIntentMessage({
    tipId,
    senderWallet: senderWallet.toBase58(),
    recipientWallet: recipient.toBase58(),
    amount: input.amount,
    nonce: input.nonce,
  });
  if (!expected.equals(signedMessage)) {
    throw new Error("Signed tip payload does not match canonical encoding");
  }

  const programIx = await program.methods
    .tipKarma({
      tipId: Array.from(tipId),
      amount: new BN(input.amount),
      nonce: new BN(input.nonce),
      signedMessage: Buffer.from(signedMessage),
    })
    .accounts({
      payer: sponsor.publicKey,
      config: configPda,
      senderProfile,
      recipientProfile,
      senderWallet,
      recipientWallet: recipient,
      tipReceipt,
      karmaMint,
      senderKarmaAta,
      recipientKarmaAta,
      instructions: anchor.web3.SYSVAR_INSTRUCTIONS_PUBKEY,
      tokenProgram: TOKEN_2022_PROGRAM_ID,
      associatedTokenProgram: ASSOCIATED_TOKEN_PROGRAM_ID,
      systemProgram: SystemProgram.programId,
    })
    .instruction();

  const tx = new Transaction().add(
    Ed25519Program.createInstructionWithPublicKey({
      publicKey: senderWallet.toBytes(),
      message: signedMessage,
      signature: Buffer.from(input.signatureBase64, "base64"),
    }),
    programIx,
  );
  tx.feePayer = sponsor.publicKey;

  const txSig = await anchor.web3.sendAndConfirmTransaction(connection, tx, [sponsor]);
  audit(input.appUserId, senderWallet.toBase58(), "tip_karma", txSig, input);
  return { txSignature: txSig };
}

export async function fetchRelayJob(jobIdHex: string) {
  const row = db.prepare("SELECT * FROM relay_jobs WHERE job_id = ?").get(jobIdHex) as AnyRecord | undefined;
  if (row) return row;
  const pda = relayJobPda(Buffer.from(jobIdHex, "hex"));
  return program.account.relayJob.fetchNullable(pda);
}

async function maybeClaimMilestoneBadge(wallet: PublicKey, appUserId: string, eventType: EventType) {
  await maybeClaimBadge(wallet, appUserId, BadgeCode.TrailScout);
  if (eventType === EventType.Species) {
    await maybeClaimBadge(wallet, appUserId, BadgeCode.SpeciesSpotter);
  }
  if (eventType === EventType.Water) {
    await maybeClaimBadge(wallet, appUserId, BadgeCode.WaterGuardian);
  }
  if (eventType === EventType.Hazard) {
    await maybeClaimBadge(wallet, appUserId, BadgeCode.HazardHerald);
  }
}

async function maybeClaimBadge(wallet: PublicKey, appUserId: string, badgeCode: BadgeCode) {
  const cfg = await fetchConfigAccount();
  const badgeMintKey = badgeMintForCode(cfg, badgeCode);
  const badgeMint = new PublicKey(String(badgeMintKey));
  const badgeAta = getAssociatedTokenAddressSync(
    badgeMint,
    wallet,
    false,
    TOKEN_2022_PROGRAM_ID,
    ASSOCIATED_TOKEN_PROGRAM_ID,
  );
  const claimPda = badgeClaimPda(wallet, badgeCode);
  const existing = await connection.getAccountInfo(claimPda);
  if (existing) return;

  try {
    const txSig = await program.methods
      .claimBadge({
        badgeCode: anchorBadgeCode(badgeCode),
      })
      .accounts({
        payer: sponsor.publicKey,
        config: configPda,
        userProfile: userProfilePda(wallet),
        userWallet: wallet,
        badgeClaim: claimPda,
        badgeMint,
        userBadgeAta: badgeAta,
        tokenProgram: TOKEN_2022_PROGRAM_ID,
        associatedTokenProgram: ASSOCIATED_TOKEN_PROGRAM_ID,
        systemProgram: SystemProgram.programId,
      })
      .signers([sponsor])
      .rpc();

    audit(appUserId, wallet.toBase58(), "claim_badge", txSig, {
      badgeCode,
      badgeMint: badgeMint.toBase58(),
    });
  } catch {
    // Badge may not be eligible yet. That is expected on most claim attempts.
  }
}

function badgeMintForCode(cfg: AnyRecord, badgeCode: BadgeCode) {
  switch (badgeCode) {
    case BadgeCode.TrailScout:
      return cfg.trailScoutMint;
    case BadgeCode.RelayRanger:
      return cfg.relayRangerMint;
    case BadgeCode.SpeciesSpotter:
      return cfg.speciesSpotterMint;
    case BadgeCode.WaterGuardian:
      return cfg.waterGuardianMint;
    case BadgeCode.HazardHerald:
      return cfg.hazardHeraldMint;
  }
}

function anchorEventType(eventType: EventType): AnchorEnumVariant {
  switch (eventType) {
    case EventType.Hazard:
      return { hazard: {} };
    case EventType.Water:
      return { water: {} };
    case EventType.Species:
      return { species: {} };
    case EventType.Relay:
      return { relay: {} };
    case EventType.Verification:
      return { verification: {} };
  }
}

function anchorVerificationTier(tier: VerificationTier): AnchorEnumVariant {
  switch (tier) {
    case VerificationTier.Tier1AutoVerified:
      return { tier1AutoVerified: {} };
    case VerificationTier.Tier2ModelAssisted:
      return { tier2ModelAssisted: {} };
    case VerificationTier.Tier3CommunityVerified:
      return { tier3CommunityVerified: {} };
  }
}

function anchorBadgeCode(badgeCode: BadgeCode): AnchorEnumVariant {
  switch (badgeCode) {
    case BadgeCode.TrailScout:
      return { trailScout: {} };
    case BadgeCode.RelayRanger:
      return { relayRanger: {} };
    case BadgeCode.SpeciesSpotter:
      return { speciesSpotter: {} };
    case BadgeCode.WaterGuardian:
      return { waterGuardian: {} };
    case BadgeCode.HazardHerald:
      return { hazardHerald: {} };
  }
}

function audit(appUserId: string, walletPublicKey: string, operation: string, txSignature: string, payload: unknown) {
  db.prepare(`
    INSERT INTO tx_audit (app_user_id, wallet_public_key, operation, tx_signature, payload_json, created_at)
    VALUES (?, ?, ?, ?, ?, ?)
  `).run(appUserId, walletPublicKey, operation, txSignature, JSON.stringify(payload), new Date().toISOString());
}

function parseJson(value: string): Record<string, unknown> {
  try {
    return JSON.parse(value) as Record<string, unknown>;
  } catch {
    return {};
  }
}

function humanizeTier(value: string): string {
  return value
    .replace(/tier/i, "Tier ")
    .replace(/([a-z])([A-Z])/g, "$1 $2")
    .trim();
}

function capitalize(value: string): string {
  return value.charAt(0).toUpperCase() + value.slice(1);
}

function shortWallet(value: string): string {
  if (!value || value.length < 10) return value || "unknown wallet";
  return `${value.slice(0, 4)}...${value.slice(-4)}`;
}

function badgeLabelFromPayload(payload: Record<string, unknown>): string {
  const badgeCode = String(payload.badgeCode ?? "");
  switch (badgeCode) {
    case String(BadgeCode.TrailScout):
    case "trail_scout":
    case "0":
      return "Trail Scout";
    case String(BadgeCode.RelayRanger):
    case "relay_ranger":
    case "1":
      return "Relay Ranger";
    case String(BadgeCode.SpeciesSpotter):
    case "species_spotter":
    case "2":
      return "Species Spotter";
    case String(BadgeCode.WaterGuardian):
    case "water_guardian":
    case "3":
      return "Water Guardian";
    case String(BadgeCode.HazardHerald):
    case "hazard_herald":
    case "4":
      return "Hazard Herald";
    default:
      return "Collectible";
  }
}
