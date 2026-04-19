import crypto from "node:crypto";

/**
 * Decrypts a payload encrypted by the Android app using X25519 and AES-GCM.
 * Packed format: [EphemeralPubKey(32), IV(12), Ciphertext+Tag]
 */
export function decryptRelayPayload(encryptedBase64: string, privateKeyPem: string): string {
    const packed = Buffer.from(encryptedBase64, "base64");
    if (packed.length < 32 + 12 + 16) {
        throw new Error("Invalid encrypted payload length");
    }

    const ephemeralPubKeyRaw = packed.subarray(0, 32);
    const iv = packed.subarray(32, 32 + 12);
    const cipherText = packed.subarray(32 + 12);

    // 1. Load Private Key
    const privateKey = crypto.createPrivateKey(privateKeyPem);

    // 2. Perform ECDH to get shared secret
    // Node.js crypto requires the public key in SPKI format or as a KeyObject
    // We'll wrap the raw 32 bytes in a basic SPKI header
    const spkiHeader = Buffer.from("302a300506032b656e032100", "hex");
    const ephemeralPubKey = crypto.createPublicKey({
        key: Buffer.concat([spkiHeader, ephemeralPubKeyRaw]),
        format: "der",
        type: "spki",
    });

    const sharedSecret = crypto.diffieHellman({
        privateKey,
        publicKey: ephemeralPubKey,
    });

    // 3. Decrypt with AES-GCM
    const decipher = crypto.createDecipheriv("aes-256-gcm", sharedSecret, iv);
    // In AES-GCM, the tag is at the end of the ciphertext in our Android impl
    const tag = cipherText.subarray(cipherText.length - 16);
    const data = cipherText.subarray(0, cipherText.length - 16);
    
    decipher.setAuthTag(tag);
    
    const decrypted = Buffer.concat([
        decipher.update(data),
        decipher.final()
    ]);

    return decrypted.toString("utf8");
}
