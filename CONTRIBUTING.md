# Contributing to CutPro

Thank you for your interest in contributing to CutPro. We welcome contributions that align with our mission of building a professional, native Android-only video editor.

---

## Code of Conduct

All contributors are expected to uphold the standards outlined in our [Code of Conduct](CODE_OF_CONDUCT.md).

---

## Architectural Principles & Guardrails

1. **Native Android Technologies Only**:
   - No webviews, Electron, React Native, Flutter, or browser-based editors.
   - Code must be native Kotlin utilizing Jetpack Compose and official Android platform APIs.
2. **Zero-Emoji Policy**:
   - Strictly no emoji characters in any UI component, toolbar icon, button, notification, template, test, or documentation file.
   - Use Material Symbols and native vector drawables.
3. **Unidirectional Data Flow**:
   - Compose components observe StateFlow from ViewModels.
   - Domain models in `:core:model` must remain pure Kotlin with zero Android SDK dependencies.
4. **Deterministic Animation Math**:
   - Motion graphics evaluations must be closed-form functions of presentation timestamps ($t_{\mu\text{s}}$) to ensure export and preview parity.
5. **Offline-First & Privacy First**:
   - Core editing, animation, and export must never require network connectivity, user accounts, or paid external APIs.

---

## Development Workflow

1. Fork the repository and create a feature branch:
   ```bash
   git checkout -b feature/your-feature-name
   ```
2. Ensure all unit and E2E regression tests pass:
   ```bash
   ./gradlew testDebugUnitTest
   python tests/e2e/runner.py
   ```
3. Verify that the debug APK compiles without warnings:
   ```bash
   ./gradlew :app:assembleDebug
   ```
4. Commit your changes with clear, descriptive commit messages adhering to Conventional Commits:
   ```bash
   git commit -m "feat(timeline): implement ripple delete on magnetic snap"
   ```
5. Submit a pull request using the provided [Pull Request Template](.github/PULL_REQUEST_TEMPLATE.md).
