import fs from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import multer from "multer";
import type { Request, Response } from "express";
import { z } from "zod";
import { db } from "./db.js";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const repoRoot = path.resolve(__dirname, "..", "..");
const biodiversityDataDir = path.join(repoRoot, "backend", "data", "biodiversity");
const audioDir = path.join(biodiversityDataDir, "audio");
const photoDir = path.join(biodiversityDataDir, "photos");

export const biodiversityUpload = multer({
  storage: multer.memoryStorage(),
  limits: {
    fileSize: 20 * 1024 * 1024,
  },
});

const audioSyncSchema = z.object({
  lat: z.coerce.number().optional(),
  lon: z.coerce.number().optional(),
  location_accuracy_meters: z.coerce.number().optional(),
  location_source: z.string().min(1),
  timestamp: z.string().min(1),
  observation_id: z.string().min(1),
  user_id: z.string().min(1),
  observer_display_name: z.string().optional(),
  observer_wallet_public_key: z.string().optional(),
  final_label: z.string().min(1),
  final_taxonomic_level: z.string().min(1),
  confidence: z.coerce.number(),
  confidence_band: z.string().min(1),
  explanation: z.string().min(1),
  safe_for_rewarding: z.union([z.boolean(), z.string(), z.number()]),
  verification_status: z.string().min(1),
  verification_tx_signature: z.string().optional(),
  verified_at: z.string().optional(),
  collectible_status: z.string().min(1),
  collectible_id: z.string().optional(),
  collectible_name: z.string().optional(),
  collectible_image_uri: z.string().optional(),
  data_share_status: z.string().min(1),
  shared_with_org_at: z.string().optional(),
  top_k_json: z.string().min(2),
  model_metadata_json: z.string().min(2),
  classification_source: z.string().min(1),
  local_model_version: z.string().min(1),
});

const photoLinkSchema = z.object({
  observation_id: z.string().min(1),
  photo_uri: z.string().optional(),
});

type BiodiversityEventRow = {
  observation_id: string;
  timestamp: string;
  lat: number | null;
  lon: number | null;
  final_label: string;
  final_taxonomic_level: string;
  confidence: number;
  confidence_band: string;
  explanation: string;
  verification_status: string;
  photo_uri: string | null;
};

function coerceOptionalString(value: unknown): string | undefined {
  if (typeof value !== "string") return undefined;
  const trimmed = value.trim();
  return trimmed.length > 0 ? trimmed : undefined;
}

function coerceBoolean(value: string | number | boolean): boolean {
  if (typeof value === "boolean") return value;
  if (typeof value === "number") return value !== 0;
  const normalized = value.trim().toLowerCase();
  return normalized === "true" || normalized === "1" || normalized === "yes";
}

async function ensureBiodiversityDirs() {
  await Promise.all([
    fs.mkdir(audioDir, { recursive: true }),
    fs.mkdir(photoDir, { recursive: true }),
  ]);
}

async function saveUploadedFile(buffer: Buffer, outputPath: string) {
  await fs.mkdir(path.dirname(outputPath), { recursive: true });
  await fs.writeFile(outputPath, buffer);
}

async function mirrorBiodiversityEvent(row: BiodiversityEventRow) {
  const host = process.env.DATABRICKS_HOST?.replace(/\/+$/, "") ?? "";
  const token = process.env.DATABRICKS_TOKEN ?? "";
  const warehouseId = process.env.DATABRICKS_WAREHOUSE ?? "";
  if (!host || !token || !warehouseId) {
    return { enabled: false, status: "skipped" };
  }

  const escaped = (value: string) => value.replaceAll("'", "''");
  const sql = `
    INSERT INTO workspace.trailkarma.biodiversity_events
    (observation_id, timestamp, lat, lon, final_label, taxonomic_level, confidence, confidence_band, explanation, verification_status, photo_uri)
    VALUES (
      '${escaped(row.observation_id)}',
      '${escaped(row.timestamp)}',
      ${row.lat ?? "NULL"},
      ${row.lon ?? "NULL"},
      '${escaped(row.final_label)}',
      '${escaped(row.final_taxonomic_level)}',
      ${row.confidence},
      '${escaped(row.confidence_band)}',
      '${escaped(row.explanation)}',
      '${escaped(row.verification_status)}',
      ${row.photo_uri ? `'${escaped(row.photo_uri)}'` : "NULL"}
    )
  `.trim();

  const response = await fetch(`${host}/api/2.0/sql/statements`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      warehouse_id: warehouseId,
      statement: sql,
      wait_timeout: "30s",
    }),
  });

  if (!response.ok) {
    const body = await response.text();
    throw new Error(`Databricks mirror failed (${response.status}): ${body}`);
  }

  const body = (await response.json()) as { status?: { state?: string } };
  return { enabled: true, status: body.status?.state ?? "UNKNOWN" };
}

