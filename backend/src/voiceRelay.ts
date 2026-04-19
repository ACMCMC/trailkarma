import crypto from "node:crypto";
import { config } from "./config.js";
import { db } from "./db.js";
import { fulfillRelayJob, openRelayJobForSenderWallet } from "./solana/client.js";
import { decryptRelayPayload } from "./solana/crypto.js";

type OpenVoiceRelayJobInput = {
  appUserId: string;
  senderWalletPublicKey: string;
  signedMessageBase64: string;
  signatureBase64: string;
  jobIdHex: string;
  destinationHashHex: string;
  payloadHashHex: string;
  expiryTs: number;
  rewardAmount: number;
  nonce: number;
  encryptedBlob: string; // The encrypted JSON containing recipient details and message
  recipientName?: string;
  recipientPhoneNumber?: string;
  messageBody?: string;
  contextJson?: string;
};

type VoiceRelayJobRow = {
  job_id: string;
  sender_app_user_id: string;
  carrier_app_user_id: string;
  sender_wallet: string;
  recipient_name: string;
  recipient_phone_number: string;
  message_body: string;
  context_summary: string;
  context_json: string;
  destination_hash: string;
  payload_hash: string;
  status: string;
  opened_tx_signature: string | null;
  fulfilled_tx_signature: string | null;
  call_sid: string | null;
  conversation_id: string | null;
  transcript_summary: string | null;
  reply_id: string | null;
  created_at: string;
  updated_at: string;
  last_checked_at: string | null;
};

type ConversationMessage = {
  role?: string;
  message?: string;
};

type ConversationResponse = {
  conversation_id: string;
  status: "initiated" | "in-progress" | "processing" | "done" | "failed";
  transcript?: ConversationMessage[];
  analysis?: {
    call_successful?: string;
    transcript_summary?: string;
  } | null;
};

const TRAILKARMA_AGENT_NAME = "TrailKarma Relay";
const TRAILKARMA_AGENT_PROMPT = `
You are TrailKarma Relay, an automated outbound voice relay for hikers.

Your job is operational and narrow:
1. Introduce yourself as TrailKarma Relay.
2. Deliver the hiker's message faithfully using the provided dynamic variables.
3. Mention location or callback details only if they were provided.
4. Ask the recipient if they want to leave a short reply for the hiker.
5. If they do, confirm the reply briefly and say it will be relayed when possible.
6. If they decline, acknowledge it and end politely.

Never invent facts. If asked questions you cannot answer, explain that you only have the relayed message and the attached trail context.

Dynamic variables:
- recipient_name: {{recipient_name}}
- sender_name: {{sender_name}}
- sender_real_name: {{sender_real_name}}
- callback_number: {{callback_number}}
- location_summary: {{location_summary}}
- message_body: {{message_body}}
- context_summary: {{context_summary}}
`.trim();

const DEFAULT_FIRST_MESSAGE =
  "Hello {{recipient_name}}, this is TrailKarma Relay with a message from {{sender_name}}.";
const VOICE_RELAY_DEDUP_WINDOW_MS = 2 * 60 * 1000;

