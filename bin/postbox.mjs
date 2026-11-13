#!/usr/bin/env node
import {
  AdbInstaller,
  DockerPackager,
  GitCommitHelper,
  GradleBuilder,
  MetadataInspector,
  PostboxToolkit,
  WindowsBundleBuilder
} from '../index.mjs';

function printHelp() {
  console.log(`Postbox Offline Mail - Local Build, Bundle & Install CLI (No Server / No Hosting)

Usage:
  postbox info                          Show applicationId and local APK/AAB artifact status
  postbox build [debug|release|bundle]  Build offline APK or Play AAB via Gradle
  postbox install [apkPath]             Install built APK onto a connected Android device via ADB
  postbox install-wsa [apkPath]         Install built APK into Windows Subsystem for Android
  postbox prepare-windows [outDir]      Stage self-contained Windows application/installer bundle
  postbox docker [commands|export]      Print or execute zero-server Docker artifact export
  postbox git [status]                  Inspect granular Git status and commit log
  postbox test                          Run local JVM/Robolectric unit tests via Gradle
  postbox modules                       List inline-loaded constructor modules from index.mjs
  postbox help                          Show this help message
`);
}

const [, , command = 'help', arg, extraArg] = process.argv;

try {
  switch (command) {
    case 'info': {
      const inspector = new MetadataInspector();
      inspector.printReport();
      break;
    }
    case 'build': {
      const builder = new GradleBuilder();
      builder.build(arg || 'debug');
      break;
    }
    case 'install': {
      const installer = new AdbInstaller();
      installer.installAndroid(arg);
      break;
    }
    case 'install-wsa': {
      const installer = new AdbInstaller();
      installer.installWsa(arg);
      break;
    }
    case 'prepare-windows': {
      const bundleBuilder = new WindowsBundleBuilder(arg ? { bundleDir: arg } : {});
      bundleBuilder.prepare();
      break;
    }
    case 'docker': {
      const packager = new DockerPackager();
      if (arg === 'export') {
        packager.exportArtifacts(extraArg || ':app:assembleDebug');
      } else {
        console.log(JSON.stringify(packager.getCommands(extraArg || ':app:assembleDebug'), null, 2));
      }
      break;
    }
    case 'git': {
      const gitHelper = new GitCommitHelper();
      gitHelper.status();
      break;
    }
    case 'test': {
      const builder = new GradleBuilder();
      builder.runTests();
      break;
    }
    case 'modules': {
      const toolkit = new PostboxToolkit();
      console.log('Loaded inline constructor modules:', toolkit.listLoadedConstructors().join(', '));
      break;
    }
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
} catch (err) {
  console.error(err.message);
  process.exit(1);
}