function loadBiodiversityEvent(observationId: string) {
  return db
    .prepare(
      `
        SELECT
          observation_id,
          timestamp,
          lat,
          lon,
          final_label,
          final_taxonomic_level,
          confidence,
          confidence_band,
          explanation,
          verification_status,
          photo_uri
        FROM biodiversity_events
        WHERE observation_id = ?
      `,
    )
    .get(observationId) as BiodiversityEventRow | undefined;
}

export async function handleAudioSync(req: Request, res: Response) {
  try {
    const parsed = audioSyncSchema.parse({
      ...req.body,
      observer_display_name: coerceOptionalString(req.body.observer_display_name),
      observer_wallet_public_key: coerceOptionalString(req.body.observer_wallet_public_key),
      verification_tx_signature: coerceOptionalString(req.body.verification_tx_signature),
      verified_at: coerceOptionalString(req.body.verified_at),
      collectible_id: coerceOptionalString(req.body.collectible_id),
      collectible_name: coerceOptionalString(req.body.collectible_name),
      collectible_image_uri: coerceOptionalString(req.body.collectible_image_uri),
      shared_with_org_at: coerceOptionalString(req.body.shared_with_org_at),
    });

    if (!req.file?.buffer?.length) {
      throw new Error("audio payload is empty");
    }

    JSON.parse(parsed.top_k_json);
    JSON.parse(parsed.model_metadata_json);

    await ensureBiodiversityDirs();
    const suffix = path.extname(req.file.originalname || "") || ".wav";
    const audioPath = path.join(audioDir, `${parsed.observation_id}${suffix}`);
    await saveUploadedFile(req.file.buffer, audioPath);

    const now = new Date().toISOString();
    db.prepare(
      `
        INSERT INTO biodiversity_events (
          observation_id,
          user_id,
          observer_display_name,
          observer_wallet_public_key,
          audio_path,
          timestamp,
          lat,
          lon,
          location_accuracy_meters,
          location_source,
          final_label,
          final_taxonomic_level,
          confidence,
          confidence_band,
          explanation,
          safe_for_rewarding,
          verification_status,
          verification_tx_signature,
          verified_at,
          collectible_status,
          collectible_id,
          collectible_name,
          collectible_image_uri,
          data_share_status,
          shared_with_org_at,
          classification_source,
          local_model_version,
          top_k_json,
          model_metadata_json,
          created_at,
          updated_at
        ) VALUES (
          @observation_id,
          @user_id,
          @observer_display_name,
          @observer_wallet_public_key,
          @audio_path,
          @timestamp,
          @lat,
          @lon,
          @location_accuracy_meters,
          @location_source,
          @final_label,
          @final_taxonomic_level,
          @confidence,
          @confidence_band,
          @explanation,
          @safe_for_rewarding,
          @verification_status,
          @verification_tx_signature,
          @verified_at,
          @collectible_status,
          @collectible_id,
          @collectible_name,
          @collectible_image_uri,
          @data_share_status,
          @shared_with_org_at,
          @classification_source,
          @local_model_version,
          @top_k_json,
          @model_metadata_json,
          @created_at,
          @updated_at
        )
        ON CONFLICT(observation_id) DO UPDATE SET
          user_id = excluded.user_id,
          observer_display_name = excluded.observer_display_name,
          observer_wallet_public_key = excluded.observer_wallet_public_key,
          audio_path = excluded.audio_path,
          timestamp = excluded.timestamp,
          lat = excluded.lat,
          lon = excluded.lon,
          location_accuracy_meters = excluded.location_accuracy_meters,
          location_source = excluded.location_source,
          final_label = excluded.final_label,
          final_taxonomic_level = excluded.final_taxonomic_level,
          confidence = excluded.confidence,
          confidence_band = excluded.confidence_band,
          explanation = excluded.explanation,
          safe_for_rewarding = excluded.safe_for_rewarding,
          verification_status = excluded.verification_status,
          verification_tx_signature = excluded.verification_tx_signature,
          verified_at = excluded.verified_at,
          collectible_status = excluded.collectible_status,
          collectible_id = excluded.collectible_id,
          collectible_name = excluded.collectible_name,
          collectible_image_uri = excluded.collectible_image_uri,
          data_share_status = excluded.data_share_status,
          shared_with_org_at = excluded.shared_with_org_at,
          classification_source = excluded.classification_source,
          local_model_version = excluded.local_model_version,
          top_k_json = excluded.top_k_json,
          model_metadata_json = excluded.model_metadata_json,
          updated_at = excluded.updated_at
      `,
    ).run({
      ...parsed,
      audio_path: audioPath,
      safe_for_rewarding: coerceBoolean(parsed.safe_for_rewarding) ? 1 : 0,
      created_at: now,
      updated_at: now,
    });

    let mirrorStatus: { enabled: boolean; status: string; error?: string } = {
      enabled: false,
      status: "skipped",
    };
    try {
      const row = loadBiodiversityEvent(parsed.observation_id);
      if (row) {
        mirrorStatus = await mirrorBiodiversityEvent(row);
      }
    } catch (error) {
      mirrorStatus = {
        enabled: true,
        status: "failed",
        error: error instanceof Error ? error.message : String(error),
      };
      console.error("Biodiversity mirror failed", error);
    }

    res.json({
      success: true,
      mirrorStatus,
    });
  } catch (error) {
    res.status(400).json({
      success: false,
      error: error instanceof Error ? error.message : String(error),
    });
  }
}