export async function openVoiceRelayJob(input: OpenVoiceRelayJobInput) {
  const sender = db
    .prepare(`
      SELECT app_user_id, display_name, wallet_public_key, real_name, phone_number
      FROM users
      WHERE wallet_public_key = ?
    `)
    .get(input.senderWalletPublicKey) as
    | {
        app_user_id: string;
        display_name: string;
        wallet_public_key: string;
        real_name: string | null;
        phone_number: string | null;
      }
    | undefined;

  if (!sender) {
    throw new Error("The sender wallet has not been registered on the backend yet.");
  }

  const existing = getVoiceRelayJob(input.jobIdHex);
  if (existing) {
    const refreshed = await refreshVoiceRelayJob(existing.job_id);
    return toVoiceRelayResponse(refreshed ?? existing);
  }

  const decrypted = decodeRelayPayload(input);
  const recipientName = decrypted.recipientName.trim();
  const recipientPhoneNumber = decrypted.recipientPhoneNumber.trim();
  const messageBody = decrypted.messageBody.trim();
  const contextJson = decrypted.contextJson;
  const normalizedRecipientPhone = normalizePhone(recipientPhoneNumber);
  const duplicate = findRecentDuplicateVoiceRelay({
    senderAppUserId: sender.app_user_id,
    recipientPhoneNumber: normalizedRecipientPhone,
    messageBody,
  });
  if (duplicate) {
    const refreshed = await refreshVoiceRelayJob(duplicate.job_id);
    return toVoiceRelayResponse(refreshed ?? duplicate);
  }
  const summary = `Relay message from ${sender.display_name}: "${messageBody}"`;

  const bootstrap = await ensureVoiceBootstrap();
  const openResult = await openRelayJobForSenderWallet({
    auditAppUserId: input.appUserId,
    senderWalletPublicKey: input.senderWalletPublicKey,
    signedMessageBase64: input.signedMessageBase64,
    signatureBase64: input.signatureBase64,
    jobIdHex: input.jobIdHex,
    destinationHashHex: input.destinationHashHex,
    payloadHashHex: input.payloadHashHex,
    expiryTs: input.expiryTs,
    rewardAmount: input.rewardAmount,
    nonce: input.nonce,
  });

  const call = await initiateOutboundCall({
    agentId: bootstrap.agentId,
    phoneNumberId: bootstrap.phoneNumberId,
    recipientPhoneNumber: recipientPhoneNumber,
    dynamicVariables: buildDynamicVariables({
      recipientName: recipientName,
      senderName: sender.display_name,
      senderRealName: sender.real_name,
      callbackNumber: sender.phone_number,
      messageBody: messageBody,
      contextSummary: summary,
      contextJson: contextJson,
    }),
  });

  const now = new Date().toISOString();
  db.prepare(`
    INSERT INTO voice_relay_jobs (
      job_id,
      sender_app_user_id,
      carrier_app_user_id,
      sender_wallet,
      recipient_name,
      recipient_phone_number,
      message_body,
      context_summary,
      context_json,
      destination_hash,
      payload_hash,
      status,
      opened_tx_signature,
      fulfilled_tx_signature,
      call_sid,
      conversation_id,
      transcript_summary,
      reply_id,
      created_at,
      updated_at,
      last_checked_at
    ) VALUES (
      @jobId,
      @senderAppUserId,
      @carrierAppUserId,
      @senderWallet,
      @recipientName,
      @recipientPhoneNumber,
      @messageBody,
      @contextSummary,
      @contextJson,
      @destinationHash,
      @payloadHash,
      @status,
      @openedTxSignature,
      NULL,
      @callSid,
      @conversationId,
      NULL,
      NULL,
      @createdAt,
      @updatedAt,
      NULL
    )
  `).run({
    jobId: input.jobIdHex,
    senderAppUserId: sender.app_user_id,
    carrierAppUserId: input.appUserId,
    senderWallet: input.senderWalletPublicKey,
    recipientName,
    recipientPhoneNumber: normalizedRecipientPhone,
    messageBody,
    contextSummary: summary,
    contextJson,
    destinationHash: input.destinationHashHex,
    payloadHash: input.payloadHashHex,
    status: "calling",
    openedTxSignature: openResult.txSignature,
    callSid: call.callSid,
    conversationId: call.conversationId,
    createdAt: now,
    updatedAt: now,
  });

  const row = getVoiceRelayJob(input.jobIdHex);
  if (!row) {
    throw new Error("Failed to store the voice relay job.");
  }
  return toVoiceRelayResponse(row);
}

export async function listVoiceRelayJobs(appUserId: string) {
  const rows = db
    .prepare(`
      SELECT *
      FROM voice_relay_jobs
      WHERE sender_app_user_id = ? OR carrier_app_user_id = ?
      ORDER BY updated_at DESC
    `)
    .all(appUserId, appUserId) as VoiceRelayJobRow[];

  const items = [];
  for (const row of rows) {
    const refreshed = await refreshVoiceRelayJob(row.job_id);
    items.push(toVoiceRelayResponse(refreshed ?? row));
  }
  return { items };
}

