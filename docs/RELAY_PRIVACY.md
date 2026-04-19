# Relay Privacy

TrailKarma uses **Non-Interactive Key Exchange (NIKE)** for offline-to-online encryption.

## Architecture
1. **Offline Encryption**: Hiker generates an ephemeral X25519 key pair and performs ECDH with the backend's static public key to derive an AES-256-GCM secret.
2. **Carrier Blindness**: Mesh carriers see only the `encrypted_blob`, `destination_hash`, and the hiker's `ephemeral_public_key`. They cannot read messages or phone numbers.
3. **Oracle Decryption**: The backend uses its static private key and the ephemeral public key to re-derive the secret and process the voice relay.

## Specs
- **Asymmetric**: X25519
- **Symmetric**: AES-256-GCM
- **Format**: `[RawPubKey(32) || IV(12) || Ciphertext(N) || Tag(16)]` (Base64)

## Visibility Matrix
| Data | Carrier | Backend | Chain |
| :--- | :---: | :---: | :---: |
| Trail Name | Yes | Yes | Yes |
| Message/Phone | **No** | Yes | No (Hashed) |
| GPS Location | **No** | Yes | No |
| Solana Intent | Yes | Yes | Yes |
