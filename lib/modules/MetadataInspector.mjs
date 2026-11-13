import { createHash } from 'node:crypto';
import { existsSync, readFileSync, statSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const DEFAULT_ROOT = resolve(fileURLToPath(import.meta.url), '..', '..', '..');
const PLAY_APP_ID = 'com.aistudio.postbox.v7k9qm';

/**
 * Explicit constructor function for inspecting offline Postbox metadata and APK/AAB artifacts.
 * Callable both with `new MetadataInspector(options)` and directly as `MetadataInspector(options)`.
 */
export function MetadataInspector(options = {}) {
  if (!(this instanceof MetadataInspector)) {
    return new MetadataInspector(options);
  }

  this.rootDir = resolve(options.rootDir || DEFAULT_ROOT);
  this.applicationId = options.applicationId || PLAY_APP_ID;
}

MetadataInspector.prototype.computeSha256 = function computeSha256(filePath) {
  const hash = createHash('sha256');
  hash.update(readFileSync(filePath));
  return hash.digest('hex');
};

MetadataInspector.prototype.findArtifacts = function findArtifacts(includeHashes = false) {
  const candidates = [
    { kind: 'debug-apk', path: join(this.rootDir, 'app', 'build', 'outputs', 'apk', 'debug', 'app-debug.apk') },
    { kind: 'release-apk', path: join(this.rootDir, 'app', 'build', 'outputs', 'apk', 'release', 'app-release.apk') },
    { kind: 'release-unsigned-apk', path: join(this.rootDir, 'app', 'build', 'outputs', 'apk', 'release', 'app-release-unsigned.apk') },
    { kind: 'release-aab', path: join(this.rootDir, 'app', 'build', 'outputs', 'bundle', 'release', 'app-release.aab') },
    { kind: 'cached-debug-apk', path: join(this.rootDir, '.build-outputs', 'apk', 'debug', 'app-debug.apk') }
  ];

  const found = [];
  for (const candidate of candidates) {
    if (existsSync(candidate.path)) {
      const sizeBytes = statSync(candidate.path).size;
      const entry = {
        kind: candidate.kind,
        path: candidate.path,
        sizeKb: Math.round(sizeBytes / 1024)
      };
      if (includeHashes) {
        entry.sha256 = this.computeSha256(candidate.path);
      }
      found.push(entry);
    }
  }
  return found;
};

MetadataInspector.prototype.inspect = function inspect() {
  return {
    name: 'Postbox Offline Mail',
    applicationId: this.applicationId,
    mode: '100% offline device-local Room storage (no INTERNET permission, no server)',
    rootDir: this.rootDir,
    artifacts: this.findArtifacts(false)
  };
};

MetadataInspector.prototype.printReport = function printReport() {
  const summary = this.inspect();
  console.log(summary.name);
  console.log(`- Application ID (Play Store): ${summary.applicationId}`);
  console.log(`- Mode: ${summary.mode}`);
  console.log(`- Workspace: ${summary.rootDir}`);
  if (summary.artifacts.length === 0) {
    console.log('- Built artifacts: none found yet. Run `npm run build:apk` or `npm run build:aab`.');
  } else {
    console.log('- Built artifacts:');
    for (const artifact of summary.artifacts) {
      console.log(`  * [${artifact.kind}] ${artifact.path} (${artifact.sizeKb} KB)`);
    }
  }
  return summary;
};

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  const inspector = new MetadataInspector();
  inspector.printReport();
}
