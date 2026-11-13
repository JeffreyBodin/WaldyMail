import assert from 'node:assert/strict';
import { existsSync, mkdtempSync, readFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import test from 'node:test';

import PostboxToolkit, {
  AdbInstaller,
  DockerPackager,
  GitCommitHelper,
  GradleBuilder,
  MetadataInspector,
  WindowsBundleBuilder,
  loadedDependencies,
  MODULE_REGISTRY
} from '../index.mjs';

test('index.mjs loads every registered dependency inline as a callable constructor', () => {
  const expectedNames = Object.keys(MODULE_REGISTRY);
  assert.deepEqual(Object.keys(loadedDependencies), expectedNames);

  const exported = {
    MetadataInspector,
    GradleBuilder,
    AdbInstaller,
    DockerPackager,
    WindowsBundleBuilder,
    GitCommitHelper
  };

  for (const name of expectedNames) {
    const Ctor = exported[name];
    assert.equal(typeof Ctor, 'function', `${name} must be a function`);

    const withNew = new Ctor();
    const withoutNew = Ctor();
    assert.ok(withNew instanceof Ctor, `new ${name}() must return instance`);
    assert.ok(withoutNew instanceof Ctor, `${name}() must return instance`);
  }

  const toolkitWithNew = new PostboxToolkit();
  const toolkitDirect = PostboxToolkit();
  assert.ok(toolkitWithNew instanceof PostboxToolkit);
  assert.ok(toolkitDirect instanceof PostboxToolkit);
  assert.deepEqual(toolkitDirect.listLoadedConstructors(), expectedNames);
});

test('WindowsBundleBuilder stages project files, Setup-Postbox.cmd, and bundle-manifest.json', () => {
  const tempDir = mkdtempSync(join(tmpdir(), 'postbox-win-bundle-'));
  try {
    const builder = new WindowsBundleBuilder({ bundleDir: tempDir });
    const manifest = builder.prepare();

    assert.equal(manifest.applicationId, 'com.aistudio.postbox.v7k9qm');
    assert.equal(manifest.offlineOnly, true);
    assert.ok(existsSync(join(tempDir, 'Setup-Postbox.cmd')));
    assert.ok(existsSync(join(tempDir, 'bundle-manifest.json')));
    assert.ok(existsSync(join(tempDir, 'windows', 'install-postbox.ps1')));
    assert.ok(existsSync(join(tempDir, 'index.mjs')));

    const parsed = JSON.parse(readFileSync(join(tempDir, 'bundle-manifest.json'), 'utf8'));
    assert.equal(parsed.launcher, 'Setup-Postbox.cmd');
  } finally {
    rmSync(tempDir, { recursive: true, force: true });
  }
});
