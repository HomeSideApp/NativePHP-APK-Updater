<?php

namespace Homeside\ApkUpdater\Tests;

use Homeside\ApkUpdater\Testing\ApkUpdaterTestMacros;
use Native\Mobile\Testing\FakeBridge;
use Native\Mobile\Testing\TestableComponent;
use PHPUnit\Framework\TestCase;

/**
 * Las macros son la superficie que consumen las apps, así que lo que se prueba
 * es el contrato: que existan, que respondan lo que PHP espera y que sean
 * idempotentes (el provider puede bootear más de una vez en la misma suite).
 */
final class ApkUpdaterTestMacrosTest extends TestCase
{
    protected function setUp(): void
    {
        parent::setUp();

        ApkUpdaterTestMacros::register();
    }

    public function test_it_registers_every_macro(): void
    {
        foreach (ApkUpdaterTestMacros::macros()['bridge'] as $macro) {
            $this->assertTrue(FakeBridge::hasMacro($macro), "Falta la macro de bridge [{$macro}].");
        }

        foreach (ApkUpdaterTestMacros::macros()['component'] as $macro) {
            $this->assertTrue(TestableComponent::hasMacro($macro), "Falta la macro de componente [{$macro}].");
        }
    }

    public function test_registering_twice_is_harmless(): void
    {
        ApkUpdaterTestMacros::register();
        ApkUpdaterTestMacros::register();

        $this->assertTrue(FakeBridge::hasMacro('withApkUpdaterDownloaded'));
    }

    public function test_the_download_macro_answers_what_php_reads(): void
    {
        $bridge = new FakeBridge;
        $bridge->withApkUpdaterDownloaded(id: '7', fileName: 'homeside-0.2.0.apk');

        $response = json_decode((string) $bridge->call('ApkUpdater.Download', '{"url":"https://example.test/app.apk"}'), true);

        $this->assertSame('7', $response['id']);
        $this->assertSame('enqueued', $response['status']);
        $this->assertSame('homeside-0.2.0.apk', $response['file_name']);
    }

    public function test_the_status_macro_scripts_progress(): void
    {
        $bridge = new FakeBridge;
        $bridge->withApkUpdaterStatus(status: 'running', progress: 42, id: '9');

        $response = json_decode((string) $bridge->call('ApkUpdater.Status', '{"id":"9"}'), true);

        $this->assertSame('running', $response['status']);
        $this->assertSame(42, $response['progress']);
    }

    public function test_the_error_macros_script_failures(): void
    {
        $download = new FakeBridge;
        $download->withApkUpdaterDownloadError();
        $this->assertSame(
            'download_failed',
            json_decode((string) $download->call('ApkUpdater.Download'), true)['error'],
        );

        $install = new FakeBridge;
        $install->withApkUpdaterInstallError();
        $this->assertSame(
            'unknown_sources_disabled',
            json_decode((string) $install->call('ApkUpdater.Install'), true)['error'],
        );
    }

    public function test_the_assertion_macros_read_the_recorded_calls(): void
    {
        // Se valida el contrato con el FakeBridge: el filtro recibe el payload
        // decodificado, que es lo que usan nuestras macros.
        $bridge = new FakeBridge;
        $bridge->call('ApkUpdater.Download', '{"url":"https://example.test/app.apk"}');

        $bridge->assertCalled('ApkUpdater.Download', static fn (array $params): bool => str_contains((string) $params['url'], 'example.test'));

        $this->addToAssertionCount(1);
    }
}
