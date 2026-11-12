<?php

namespace Homeside\ApkUpdater\Tests;

use Homeside\ApkUpdater\ApkUpdater;
use InvalidArgumentException;
use PHPUnit\Framework\TestCase;
use RuntimeException;

final class ApkUpdaterTest extends TestCase
{
    private ApkUpdater $updater;

    protected function setUp(): void
    {
        parent::setUp();

        $GLOBALS['apk_updater_calls'] = [];
        $GLOBALS['apk_updater_response'] = null;
        $GLOBALS['apk_updater_android'] = true;

        // La plataforma se inyecta: el helper `isAndroid()` del core necesita
        // una aplicación Laravel arrancada.
        $this->updater = new ApkUpdater(fn (): bool => (bool) $GLOBALS['apk_updater_android']);
    }

    public function test_it_downloads_with_the_expected_payload(): void
    {
        $GLOBALS['apk_updater_response'] = json_encode([
            'id' => 42,
            'status' => 'enqueued',
            'file_name' => 'homeside-0.2.0.apk',
        ]);

        $result = $this->updater->download('https://example.test/app.apk', [
            'sha256' => strtoupper(str_repeat('a', 64)),
            'file_name' => 'homeside-0.2.0.apk',
            'title' => 'HomeSide',
        ]);

        $this->assertSame('42', $result['id']);
        $this->assertSame('enqueued', $result['status']);

        $call = $this->lastCall();

        $this->assertSame('ApkUpdater.Download', $call['function']);
        $this->assertSame([
            'url' => 'https://example.test/app.apk',
            'file_name' => 'homeside-0.2.0.apk',
            'sha256' => str_repeat('a', 64),
            'title' => 'HomeSide',
        ], json_decode((string) $call['payload'], true));
    }

    public function test_optional_fields_are_left_out(): void
    {
        $this->updater->download('https://example.test/app.apk');

        $this->assertSame(
            ['url' => 'https://example.test/app.apk'],
            json_decode((string) $this->lastCall()['payload'], true),
        );
    }

    public function test_it_rejects_an_empty_url(): void
    {
        $this->expectException(InvalidArgumentException::class);

        $this->updater->download('   ');
    }

    public function test_it_rejects_a_malformed_checksum(): void
    {
        $this->expectException(InvalidArgumentException::class);

        $this->updater->download('https://example.test/app.apk', ['sha256' => 'not-a-hash']);
    }

    public function test_status_sends_the_download_id(): void
    {
        $GLOBALS['apk_updater_response'] = json_encode([
            'id' => 7,
            'status' => 'running',
            'progress' => 37,
        ]);

        $result = $this->updater->status(7);

        $this->assertSame('running', $result['status']);
        $this->assertSame('37', $result['progress']);
        $this->assertSame('ApkUpdater.Status', $this->lastCall()['function']);
        $this->assertSame(['id' => '7'], json_decode((string) $this->lastCall()['payload'], true));
    }

    public function test_install_reports_an_error_without_throwing(): void
    {
        $GLOBALS['apk_updater_response'] = json_encode([
            'error' => 'unknown_sources_disabled',
            'message' => 'Enable unknown sources.',
        ]);

        $result = $this->updater->install('7');

        $this->assertSame('unknown_sources_disabled', $result['error']);
        $this->assertSame('Enable unknown sources.', $result['message']);
    }

    public function test_clear_and_settings_use_their_own_functions(): void
    {
        $this->updater->clear('7');
        $this->updater->openInstallSettings();

        $calls = $GLOBALS['apk_updater_calls'];

        $this->assertSame('ApkUpdater.Clear', $calls[0]['function']);
        $this->assertSame('ApkUpdater.OpenSettings', $calls[1]['function']);
        $this->assertSame('{}', $calls[1]['payload']);
    }

    public function test_opening_the_settings_never_throws(): void
    {
        $GLOBALS['apk_updater_response'] = '';

        $result = $this->updater->openInstallSettings();

        $this->assertSame('no_response', $result['error']);
        $this->assertSame('ApkUpdater.OpenSettings', $this->lastCall()['function']);
    }

    public function test_the_platform_check_reports_android_only(): void
    {
        $this->assertTrue($this->updater->isSupported());

        $GLOBALS['apk_updater_android'] = false;

        $this->assertFalse($this->updater->isSupported());
    }

    public function test_on_another_platform_it_answers_instead_of_crashing(): void
    {
        $GLOBALS['apk_updater_android'] = false;

        $result = $this->updater->install('7');

        $this->assertSame('unsupported_platform', $result['error']);
        $this->assertSame([], $GLOBALS['apk_updater_calls']);
    }

    public function test_an_empty_native_response_is_an_error(): void
    {
        $GLOBALS['apk_updater_response'] = '';

        $this->expectException(RuntimeException::class);

        $this->updater->status(1);
    }

    public function test_invalid_json_from_the_native_side_is_an_error(): void
    {
        $GLOBALS['apk_updater_response'] = 'not json';

        $this->expectException(RuntimeException::class);

        $this->updater->status(1);
    }

    /**
     * @return array{function: string, payload: ?string}
     */
    private function lastCall(): array
    {
        $calls = $GLOBALS['apk_updater_calls'];

        $this->assertNotEmpty($calls, 'No native call was made.');

        return $calls[array_key_last($calls)];
    }
}
