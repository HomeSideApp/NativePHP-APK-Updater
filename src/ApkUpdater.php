<?php

namespace Homeside\ApkUpdater;

use Closure;
use InvalidArgumentException;
use RuntimeException;

/**
 * Self-hosted APK updates for NativePHP Mobile (Android).
 *
 * The package is deliberately dumb about policy: it downloads, verifies and
 * invokes the system installer. Deciding *whether* an update exists (fetching
 * a manifest, comparing version codes) stays in your app.
 *
 * Every call returns an array from the native side. Successful shapes:
 *
 *   download()  ['id' => '42', 'status' => 'enqueued', 'file_name' => 'update.apk']
 *   status()    ['id' => '42', 'status' => 'running', 'progress' => 37]
 *   install()   ['started' => 'true']
 *   clear()     ['removed' => 'true']
 *   openInstallSettings() ['opened' => 'true']
 *
 * Failures carry `error` (stable code) and `message`:
 *
 *   missing_url, download_failed, missing_id, status_failed, not_ready,
 *   checksum_mismatch, checksum_unreadable, unknown_sources_disabled,
 *   no_installer, clear_failed, settings_unavailable, unsupported_platform
 */
final class ApkUpdater
{
    /**
     * @param  (Closure(): bool)|null  $platformProbe
     *                                                 Lets a host (or the test suite) decide whether the native side exists,
     *                                                 instead of reaching for the framework's `isAndroid()` helper, which
     *                                                 needs a booted application.
     */
    public function __construct(
        private readonly ?Closure $platformProbe = null,
    ) {}

    /**
     * Enqueue an APK download through Android's DownloadManager.
     *
     * @param  array{file_name?: string, sha256?: string, title?: string}  $options
     * @return array<string, string>
     */
    public function download(string $url, array $options = []): array
    {
        if (trim($url) === '') {
            throw new InvalidArgumentException('The download URL cannot be empty.');
        }

        if (isset($options['sha256']) && ! $this->isSha256($options['sha256'])) {
            throw new InvalidArgumentException('The sha256 option must be a 64 character hex string.');
        }

        return $this->call('ApkUpdater.Download', array_filter([
            'url' => $url,
            'file_name' => $options['file_name'] ?? null,
            'sha256' => isset($options['sha256']) ? strtolower($options['sha256']) : null,
            'title' => $options['title'] ?? null,
        ], static fn (mixed $value): bool => $value !== null));
    }

    /**
     * @return array<string, string>
     */
    public function status(string|int $id): array
    {
        return $this->call('ApkUpdater.Status', ['id' => (string) $id]);
    }

    /**
     * @return array<string, string>
     */
    public function install(string|int $id): array
    {
        return $this->call('ApkUpdater.Install', ['id' => (string) $id]);
    }

    /**
     * @return array<string, string>
     */
    public function clear(string|int $id): array
    {
        return $this->call('ApkUpdater.Clear', ['id' => (string) $id]);
    }

    /**
     * Sends the user to "Install unknown apps" for this application, which is
     * where they have to grant the permission the installer needs.
     *
     * Es "disparar y olvidar": si el lado nativo no contesta (una app vieja sin
     * la función, un bridge que falló), devolver un error es mejor que tirar una
     * excepción desde el camino que ya está manejando otro error.
     *
     * @return array<string, string>
     */
    public function openInstallSettings(): array
    {
        return $this->callOrNothing('ApkUpdater.OpenSettings');
    }

    /**
     * True on an Android device where the native side exists.
     */
    public function isSupported(): bool
    {
        if ($this->platformProbe !== null) {
            return (bool) ($this->platformProbe)();
        }

        if (! function_exists('nativephp_call') || ! function_exists('isAndroid')) {
            return false;
        }

        try {
            return isAndroid();
        } catch (\Throwable) {
            // No booted application (CLI, queue worker without the facade).
            return false;
        }
    }

    /**
     * @param  array<string, mixed>  $payload
     * @return array<string, string>
     */
    private function call(string $function, array $payload = []): array
    {
        if (! $this->isSupported()) {
            return [
                'error' => 'unsupported_platform',
                'message' => 'In-app APK updates are only implemented for Android.',
            ];
        }

        $encoded = $payload === []
            ? '{}'
            : (string) json_encode($payload, JSON_UNESCAPED_SLASHES);

        $result = nativephp_call($function, $encoded);

        if (! is_string($result) || $result === '') {
            throw new RuntimeException("The native side returned nothing for {$function}.");
        }

        $decoded = json_decode($result, true);

        if (! is_array($decoded)) {
            throw new RuntimeException("The native side returned invalid JSON for {$function}.");
        }

        /** @var array<string, string> $normalized */
        $normalized = array_map(static fn (mixed $value): string => is_scalar($value)
            ? (string) $value
            : (string) json_encode($value), $decoded);

        return $normalized;
    }

    /**
     * @return array<string, string>
     */
    private function callOrNothing(string $function): array
    {
        try {
            return $this->call($function);
        } catch (RuntimeException) {
            return [
                'error' => 'no_response',
                'message' => 'The native side did not answer.',
            ];
        }
    }

    private function isSha256(string $value): bool
    {
        return preg_match('/^[a-f0-9]{64}$/i', $value) === 1;
    }
}
