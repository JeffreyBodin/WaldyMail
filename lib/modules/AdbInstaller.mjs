import { spawnSync } from 'node:child_process';
import { existsSync } from 'node:fs';
import { resolve } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { GradleBuilder } from './GradleBuilder.mjs';
import { MetadataInspector } from './MetadataInspector.mjs';

const DEFAULT_ROOT = resolve(fileURLToPath(import.meta.url), '..', '..', '..');
const WSA_ADDRESS = '127.0.0.1:58526';

/**
 * Explicit constructor function for installing the offline Postbox APK via ADB or Windows Subsystem for Android.
 * Callable both with `new AdbInstaller(options)` and directly as `AdbInstaller(options)`.
 */
export function AdbInstaller(options = {}) {
  if (!(this instanceof AdbInstaller)) {
    return new AdbInstaller(options);
  }

  this.rootDir = resolve(options.rootDir || DEFAULT_ROOT);
  this.inspector = options.inspector || new MetadataInspector({ rootDir: this.rootDir });
  this.builder = options.builder || new GradleBuilder({ rootDir: this.rootDir, inspector: this.inspector });
}

AdbInstaller.prototype.resolveApk = function resolveApk(explicitPath) {
  if (explicitPath) {
    const resolved = resolve(explicitPath);
    if (!existsSync(resolved)) {
      throw new Error(`APK not found at '${resolved}'.`);
    }
    return resolved;
  }

  const apks = this.inspector.findArtifacts().filter((item) => item.path.endsWith('.apk'));
  if (apks.length > 0) {
    return apks[0].path;
  }

  console.log('No APK found locally. Building debug APK first...');
  this.builder.build('debug');
  const built = this.inspector.findArtifacts().find((item) => item.path.endsWith('.apk'));
  if (!built) {
    throw new Error('Could not locate an APK file after running debug build.');
  }
  return built.path;
};

AdbInstaller.prototype.runAdb = function runAdb(args) {
  const result = spawnSync('adb', args, {
    cwd: this.rootDir,
    stdio: 'inherit',
    shell: process.platform === 'win32'
  });

  if (result.error) {
    throw new Error(`Failed to execute 'adb ${args.join(' ')}': ${result.error.message}`);
  }
  if (typeof result.status === 'number' && result.status !== 0) {
    throw new Error(`adb exited with status ${result.status}`);
  }
  return { args, exitCode: 0 };
};

AdbInstaller.prototype.installAndroid = function installAndroid(explicitApkPath) {
  const apkPath = this.resolveApk(explicitApkPath);
  console.log(`Installing ${apkPath} via adb...`);
  return this.runAdb(['install', '-r', apkPath]);
};

AdbInstaller.prototype.installWsa = function installWsa(explicitApkPath) {
  const apkPath = this.resolveApk(explicitApkPath);
  console.log(`Connecting to Windows Subsystem for Android (${WSA_ADDRESS})...`);
  this.runAdb(['connect', WSA_ADDRESS]);
  return this.runAdb(['-s', WSA_ADDRESS, 'install', '-r', apkPath]);
};

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  const mode = process.argv[2] || 'android';
  const apkArg = process.argv[3];
  try {
    const installer = new AdbInstaller();
    if (mode === 'wsa') {
      installer.installWsa(apkArg);
    } else {
      installer.installAndroid(apkArg);
    }
  } catch (err) {
    console.error(err.message);
    process.exit(1);
  }
}
