# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses
[semantic versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0] - 2026-11-11

### Added

- `ApkUpdater::download()` — enqueues an APK in Android's `DownloadManager`,
  with optional file name, title and SHA-256.
- `ApkUpdater::status()` — progress and state of a download.
- `ApkUpdater::install()` — verifies the checksum and opens the system package
  installer with a temporary read grant on the finished file.
- `ApkUpdater::clear()` and `ApkUpdater::openInstallSettings()`.
- `REQUEST_INSTALL_PACKAGES` declared through the plugin manifest, so the
  compiler merges it into the app.
- Errors returned as `error`/`message` pairs instead of exceptions.
- `composer.json` declares `"type": "nativephp-plugin"`. Anything else makes
  NativePHP's plugin discovery ignore the package: it installs and autoloads,
  but its Kotlin never reaches the build.
- Testing helpers for host apps: `withApkUpdaterDownloaded()`,
  `withApkUpdaterStatus()`, `withApkUpdaterDownloadError()`,
  `withApkUpdaterInstallError()` and the `assertApkUpdater*` / `assertNoApkUpdaterCalls`
  macros.
- `ApkUpdaterLogic`: every decision in the Android layer (percentage maths, id
  parsing, checksum comparison, filename sanitising, state mapping) as pure
  Kotlin, with a JVM test module under `tests/native` that needs no emulator.
