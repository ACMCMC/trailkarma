# Mesh Relay Privacy Architecture

TrailKarma uses a privacy-preserving relay architecture that ensures hiker messages remain confidential even when passed through multiple intermediate "carrier" phones in the mesh network.

## The Challenge: Offline Privacy
In a typical mesh network, intermediate nodes can often see the data they are carrying. For sensitive hiker communications (like check-ins or emergency voice relays), this is unacceptable. However, a traditional handshake (like TLS) is impossible because the hiker and the backend are never online at the same time.

## The Solution: Non-Interactive Key Exchange (NIKE)
TrailKarma implements a **Non-Interactive Key Exchange** (a form of ECIES) that allows the hiker to encrypt data for the backend without ever establishing a connection.

### 1. Static-to-Ephemeral ECDH
The system relies on a combination of a **Static Public Key** (for the backend) and an **Ephemeral Key Pair** (generated on-the-fly by the hiker).

- **Backend (Oracle)**: Has a static X25519 key pair. The public key is bundled into the Android app.
- **Hiker (Sender)**:
    1. Generates a fresh **Ephemeral X25519 Key Pair** offline.
    2. Performs an ECDH (Elliptic Curve Diffie-Hellman) calculation using their *ephemeral private key* and the *backend's static public key*.
    3. Derives a **Shared Secret**.
    4. Encrypts the sensitive JSON (recipient details, message body, context) using **AES-256-GCM** with this secret.
    5. Broadcasts the `[Ephemeral Public Key, IV, Ciphertext]` via BLE.

### 2. Carrier Blindness
Intermediate carriers receive the `RelayPacket` via BLE. They see:
- A `job_id` (public)
- A `destination_hash` (truncated SHA-256 of the recipient's phone number)
- An `encrypted_blob` (opaque)
- The hiker's `Ephemeral Public Key` (opaque)

The carrier **cannot** read the message, see the recipient's phone number, or determine the hiker's precise identity beyond their trail name.

### 3. Decryption at the Oracle
When a carrier reaches internet connectivity, they submit the bundle to the backend.
- The backend uses its **Static Private Key** and the **Ephemeral Public Key** provided in the packet to re-derive the exact same **Shared Secret**.
- It decrypts the blob, recovers the recipient's phone number, and triggers the **ElevenLabs AI Voice Relay**.

## Security Implementation Details

### Cryptographic Stack
- **Asymmetric**: X25519 (Diffie-Hellman over Curve25519).
- **Symmetric**: AES-256-GCM (provides both confidentiality and integrity).
- **Encoding**: Payloads are packed as `[RawPubKey(32) || IV(12) || Ciphertext(N) || Tag(16)]` and Base64 encoded.

### Key Management
- **Backend Key**: Managed via the `RELAY_ENCRYPTION_PRIVATE_KEY` environment variable.
- **Android Key**: The backend's public key is injected via `build.gradle.kts`.
- **Ephemeral Keys**: Created per-message and destroyed immediately after encryption, providing a form of **Forward Secrecy** for the sender.

## Privacy Summary
| Data Type | Visible to Carrier? | Visible to Backend? | Visible to On-Chain? |
| :--- | :---: | :---: | :---: |
| Hiker Trail Name | Yes | Yes | Yes |
| Recipient Phone Number | No | Yes | No (Hashed) |
| Message Body | No | Yes | No (Hashed) |
| Hiker GPS Location | No | Yes | No |
| Signed Solana Intent | Yes | Yes | Yes |

This architecture balances the need for trustless relay (anyone can help) with the absolute requirement for hiker privacy (no one can spy).
