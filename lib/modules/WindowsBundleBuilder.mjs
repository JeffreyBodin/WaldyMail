import { cpSync, existsSync, mkdirSync, rmSync, writeFileSync } from 'node:fs';
import { basename, join, resolve } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { MetadataInspector } from './MetadataInspector.mjs';

const DEFAULT_ROOT = resolve(fileURLToPath(import.meta.url), '..', '..', '..');

/**
 * Explicit constructor function that prepares project files and built Android artifacts
 * into a self-contained Windows application/installer staging bundle (`dist/windows-bundle`).
 * Callable both with `new WindowsBundleBuilder(options)` and directly as `WindowsBundleBuilder(options)`.
 */
export function WindowsBundleBuilder(options = {}) {
  if (!(this instanceof WindowsBundleBuilder)) {
    return new WindowsBundleBuilder(options);
  }

  this.rootDir = resolve(options.rootDir || DEFAULT_ROOT);
  this.bundleDir = resolve(options.bundleDir || join(this.rootDir, 'dist', 'windows-bundle'));
  this.inspector = options.inspector || new MetadataInspector({ rootDir: this.rootDir });
}

WindowsBundleBuilder.prototype.copyRequiredFiles = function copyRequiredFiles() {
  const entries = [
    'package.json',
    'README.md',
    'windows',
    'bin',
    'scripts'
  ];

  const copied = [];
  for (const relPath of entries) {
    const src = join(this.rootDir, relPath);
    if (existsSync(src)) {
      const dest = join(this.bundleDir, relPath);
      cpSync(src, dest, { recursive: true });
      copied.push(relPath);
    }
  }

  const indexSrc = join(this.rootDir, 'index.mjs');
  if (existsSync(indexSrc)) {
    cpSync(indexSrc, join(this.bundleDir, 'index.mjs'));
    copied.push('index.mjs');
  }

  const libSrc = join(this.rootDir, 'lib');
  if (existsSync(libSrc)) {
    cpSync(libSrc, join(this.bundleDir, 'lib'), { recursive: true });
    copied.push('lib');
  }

  return copied;
};

WindowsBundleBuilder.prototype.stageArtifacts = function stageArtifacts() {
  const artifactsDir = join(this.bundleDir, 'artifacts');
  mkdirSync(artifactsDir, { recursive: true });

  const artifacts = this.inspector.findArtifacts(true);
  const staged = [];

  for (const item of artifacts) {
    const fileName = `${item.kind}-${basename(item.path)}`;
    const dest = join(artifactsDir, fileName);
    cpSync(item.path, dest);
    staged.push({
      kind: item.kind,
      fileName,
      sizeKb: item.sizeKb,
      sha256: item.sha256
    });
  }

  return staged;
};

WindowsBundleBuilder.prototype.writeSetupLauncher = function writeSetupLauncher() {
  const launcherPath = join(this.bundleDir, 'Setup-Postbox.cmd');
  const script = `@echo off
setlocal
set "ACTION=%~1"
if "%ACTION%"=="" set "ACTION=InstallWsa"

for %%F in ("%~dp0artifacts\\debug-apk-*.apk") do (
  set "BUNDLED_APK=%%~fF"
  goto :found
)
for %%F in ("%~dp0artifacts\\release-apk-*.apk") do (
  set "BUNDLED_APK=%%~fF"
  goto :found
)

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0windows\\install-postbox.ps1" -Action %ACTION%
exit /b %ERRORLEVEL%

:found
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0windows\\install-postbox.ps1" -Action %ACTION% -ApkPath "%BUNDLED_APK%"
exit /b %ERRORLEVEL%
`;
  writeFileSync(launcherPath, script.replace(/\n/g, '\r\n'), 'utf8');
  return launcherPath;
};

WindowsBundleBuilder.prototype.prepare = function prepare() {
  rmSync(this.bundleDir, { recursive: true, force: true });
  mkdirSync(this.bundleDir, { recursive: true });

  const copiedEntries = this.copyRequiredFiles();
  const stagedArtifacts = this.stageArtifacts();
  const launcherPath = this.writeSetupLauncher();

  const manifest = {
    name: 'Postbox Offline Mail - Windows Installer Bundle',
    applicationId: this.inspector.applicationId,
    generatedAt: new Date().toISOString(),
    offlineOnly: true,
    bundleDir: this.bundleDir,
    includedEntries: copiedEntries,
    artifacts: stagedArtifacts,
    launcher: basename(launcherPath)
  };

  const manifestPath = join(this.bundleDir, 'bundle-manifest.json');
  writeFileSync(manifestPath, `${JSON.stringify(manifest, null, 2)}\n`, 'utf8');

  console.log(`Prepared Windows installer bundle at: ${this.bundleDir}`);
  console.log(`- Included entries (${copiedEntries.length}): ${copiedEntries.join(', ')}`);
  console.log(`- Staged artifacts (${stagedArtifacts.length}): ${stagedArtifacts.map((a) => a.fileName).join(', ') || 'none'}`);
  console.log(`- Launcher: ${launcherPath}`);
  console.log(`- Manifest: ${manifestPath}`);

  return manifest;
};

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  try {
    const bundleBuilder = new WindowsBundleBuilder();
    bundleBuilder.prepare();
  } catch (err) {
    console.error(err.message);
    process.exit(1);
  }
}