export async function listRelayInbox(appUserId: string) {
  const rows = db
    .prepare(`
      SELECT *
      FROM voice_relay_inbox
      WHERE user_app_id = ?
      ORDER BY created_at DESC
    `)
    .all(appUserId) as Array<{
      reply_id: string;
      original_job_id: string;
      sender_label: string;
      sender_phone_number: string;
      message_summary: string;
      message_body: string;
      context_json: string;
      created_at: string;
      status: string;
    }>;

  return {
    items: rows.map((row) => ({
      replyId: row.reply_id,
      originalJobId: row.original_job_id,
      senderLabel: row.sender_label,
      senderPhoneNumber: row.sender_phone_number,
      messageSummary: row.message_summary,
      messageBody: row.message_body,
      contextJson: row.context_json,
      createdAt: row.created_at,
      status: row.status,
    })),
  };
}

export async function listPendingMeshRelayReplies(carrierAppUserId: string) {
  await refreshActiveVoiceRelayJobs();
  const rows = db
    .prepare(`
      SELECT *
      FROM voice_relay_inbox
      WHERE acknowledged = 0
      ORDER BY created_at ASC
      LIMIT 50
    `)
    .all() as Array<{
      reply_id: string;
      original_job_id: string;
      user_app_id: string;
      sender_label: string;
      sender_phone_number: string;
      message_summary: string;
      message_body: string;
      context_json: string;
      created_at: string;
      status: string;
    }>;

  return {
    items: rows.map((row) => ({
      replyId: row.reply_id,
      originalJobId: row.original_job_id,
      targetUserId: row.user_app_id,
      senderLabel: row.sender_label,
      senderPhoneNumber: row.sender_phone_number,
      messageSummary: row.message_summary,
      messageBody: row.message_body,
      contextJson: row.context_json,
      createdAt: row.created_at,
      status: row.status,
      carrierAppUserId,
    })),
  };
}

export async function acknowledgeRelayInbox(replyId: string) {
  db.prepare(`
    UPDATE voice_relay_inbox
    SET acknowledged = 1,
        status = 'delivered',
        updated_at = ?
    WHERE reply_id = ?
  `).run(new Date().toISOString(), replyId);

  return {
    txSignature: `ack:${replyId}`,
    status: "acknowledged",
  };
}

async function refreshVoiceRelayJob(jobId: string) {
  const row = getVoiceRelayJob(jobId);
  if (!row || !row.conversation_id) return row;
  if (row.status === "fulfilled" || row.status === "failed") return row;

  const conversation = await getConversation(row.conversation_id);
  if (!conversation) return row;

  const transcriptSummary = conversation.analysis?.transcript_summary ?? row.transcript_summary;
  let status = mapConversationStatus(conversation.status);
  let fulfilledTxSignature = row.fulfilled_tx_signature;
  let replyId = row.reply_id;

  if (conversation.status === "done") {
    const successful = conversation.analysis?.call_successful === "success" || hasMeaningfulUserResponse(conversation);
    if (successful && !fulfilledTxSignature) {
      const fulfilled = await fulfillRelayJob({
        appUserId: row.carrier_app_user_id,
        jobIdHex: row.job_id,
        proofRef: `conv:${row.conversation_id}`,
      });
      fulfilledTxSignature = fulfilled.txSignature;
    }

    const replyMessage = extractReply(conversation.transcript ?? []);
    if (replyMessage && !replyId) {
      replyId = createInboxReply(row, replyMessage, transcriptSummary);
    }
    status = successful ? "fulfilled" : "failed";
  } else if (conversation.status === "failed") {
    status = "failed";
  }

  const now = new Date().toISOString();
  db.prepare(`
    UPDATE voice_relay_jobs
    SET status = @status,
        fulfilled_tx_signature = @fulfilledTxSignature,
        transcript_summary = @transcriptSummary,
        reply_id = @replyId,
        updated_at = @updatedAt,
        last_checked_at = @lastCheckedAt
    WHERE job_id = @jobId
  `).run({
    status,
    fulfilledTxSignature,
    transcriptSummary,
    replyId,
    updatedAt: now,
    lastCheckedAt: now,
    jobId,
  });

  return getVoiceRelayJob(jobId);
}

