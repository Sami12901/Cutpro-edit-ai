# CutPro Privacy Manifesto

CutPro is engineered as an offline-first, private-by-design native Android application.

---

## 1. Core Principles

- **No Mandatory Accounts**: CutPro does not require user registration, email sign-in, OAuth, or subscriptions to access any editing, animation, or export feature.
- **No Remote Telemetry or Tracking**: No embedded analytics frameworks, tracking pixels, advertising SDKs, crash beacons, or user monitoring libraries exist in CutPro.
- **Offline-First Processing**: Video decoding, audio DSP mixing, motion graphics animation, subtitle parsing, and video encoding occur entirely on the local device hardware.
- **Zero Cloud Video Transcoding**: Your media files are never uploaded to remote servers or cloud services.
- **Local Speech-to-Text**: Speech transcription models run locally on-device. Audio data and transcripts are never transmitted over the network.
- **Explicit Storage Permissions**: Media import utilizes Android's Photo Picker and Storage Access Framework (SAF) to request access strictly to user-selected media assets. Unrestricted storage permissions are never requested.

---

## 2. Cryptographic Security

- **Android Keystore**: Sensitive local configuration is encrypted using hardware-backed AES-256-GCM via `EncryptedSharedPreferences`.
- **Portable Bundles**: `.cutprobundle` archives enforce strict canonical path verification to prevent Zip-Slip directory traversal and streaming decompression limits to reject zip bombs.

---

## 3. Disclosures

While CutPro does not transmit user data across the network, local project files and unencrypted media files remain subject to device-level security and Android sandbox permissions. Users are advised to utilize device lock screens and Android full-disk encryption to protect local data.
