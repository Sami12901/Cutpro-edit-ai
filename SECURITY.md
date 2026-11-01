# Security Policy

CutPro takes application security, data integrity, and local privacy seriously.

---

## Supported Versions

| Version | Supported |
|---|---|
| 1.0.x | Yes |
| < 1.0 | No |

---

## Reporting a Vulnerability

If you discover a security vulnerability in CutPro, please do not disclose it publicly in an open GitHub issue.

Please report the issue confidentially by email:
- **Security Contact**: security@cutpro.studio
- Please include:
  - Description of the vulnerability.
  - Steps to reproduce or proof-of-concept project manifest.
  - Affected device architecture or Android version.

We will acknowledge receipt within 48 hours and provide updates until the vulnerability is resolved and patched.

---

## Security Invariants in CutPro

- **Zero Remote Cloud Uploads**: CutPro operates completely offline without remote servers or cloud accounts.
- **Zip-Slip Protection**: `.cutprobundle` project archive extraction strictly verifies canonical paths to prevent directory traversal outside extraction targets.
- **Android Keystore Encryption**: Project preferences and sensitive settings are encrypted using hardware-backed AES-256-GCM via AndroidX Security.
- **Scoped Storage**: CutPro strictly respects Android 10+ Scoped Storage boundaries using `MediaStore.Video.Media.IS_PENDING` and persistent SAF URIs.