async function refreshActiveVoiceRelayJobs() {
  const rows = db
    .prepare(`
      SELECT job_id
      FROM voice_relay_jobs
      WHERE status NOT IN ('fulfilled', 'failed')
        AND conversation_id IS NOT NULL
      ORDER BY updated_at ASC
      LIMIT 25
    `)
    .all() as Array<{ job_id: string }>;

  for (const row of rows) {
    await refreshVoiceRelayJob(row.job_id);
  }
}

async function ensureVoiceBootstrap() {
  if (!config.elevenLabsApiKey) {
    throw new Error("Missing ELEVENLABS_API_KEY.");
  }

  let agentId = config.elevenLabsAgentId ?? getSetting("voice_agent_id");
  if (!agentId) {
    const created = await elevenlabsRequest<{ agent_id: string }>("/v1/convai/agents/create", {
      method: "POST",
      body: JSON.stringify({
        name: TRAILKARMA_AGENT_NAME,
        conversation_config: {},
      }),
    });
    agentId = created.agent_id;
    setSetting("voice_agent_id", agentId);
  }

  await elevenlabsRequest(`/v1/convai/agents/${agentId}`, {
    method: "PATCH",
    body: JSON.stringify({
      name: TRAILKARMA_AGENT_NAME,
      conversation_config: {
        asr: {
          user_input_audio_format: "ulaw_8000",
        },
        tts: {
          agent_output_audio_format: "ulaw_8000",
        },
        agent: {
          first_message: DEFAULT_FIRST_MESSAGE,
          language: "en",
          prompt: {
            prompt: TRAILKARMA_AGENT_PROMPT,
            llm: "gemini-2.5-flash",
          },
        },
      },
    }),
  });

  let phoneNumberId = config.elevenLabsPhoneNumberId ?? getSetting("voice_phone_number_id");
  const phoneNumbers = await elevenlabsRequest<Array<{
    phone_number_id: string;
    phone_number: string;
  }>>("/v1/convai/phone-numbers");
  const configuredPhoneNumber = normalizePhone(config.twilioPhoneNumber ?? "");

  const matchingPhone = phoneNumbers.find((phone) =>
    configuredPhoneNumber
      ? normalizePhone(phone.phone_number) === configuredPhoneNumber
      : phone.phone_number_id === phoneNumberId,
  );
  if (!phoneNumberId && matchingPhone) {
    phoneNumberId = matchingPhone.phone_number_id;
  }

  if (!phoneNumberId && configuredPhoneNumber && config.twilioAccountSid && config.twilioAuthToken) {
    const created = await elevenlabsRequest<{ phone_number_id: string }>("/v1/convai/phone-numbers", {
      method: "POST",
      body: JSON.stringify({
        label: "TrailKarma Relay Number",
        phone_number: configuredPhoneNumber,
        sid: config.twilioAccountSid,
        token: config.twilioAuthToken,
      }),
    });
    phoneNumberId = created.phone_number_id;
  }

  if (!phoneNumberId) {
    throw new Error(
      "No ElevenLabs phone number is available. Set ELEVENLABS_PHONE_NUMBER_ID or import your Twilio number first.",
    );
  }

  await elevenlabsRequest(`/v1/convai/phone-numbers/${phoneNumberId}`, {
    method: "PATCH",
    body: JSON.stringify({
      agent_id: agentId,
      label: "TrailKarma Relay Number",
    }),
  });

  setSetting("voice_agent_id", agentId);
  setSetting("voice_phone_number_id", phoneNumberId);
  return { agentId, phoneNumberId };
}

async function initiateOutboundCall(input: {
  agentId: string;
  phoneNumberId: string;
  recipientPhoneNumber: string;
  dynamicVariables: Record<string, string>;
}) {
  const response = await elevenlabsRequest<{
    conversation_id?: string | null;
    conversationId?: string | null;
    callSid?: string | null;
    call_sid?: string | null;
  }>("/v1/convai/twilio/outbound-call", {
    method: "POST",
    body: JSON.stringify({
      agent_id: input.agentId,
      agent_phone_number_id: input.phoneNumberId,
      to_number: normalizePhone(input.recipientPhoneNumber),
      conversation_initiation_client_data: {
        dynamic_variables: input.dynamicVariables,
      },
    }),
  });

  return {
    conversationId: response.conversation_id ?? response.conversationId ?? null,
    callSid: response.callSid ?? response.call_sid ?? null,
  };
}