export async function handlePhotoLink(req: Request, res: Response) {
  try {
    const parsed = photoLinkSchema.parse({
      ...req.body,
      photo_uri: coerceOptionalString(req.body.photo_uri),
    });

    const existing = loadBiodiversityEvent(parsed.observation_id);
    if (!existing) {
      res.status(404).json({ success: false, error: "observation_id not found" });
      return;
    }

    await ensureBiodiversityDirs();
    let photoUri = parsed.photo_uri ?? null;
    if (req.file?.buffer?.length) {
      const suffix = path.extname(req.file.originalname || "") || ".jpg";
      const photoPath = path.join(photoDir, `${parsed.observation_id}${suffix}`);
      await saveUploadedFile(req.file.buffer, photoPath);
      photoUri = photoPath;
    }

    if (!photoUri) {
      res.status(400).json({ success: false, error: "photo or photo_uri is required" });
      return;
    }

    db.prepare(
      `
        UPDATE biodiversity_events
        SET photo_uri = ?, updated_at = ?
        WHERE observation_id = ?
      `,
    ).run(photoUri, new Date().toISOString(), parsed.observation_id);

    let mirrorStatus: { enabled: boolean; status: string; error?: string } = {
      enabled: false,
      status: "skipped",
    };
    try {
      const row = loadBiodiversityEvent(parsed.observation_id);
      if (row) {
        mirrorStatus = await mirrorBiodiversityEvent(row);
      }
    } catch (error) {
      mirrorStatus = {
        enabled: true,
        status: "failed",
        error: error instanceof Error ? error.message : String(error),
      };
      console.error("Biodiversity photo mirror failed", error);
    }

    res.json({ success: true, mirrorStatus });
  } catch (error) {
    res.status(400).json({
      success: false,
      error: error instanceof Error ? error.message : String(error),
    });
  }
}
