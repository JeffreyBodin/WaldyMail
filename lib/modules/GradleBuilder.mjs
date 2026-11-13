import { spawnSync } from 'node:child_process';
import { resolve } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { MetadataInspector } from './MetadataInspector.mjs';

const DEFAULT_ROOT = resolve(fileURLToPath(import.meta.url), '..', '..', '..');

const TARGET_TASK_MAP = {
  debug: ':app:assembleDebug',
  release: ':app:assembleRelease',
  bundle: ':app:bundleRelease',
  aab: ':app:bundleRelease',
  test: ':app:testDebugUnitTest'
};

/**
 * Explicit constructor function for executing offline Gradle builds and unit tests.
 * Callable both with `new GradleBuilder(options)` and directly as `GradleBuilder(options)`.
 */
export function GradleBuilder(options = {}) {
  if (!(this instanceof GradleBuilder)) {
    return new GradleBuilder(options);
  }

  this.rootDir = resolve(options.rootDir || DEFAULT_ROOT);
  this.inspector = options.inspector || new MetadataInspector({ rootDir: this.rootDir });
}

GradleBuilder.prototype.resolveTask = function resolveTask(target = 'debug') {
  const task = TARGET_TASK_MAP[target];
  if (!task) {
    throw new Error(`Unknown Gradle target '${target}'. Expected: debug, release, bundle, aab, or test.`);
  }
  return task;
};

GradleBuilder.prototype.runGradleTask = function runGradleTask(task) {
  const result = spawnSync('gradle', [task], {
    cwd: this.rootDir,
    stdio: 'inherit',
    shell: process.platform === 'win32'
  });

  if (result.error) {
    throw new Error(`Failed to execute 'gradle ${task}': ${result.error.message}`);
  }
  if (typeof result.status === 'number' && result.status !== 0) {
    throw new Error(`Gradle task '${task}' exited with status ${result.status}`);
  }
  return { task, exitCode: 0 };
};

GradleBuilder.prototype.build = function build(target = 'debug') {
  const task = this.resolveTask(target);

  if ((target === 'release' || target === 'bundle' || target === 'aab') &&
      (!process.env.STORE_PASSWORD || !process.env.KEY_PASSWORD)) {
    console.warn(
      'Note: Release signing reads KEYSTORE_PATH (or my-upload-key.jks), STORE_PASSWORD, and KEY_PASSWORD.'
    );
  }

  console.log(`Running gradle ${task} ...`);
  const outcome = this.runGradleTask(task);
  if (target !== 'test') {
    this.inspector.printReport();
  }
  return outcome;
};

GradleBuilder.prototype.runTests = function runTests() {
  return this.build('test');
};

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  const target = process.argv[2] || 'debug';
  try {
    const builder = new GradleBuilder();
    builder.build(target);
  } catch (err) {
    console.error(err.message);
    process.exit(1);
  }
}