async function getConversation(conversationId: string) {
  return elevenlabsRequest<ConversationResponse>(`/v1/convai/conversations/${conversationId}`);
}

async function elevenlabsRequest<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`https://api.elevenlabs.io${path}`, {
    ...init,
    headers: {
      "Content-Type": "application/json",
      "xi-api-key": config.elevenLabsApiKey ?? "",
      ...(init?.headers ?? {}),
    },
  });

  if (!response.ok) {
    const body = await response.text();
    throw new Error(`ElevenLabs request failed (${response.status}): ${body}`);
  }

  return (await response.json()) as T;
}

function buildDynamicVariables(input: {
  recipientName: string;
  senderName: string;
  senderRealName: string | null;
  callbackNumber: string | null;
  messageBody: string;
  contextSummary: string;
  contextJson: string;
}) {
  const context = safeJsonParse<Record<string, unknown>>(input.contextJson) ?? {};
  const location = safeJsonParse<{ lat?: number; lng?: number }>(context.location);
  const locationSummary =
    location && typeof location.lat === "number" && typeof location.lng === "number"
      ? `Last known coordinates ${location.lat.toFixed(5)}, ${location.lng.toFixed(5)}.`
      : "No location was attached.";

  return {
    recipient_name: blankOr(input.recipientName, "there"),
    sender_name: input.senderName,
    sender_real_name: input.senderRealName ?? "",
    callback_number: input.callbackNumber ?? "",
    location_summary: locationSummary,
    message_body: input.messageBody,
    context_summary: input.contextSummary,
  };
}

