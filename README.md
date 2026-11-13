# Postbox — Offline Packaging & Personal Google Play Upload Guide

Postbox is a **100% offline Android client** backed locally by Room SQLite. It does **not** run any web server, Node host, or cloud backend.

---

## 1. Personal Google Play Console Upload

- **Application ID**: `com.aistudio.postbox.v7k9qm` (updated from the reserved `com.example.postbox` prefix so Google Play Console accepts the listing).
- **Permissions**: Zero dangerous permissions and no `INTERNET` permission.

### Build a Signed Android App Bundle (`.aab`)
1. Generate or point to your personal upload keystore (`.jks`) with alias `upload`:
   - `KEYSTORE_PATH=/path/to/my-upload-key.jks`
   - `STORE_PASSWORD=<your-store-password>`
   - `KEY_PASSWORD=<your-key-password>`
2. Run:
   ```bash
   gradle :app:bundleRelease
   ```
3. Upload the generated bundle to your Google Play Console track:
   - `app/build/outputs/bundle/release/app-release.aab`

---

## 2. NPM Package (`postbox-offline-mail`)

Packaged as a pure local CLI utility (`bin/postbox.mjs`) — **never starts a server**.

```bash
# Install globally from this folder (or link locally)
npm install -g .

# Available CLI commands
postbox info
postbox build debug      # builds app/build/outputs/apk/debug/app-debug.apk
postbox build bundle     # builds app/build/outputs/bundle/release/app-release.aab
postbox install          # sideloads APK via ADB
postbox test             # runs local JVM/Robolectric tests
```

Or create a distributable `.tgz` NPM tarball:
```bash
npm pack
```

---

## 3. Docker Package (Build & Export Only — No Server)

Builds the Android binary inside a container and exports the `.apk` / `.aab` with **no exposed ports and no running server**.

### Option A: Export `.apk` directly to `./dist` via BuildKit
```bash
docker build --target artifact --output type=local,dest=./dist .
```

### Option B: Build an exporter image and extract `.apk` / `.aab`
```bash
docker build -t postbox-offline-apk .
docker run --rm -v "$PWD/dist:/export" postbox-offline-apk
```

To build a release `.aab` inside Docker (pass signing args/env as needed):
```bash
docker build --build-arg GRADLE_TASK=:app:bundleRelease --target artifact --output type=local,dest=./dist .
```

---

## 4. Windows Context Package (`windows/`)

Use PowerShell or Command Prompt directly on Windows (no server or Node host required):

```powershell
# Inspect local APK/AAB artifacts
.\windows\install-postbox.ps1 -Action Info

# Build offline debug APK
.\windows\install-postbox.ps1 -Action BuildApk

# Build signed release AAB for Google Play
$env:KEYSTORE_PATH = "C:\keys\my-upload-key.jks"
$env:STORE_PASSWORD = "your-store-password"
$env:KEY_PASSWORD = "your-key-password"
.\windows\install-postbox.ps1 -Action BuildBundle

# Install onto a USB/Wi-Fi Android device via ADB
.\windows\install-postbox.ps1 -Action InstallAdb

# Or install into Windows Subsystem for Android (WSA)
.\windows\install-postbox.ps1 -Action InstallWsa
```

Command Prompt launcher:
```cmd
windows\postbox.cmd BuildApk
windows\postbox.cmd InstallWsa
```

---

## 5. Modular JavaScript Toolkit & Windows Bundle Builder (`index.mjs`)

`index.mjs` loads every script module in `lib/modules/` inline and exports each as an individual callable constructor function object (callable with or without `new`):

- `MetadataInspector` (`lib/modules/MetadataInspector.mjs`)
- `GradleBuilder` (`lib/modules/GradleBuilder.mjs`)
- `AdbInstaller` (`lib/modules/AdbInstaller.mjs`)
- `DockerPackager` (`lib/modules/DockerPackager.mjs`)
- `WindowsBundleBuilder` (`lib/modules/WindowsBundleBuilder.mjs`)
- `GitCommitHelper` (`lib/modules/GitCommitHelper.mjs`)
- `PostboxToolkit` (composed master constructor)

Prepare a self-contained Windows application/installer bundle in `dist/windows-bundle/`:

```bash
npm run prepare:windows
```


