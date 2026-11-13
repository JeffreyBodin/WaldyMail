#!/usr/bin/env node
import { spawnSync } from 'node:child_process';
import { existsSync, readdirSync, statSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const ROOT_DIR = resolve(__filename, '..', '..');

const APP_ID = 'com.aistudio.postbox.v7k9qm';

function printHelp() {
  console.log(`Postbox Offline Mail - Local Build & Install CLI (No Server / No Hosting)

Usage:
  postbox info                  Show applicationId and local APK/AAB artifact status
  postbox build [debug|release|bundle]
                                Build offline APK (debug/release) or Play AAB (bundle) via Gradle
  postbox install [apkPath]     Install built offline APK onto a connected device via ADB
  postbox test                  Run local JVM/Robolectric unit tests via Gradle
  postbox help                  Show this help message
`);
}

function findArtifacts() {
  const candidates = [
    join(ROOT_DIR, 'app', 'build', 'outputs', 'apk', 'debug', 'app-debug.apk'),
    join(ROOT_DIR, 'app', 'build', 'outputs', 'apk', 'release', 'app-release.apk'),
    join(ROOT_DIR, 'app', 'build', 'outputs', 'apk', 'release', 'app-release-unsigned.apk'),
    join(ROOT_DIR, 'app', 'build', 'outputs', 'bundle', 'release', 'app-release.aab'),
    join(ROOT_DIR, '.build-outputs', 'apk', 'debug', 'app-debug.apk')
  ];

  const found = [];
  for (const file of candidates) {
    if (existsSync(file)) {
      const sizeKb = Math.round(statSync(file).size / 1024);
      found.push({ path: file, sizeKb });
    }
  }
  return found;
}

function runTool(cmd, args) {
  const result = spawnSync(cmd, args, {
    cwd: ROOT_DIR,
    stdio: 'inherit',
    shell: process.platform === 'win32'
  });
  if (result.error) {
    console.error(`Failed to execute '${cmd}': ${result.error.message}`);
    process.exit(1);
  }
  if (typeof result.status === 'number' && result.status !== 0) {
    process.exit(result.status);
  }
}

function printInfo() {
  console.log('Postbox Offline Mail');
  console.log(`- Application ID (Play Store): ${APP_ID}`);
  console.log('- Mode: 100% offline device-local Room storage (no INTERNET permission, no server)');
  console.log(`- Workspace: ${ROOT_DIR}`);

  const artifacts = findArtifacts();
  if (artifacts.length === 0) {
    console.log('- Built artifacts: none found yet. Run `postbox build debug` or `postbox build bundle`.');
  } else {
    console.log('- Built artifacts:');
    for (const item of artifacts) {
      console.log(`  * ${item.path} (${item.sizeKb} KB)`);
    }
  }
}

function handleBuild(mode = 'debug') {
  const taskMap = {
    debug: ':app:assembleDebug',
    release: ':app:assembleRelease',
    bundle: ':app:bundleRelease',
    aab: ':app:bundleRelease'
  };

  const task = taskMap[mode];
  if (!task) {
    console.error(`Unknown build target '${mode}'. Expected: debug, release, or bundle.`);
    process.exit(1);
  }

  if ((mode === 'release' || mode === 'bundle' || mode === 'aab') &&
      (!process.env.STORE_PASSWORD || !process.env.KEY_PASSWORD)) {
    console.warn(
      'Note: Release signing uses KEYSTORE_PATH (or my-upload-key.jks), STORE_PASSWORD, and KEY_PASSWORD environment variables.'
    );
  }

  console.log(`Running gradle ${task} ...`);
  runTool('gradle', [task]);
  printInfo();
}

function handleInstall(explicitPath) {
  let apkPath = explicitPath ? resolve(explicitPath) : null;
  if (!apkPath) {
    const apks = findArtifacts().filter((a) => a.path.endsWith('.apk'));
    if (apks.length === 0) {
      console.log('No APK found locally. Building debug APK first...');
      handleBuild('debug');
      apkPath = findArtifacts().find((a) => a.path.endsWith('.apk'))?.path;
    } else {
      apkPath = apks[0].path;
    }
  }

  if (!apkPath || !existsSync(apkPath)) {
    console.error('Could not locate an APK to install.');
    process.exit(1);
  }

  console.log(`Installing ${apkPath} via adb...`);
  runTool('adb', ['install', '-r', apkPath]);
}

const [, , command = 'help', arg] = process.argv;

switch (command) {
  case 'info':
    printInfo();
    break;
  case 'build':
    handleBuild(arg || 'debug');
    break;
  case 'install':
    handleInstall(arg);
    break;
  case 'test':
    runTool('gradle', [':app:testDebugUnitTest']);
    break;
  case 'help':
  case '--help':
  case '-h':
    printHelp();
    break;
  default:
    console.error(`Unknown command '${command}'.`);
    printHelp();
    process.exit(1);
}
