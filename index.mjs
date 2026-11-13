import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT_DIR = resolve(fileURLToPath(import.meta.url), '..');

/**
 * Explicit registry pointing to each individual Postbox build, packaging,
 * installation, and repository management script module.
 */
export const MODULE_REGISTRY = Object.freeze({
  MetadataInspector: './lib/modules/MetadataInspector.mjs',
  GradleBuilder: './lib/modules/GradleBuilder.mjs',
  AdbInstaller: './lib/modules/AdbInstaller.mjs',
  DockerPackager: './lib/modules/DockerPackager.mjs',
  WindowsBundleBuilder: './lib/modules/WindowsBundleBuilder.mjs',
  GitCommitHelper: './lib/modules/GitCommitHelper.mjs'
});

/**
 * Loads all registered script dependencies inline and binds each exported
 * constructor as an individual callable function object.
 */
async function loadInlineDependencies(registry) {
  const entries = Object.entries(registry);
  const resolved = {};

  for (const [exportName, relativePath] of entries) {
    const moduleUrl = new URL(relativePath, import.meta.url).href;
    const loadedModule = await import(moduleUrl);
    const ctor = loadedModule[exportName];

    if (typeof ctor !== 'function') {
      throw new TypeError(
        `Dependency '${exportName}' at '${relativePath}' did not export a callable constructor function.`
      );
    }

    resolved[exportName] = ctor;
  }

  return Object.freeze(resolved);
}

export const loadedDependencies = await loadInlineDependencies(MODULE_REGISTRY);

export const MetadataInspector = loadedDependencies.MetadataInspector;
export const GradleBuilder = loadedDependencies.GradleBuilder;
export const AdbInstaller = loadedDependencies.AdbInstaller;
export const DockerPackager = loadedDependencies.DockerPackager;
export const WindowsBundleBuilder = loadedDependencies.WindowsBundleBuilder;
export const GitCommitHelper = loadedDependencies.GitCommitHelper;

/**
 * Explicit master constructor function composing all inline-loaded Postbox module constructors.
 * Callable both with `new PostboxToolkit(options)` and directly as `PostboxToolkit(options)`.
 */
export function PostboxToolkit(options = {}) {
  if (!(this instanceof PostboxToolkit)) {
    return new PostboxToolkit(options);
  }

  const rootDir = resolve(options.rootDir || ROOT_DIR);
  this.rootDir = rootDir;
  this.metadata = new MetadataInspector({ rootDir, applicationId: options.applicationId });
  this.gradle = new GradleBuilder({ rootDir, inspector: this.metadata });
  this.adb = new AdbInstaller({ rootDir, inspector: this.metadata, builder: this.gradle });
  this.docker = new DockerPackager({
    rootDir,
    imageName: options.dockerImageName,
    exportDir: options.dockerExportDir
  });
  this.windowsBundle = new WindowsBundleBuilder({
    rootDir,
    bundleDir: options.windowsBundleDir,
    inspector: this.metadata
  });
  this.git = new GitCommitHelper({ rootDir });
}

PostboxToolkit.prototype.listLoadedConstructors = function listLoadedConstructors() {
  return Object.keys(loadedDependencies);
};

export default PostboxToolkit;