function extractReply(transcript: ConversationMessage[]) {
  const userMessages = transcript
    .filter((item) => item.role === "user")
    .map((item) => item.message?.trim() ?? "")
    .filter((message) => message.length >= 6);

  if (userMessages.length === 0) return null;
  const joined = userMessages.slice(-3).join(" ");
  if (/(no reply|no thanks|that's all|that is all|nothing else)/i.test(joined) && joined.length < 80) {
    return null;
  }
  return joined.slice(0, 1200);
}

function hasMeaningfulUserResponse(conversation: ConversationResponse) {
  return (conversation.transcript ?? []).some(
    (item) => item.role === "user" && (item.message?.trim().length ?? 0) >= 6,
  );
}

function createInboxReply(row: VoiceRelayJobRow, replyMessage: string, transcriptSummary: string | null) {
  const replyId = crypto.createHash("sha256").update(`${row.job_id}:${replyMessage}`).digest("hex");
  const now = new Date().toISOString();
  db.prepare(`
    INSERT INTO voice_relay_inbox (
      reply_id,
      original_job_id,
      user_app_id,
      sender_label,
      sender_phone_number,
      message_summary,
      message_body,
      context_json,
      status,
      acknowledged,
      conversation_id,
      created_at,
      updated_at
    ) VALUES (
      @replyId,
      @originalJobId,
      @userAppId,
      @senderLabel,
      @senderPhoneNumber,
      @messageSummary,
      @messageBody,
      @contextJson,
      'pending',
      0,
      @conversationId,
      @createdAt,
      @updatedAt
    )
    ON CONFLICT(reply_id) DO NOTHING
  `).run({
    replyId,
    originalJobId: row.job_id,
    userAppId: row.sender_app_user_id,
    senderLabel: blankOr(row.recipient_name, "Relay recipient"),
    senderPhoneNumber: row.recipient_phone_number,
    messageSummary: blankOr(transcriptSummary ?? "", "A relay recipient left a reply.").slice(0, 220),
    messageBody: replyMessage,
    contextJson: row.context_json,
    conversationId: row.conversation_id,
    createdAt: now,
    updatedAt: now,
  });
  return replyId;
}

function mapConversationStatus(status: ConversationResponse["status"]) {
  switch (status) {
    case "initiated":
      return "calling";
    case "in-progress":
      return "in_progress";
    case "processing":
      return "processing";
    case "done":
      return "processed";
    case "failed":
      return "failed";
  }
}

function getVoiceRelayJob(jobId: string) {
  return db.prepare("SELECT * FROM voice_relay_jobs WHERE job_id = ?").get(jobId) as VoiceRelayJobRow | undefined;
}

function findRecentDuplicateVoiceRelay(input: {
  senderAppUserId: string;
  recipientPhoneNumber: string;
  messageBody: string;
}) {
  const createdAfter = new Date(Date.now() - VOICE_RELAY_DEDUP_WINDOW_MS).toISOString();
  return db
    .prepare(`
      SELECT *
      FROM voice_relay_jobs
      WHERE sender_app_user_id = @senderAppUserId
        AND recipient_phone_number = @recipientPhoneNumber
        AND message_body = @messageBody
        AND status NOT IN ('fulfilled', 'failed')
        AND updated_at >= @createdAfter
      ORDER BY updated_at DESC
      LIMIT 1
    `)
    .get({
      senderAppUserId: input.senderAppUserId,
      recipientPhoneNumber: input.recipientPhoneNumber,
      messageBody: input.messageBody,
      createdAfter,
    }) as VoiceRelayJobRow | undefined;
}

function toVoiceRelayResponse(row: VoiceRelayJobRow) {
  return {
    jobId: row.job_id,
    status: row.status,
    openedTxSignature: row.opened_tx_signature,
    fulfilledTxSignature: row.fulfilled_tx_signature,
    callSid: row.call_sid,
    conversationId: row.conversation_id,
    transcriptSummary: row.transcript_summary,
    replyJobId: row.reply_id,
  };
}

function getSetting(key: string) {
  const row = db.prepare("SELECT value FROM app_settings WHERE key = ?").get(key) as { value: string } | undefined;
  return row?.value ?? null;
}

function setSetting(key: string, value: string) {
  db.prepare(`
    INSERT INTO app_settings (key, value, updated_at)
    VALUES (?, ?, ?)
    ON CONFLICT(key) DO UPDATE SET
      value = excluded.value,
      updated_at = excluded.updated_at
  `).run(key, value, new Date().toISOString());
}

function normalizePhone(value: string) {
  const trimmed = value.trim();
  if (!trimmed) return "";
  if (trimmed.startsWith("+")) {
    return `+${trimmed.slice(1).replace(/\D/g, "")}`;
  }
  return `+${trimmed.replace(/\D/g, "")}`;
}

function decodeRelayPayload(input: OpenVoiceRelayJobInput) {
  if (config.relayEncryptionPrivateKey) {
    try {
      return JSON.parse(decryptRelayPayload(input.encryptedBlob, config.relayEncryptionPrivateKey)) as {
        recipientName: string;
        recipientPhoneNumber: string;
        messageBody: string;
        contextJson: string;
      };
    } catch (error) {
      const fallback = plaintextRelayPayload(input);
      if (fallback) return fallback;
      throw error;
    }
  }

  const fallback = plaintextRelayPayload(input);
  if (fallback) return fallback;
  throw new Error("Backend encryption key is not configured and no plaintext relay payload was provided.");
}

function plaintextRelayPayload(input: OpenVoiceRelayJobInput) {
  if (
    input.recipientName?.trim() &&
    input.recipientPhoneNumber?.trim() &&
    input.messageBody?.trim() &&
    input.contextJson?.trim()
  ) {
    return {
      recipientName: input.recipientName.trim(),
      recipientPhoneNumber: input.recipientPhoneNumber.trim(),
      messageBody: input.messageBody.trim(),
      contextJson: input.contextJson,
    };
  }
  return null;
}

function blankOr(value: string, fallback: string) {
  return value.trim().length > 0 ? value : fallback;
}

function safeJsonParse<T>(value: unknown): T | null {
  if (typeof value !== "string") return value as T;
  try {
    return JSON.parse(value) as T;
  } catch {
    return null;
  }
}
