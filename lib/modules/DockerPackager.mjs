import { spawnSync } from 'node:child_process';
import { existsSync, mkdirSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const DEFAULT_ROOT = resolve(fileURLToPath(import.meta.url), '..', '..', '..');

/**
 * Explicit constructor function for Docker-based offline APK/AAB building and artifact export.
 * Never exposes ports or starts a server container.
 * Callable both with `new DockerPackager(options)` and directly as `DockerPackager(options)`.
 */
export function DockerPackager(options = {}) {
  if (!(this instanceof DockerPackager)) {
    return new DockerPackager(options);
  }

  this.rootDir = resolve(options.rootDir || DEFAULT_ROOT);
  this.imageName = options.imageName || 'postbox-offline-apk';
  this.exportDir = resolve(options.exportDir || join(this.rootDir, 'dist', 'docker'));
}

DockerPackager.prototype.verifyDockerfile = function verifyDockerfile() {
  const dockerfilePath = join(this.rootDir, 'Dockerfile');
  if (!existsSync(dockerfilePath)) {
    throw new Error(`Dockerfile not found at '${dockerfilePath}'.`);
  }
  return dockerfilePath;
};

DockerPackager.prototype.getCommands = function getCommands(gradleTask = ':app:assembleDebug') {
  this.verifyDockerfile();
  return {
    exportDirect: `docker build --build-arg GRADLE_TASK=${gradleTask} --target artifact --output type=local,dest=${this.exportDir} .`,
    buildExporterImage: `docker build --build-arg GRADLE_TASK=${gradleTask} -t ${this.imageName} .`,
    runExporterContainer: `docker run --rm -v "${this.exportDir}:/export" ${this.imageName}`
  };
};

DockerPackager.prototype.exportArtifacts = function exportArtifacts(gradleTask = ':app:assembleDebug') {
  this.verifyDockerfile();
  mkdirSync(this.exportDir, { recursive: true });

  const args = [
    'build',
    '--build-arg',
    `GRADLE_TASK=${gradleTask}`,
    '--target',
    'artifact',
    '--output',
    `type=local,dest=${this.exportDir}`,
    '.'
  ];

  const result = spawnSync('docker', args, {
    cwd: this.rootDir,
    stdio: 'inherit',
    shell: process.platform === 'win32'
  });

  if (result.error) {
    throw new Error(`Failed to execute 'docker': ${result.error.message}`);
  }
  if (typeof result.status === 'number' && result.status !== 0) {
    throw new Error(`docker build exited with status ${result.status}`);
  }
  return { exportDir: this.exportDir, gradleTask };
};

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  const action = process.argv[2] || 'commands';
  const task = process.argv[3] || ':app:assembleDebug';
  try {
    const packager = new DockerPackager();
    if (action === 'export') {
      packager.exportArtifacts(task);
    } else {
      console.log('Docker Offline Build/Export Commands (No Server):');
      console.log(JSON.stringify(packager.getCommands(task), null, 2));
    }
  } catch (err) {
    console.error(err.message);
    process.exit(1);
  }
}
