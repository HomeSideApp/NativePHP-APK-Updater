# NativePHP APK Updater (Android)

Self-hosted APK updates for [NativePHP Mobile](https://nativephp.com) apps: the
device downloads your APK, verifies its checksum and hands it to the system
installer. **No Play Store, no Bifrost, no third-party service.**

```php
use Homeside\ApkUpdater\Facades\ApkUpdater;

$download = ApkUpdater::download($update['apk_url'], [
    'sha256' => $update['sha256'],
    'file_name' => "homeside-{$update['version']}.apk",
]);

// later, from a poll or a button
$status = ApkUpdater::status($download['id']);

if ($status['status'] === 'successful') {
    ApkUpdater::install($download['id']);
}
```

Built and maintained by the [HomeSide](https://github.com/HomeSideApp) project,
an open-source home management app, and released under MIT so other NativePHP
apps can stop sending people to a browser to update.

## Why

Android has no "install this APK" API that a sideloaded app can call silently.
What it does have is a system installer and a system downloader, and both are
perfectly usable — the pain is the plumbing: permissions, a download that
survives the app being backgrounded, a progress UI, a content URI the installer
is allowed to read, and a checksum check so a truncated file never reaches the
user.

This package is that plumbing, in one Kotlin object and a thin PHP wrapper.

## Requirements

- `nativephp/mobile` **4.6+**
- Android only (see [Limitations](#limitations))
- PHP 8.2+

## Installation

```bash
composer require homeside/apk-updater
php artisan vendor:publish --tag=nativephp-plugins-provider   # once per app
php artisan native:plugin:register homeside/apk-updater
php artisan native:plugin:validate
```

> **Careful with `--force`.** `vendor:publish --tag=nativephp-plugins-provider`
> overwrites `app/Providers/NativeServiceProvider.php`. If you already have
> plugins registered (for example `nativephp/mobile-ui`), publish **without**
> `--force`, or re-register them afterwards — otherwise they silently stop being
> compiled and your screens lose their elements.

Rebuild the app (`php artisan native:run` or `native:package`): the plugin ships
Kotlin, so it is compiled into the APK.

`nativephp.json` declares the `REQUEST_INSTALL_PACKAGES` permission; the plugin
compiler merges it into the app's manifest. Android 8+ still requires the user
to allow *install unknown apps* for your application — the app cannot grant that
to itself. `ApkUpdater::openInstallSettings()` sends them straight to the right
settings screen, and `install()` returns `unknown_sources_disabled` while it is
off.

## Usage

The package never decides whether an update exists. Your app owns that policy —
usually a JSON manifest published next to the APK:

```json
{
  "version": "0.2.0",
  "version_code": 2,
  "apk_url": "https://example.com/releases/homeside-0.2.0.apk",
  "sha256": "…64 hex chars…"
}
```

Then:

```php
// 1. start the download (async, the system shows the notification)
$download = ApkUpdater::download($manifest['apk_url'], [
    'sha256' => $manifest['sha256'],
    'file_name' => 'homeside-'.$manifest['version'].'.apk',
    'title' => 'HomeSide update',
]);

// 2. poll it from your own screen
$status = ApkUpdater::status($download['id']);
// ['status' => 'running', 'progress' => 42]

// 3. when it finishes, verify + open the installer
$result = ApkUpdater::install($download['id']);

if (($result['error'] ?? null) === 'unknown_sources_disabled') {
    ApkUpdater::openInstallSettings();
}
```

`install()` re-checks the SHA-256 you passed to `download()` and refuses a file
that does not match, deleting it before the installer ever sees it.

### API

| Method | Native function | Returns |
|---|---|---|
| `download(string $url, array $options = [])` | `ApkUpdater.Download` | `id`, `status` (`enqueued`), `file_name` |
| `status(string\|int $id)` | `ApkUpdater.Status` | `status` (`pending`/`running`/`paused`/`successful`/`failed`/`unknown`), `progress` (0–100), `reason` on failure |
| `install(string\|int $id)` | `ApkUpdater.Install` | `started` |
| `clear(string\|int $id)` | `ApkUpdater.Clear` | `removed` |
| `openInstallSettings()` | `ApkUpdater.OpenSettings` | `opened` |
| `isSupported()` | — | `true` on Android |

`$options`: `file_name`, `sha256`, `title`.

Errors come back as data, never as exceptions, so a failed install can be shown
to the user:

| `error` | Meaning |
|---|---|
| `missing_url` | empty URL |
| `download_failed` | DownloadManager refused the request |
| `missing_id` | no download id in the call |
| `not_ready` | the file is still downloading or was removed |
| `checksum_mismatch` | the file does not match the published SHA-256 (it is deleted) |
| `checksum_unreadable` | the downloaded file could not be read |
| `unknown_sources_disabled` | the user must allow installs for this app |
| `no_installer` | no activity could handle the APK intent |
| `unsupported_platform` | not Android |

Invalid arguments (empty URL, non-hex checksum) throw `InvalidArgumentException`
— those are programming errors, not user-facing states.

## How it works

1. `Download` enqueues a `DownloadManager` request with
   `VISIBILITY_VISIBLE_NOTIFY_COMPLETED`, so Android owns progress, retries and
   the notification, and keeps downloading when the app is backgrounded.
2. `Status` queries `DownloadManager` by id.
3. `Install` asks `DownloadManager` for the finished file's content URI, grants
   the installer a temporary read permission and starts `ACTION_VIEW` with
   `application/vnd.android.package-archive`.

No `FileProvider`, no foreground service, no custom notification: three things
that usually make this plugin-shaped problem bigger than it is.

## Security notes

- **Keep your keystore stable.** Android refuses to update an app signed with a
  different key; the update becomes "uninstall and start over".
- **Always publish a checksum.** The installer validates the APK signature, not
  the download's integrity; a truncated file fails late and confusingly.
- Serve APKs over HTTPS. The download URL is handed to the system downloader.

## Limitations

- **Android only.** iOS has no equivalent API: App Store / TestFlight update the
  app for you, and there is nothing an app can download and install.
- **No silent install.** Even with the permission, Android shows its own
  confirmation screen. That is by design and not bypassable.
- The download is managed by the system, so progress UI in your app is polling
  (`status()`), not a stream of events.

## Development

```bash
composer install
composer test        # PHPUnit
composer lint        # Pint
```

The suite exercises the PHP side against a fake `nativephp_call()`, so it runs
without Laravel or a device.

## License

MIT. See [LICENSE](LICENSE).
