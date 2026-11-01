# Building CutPro on Android

This guide explains how to set up the development environment, build the project, run tests, and package release APKs.

---

## 1. Development Environment Prerequisites

- **Operating System**: Linux, macOS, or Windows 10/11 (64-bit).
- **IDE**: Android Studio Ladybug (2024.2.1) or newer.
- **Java Development Kit (JDK)**: JDK 17 (Microsoft OpenJDK 17 or Eclipse Temurin 17).
- **Android SDK Components**:
  - Android SDK Platform 34 (Android 14 / UpsideDownCake).
  - Android SDK Build-Tools 34.0.0.
  - Android NDK (optional, for native rendering optimizations).
  - Android SDK Command-line Tools (latest).

---

## 2. Environment Configuration

### Java Environment Variable
Ensure `JAVA_HOME` points to your JDK 17 installation.

On Windows (PowerShell):
```powershell
$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot"
```

On macOS / Linux:
```bash
export JAVA_HOME="/usr/lib/jvm/java-17-openjdk"
```

### Android SDK Path
Create or update `local.properties` in the project root:
```properties
sdk.dir=/path/to/your/android/sdk
```
On Windows:
```properties
sdk.dir=C\:\\Users\\<username>\\AppData\\Local\\Android\\Sdk
```

---

## 3. Command Line Compilation

CutPro uses Gradle with Kotlin DSL and the Gradle Version Catalog (`gradle/libs.versions.toml`).

### Clean and Build
```bash
# Linux/macOS
./gradlew clean build

# Windows PowerShell
.\gradlew.bat clean build
```

### Compile Debug APK
```bash
# Linux/macOS
./gradlew :app:assembleDebug

# Windows PowerShell
.\gradlew.bat :app:assembleDebug
```
The resulting debug APK is located at:
`app/build/outputs/apk/debug/app-debug.apk`

---

## 4. Running Automated Tests

### Unit Tests (All Modules)
```bash
# Linux/macOS
./gradlew testDebugUnitTest

# Windows PowerShell
.\gradlew.bat testDebugUnitTest
```

### Specific Module Unit Tests
```bash
# Test Core Motion Graphics & Subtitles
.\gradlew.bat :core:motiongraphics:testDebugUnitTest :core:subtitles:testDebugUnitTest

# Test AI Subsystem
.\gradlew.bat :core:ai:testDebugUnitTest

# Test Media3 Export Pipeline
.\gradlew.bat :core:export:testDebugUnitTest :feature:export:testDebugUnitTest
```

### Opaque-Box E2E Regression Suite (915 Tests)
CutPro features a standalone opaque-box end-to-end Python test runner:
```bash
python tests/e2e/runner.py
```
Outputs test execution metrics in JSON and JUnit XML formats to `tests/e2e/reports/`.

---

## 5. Release Packaging & Signing

### Generating a Release Keystore
Generate a private signing key using the `keytool` utility:
```bash
keytool -genkey -v -keystore cutpro-release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias cutpro
```

### Secure Signing Configuration
Set signing credentials in environment variables (never commit keystores or passwords to Git):
```bash
export CUTPRO_KEYSTORE_FILE="/path/to/cutpro-release.jks"
export CUTPRO_KEYSTORE_PASSWORD="your-keystore-password"
export CUTPRO_KEY_ALIAS="cutpro"
export CUTPRO_KEY_PASSWORD="your-key-password"
```

### Building Signed Release APK
```bash
./gradlew :app:assembleRelease
```
The output file will be generated at:
`app/build/outputs/apk/release/app-release.apk`
