import { spawnSync } from 'node:child_process';
import { existsSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const DEFAULT_ROOT = resolve(fileURLToPath(import.meta.url), '..', '..', '..');

/**
 * Explicit constructor function for granular Git staging and commit tracking via scripts/stage-commit.sh.
 * Callable both with `new GitCommitHelper(options)` and directly as `GitCommitHelper(options)`.
 */
export function GitCommitHelper(options = {}) {
  if (!(this instanceof GitCommitHelper)) {
    return new GitCommitHelper(options);
  }

  this.rootDir = resolve(options.rootDir || DEFAULT_ROOT);
  this.scriptPath = join(this.rootDir, 'scripts', 'stage-commit.sh');
}

GitCommitHelper.prototype.runHelper = function runHelper(args) {
  if (!existsSync(this.scriptPath)) {
    throw new Error(`Commit helper script not found at '${this.scriptPath}'.`);
  }

  const result = spawnSync('bash', [this.scriptPath, ...args], {
    cwd: this.rootDir,
    stdio: 'inherit'
  });

  if (result.error) {
    throw new Error(`Failed to execute stage-commit.sh: ${result.error.message}`);
  }
  if (typeof result.status === 'number' && result.status !== 0) {
    throw new Error(`stage-commit.sh exited with status ${result.status}`);
  }
  return { args, exitCode: 0 };
};

GitCommitHelper.prototype.status = function status() {
  return this.runHelper(['--status']);
};

GitCommitHelper.prototype.stageAndCommit = function stageAndCommit(message, paths = []) {
  if (!message || !Array.isArray(paths) || paths.length === 0) {
    throw new Error('Provide a descriptive commit message and at least one file path.');
  }
  return this.runHelper(['-m', message, ...paths]);
};

GitCommitHelper.prototype.stagePatch = function stagePatch(paths = []) {
  return this.runHelper(['-p', ...paths]);
};

GitCommitHelper.prototype.setRemote = function setRemote(remoteUrl = '', remoteName = 'origin') {
  const args = remoteUrl ? ['--remote', remoteUrl, remoteName] : ['--remote'];
  return this.runHelper(args);
};

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  const mode = process.argv[2] || 'status';
  try {
    const helper = new GitCommitHelper();
    if (mode === 'patch') {
      helper.stagePatch(process.argv.slice(3));
    } else if (mode === 'commit') {
      const [, , , msg, ...paths] = process.argv;
      helper.stageAndCommit(msg, paths);
    } else if (mode === 'remote') {
      helper.setRemote(process.argv[3], process.argv[4]);
    } else {
      helper.status();
    }
  } catch (err) {
    console.error(err.message);
    process.exit(1);
  }
}
