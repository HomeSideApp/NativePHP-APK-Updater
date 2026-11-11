<?php

namespace Homeside\ApkUpdater\Testing;

use Native\Mobile\Testing\FakeBridge;
use Native\Mobile\Testing\TestableComponent;

/**
 * Helpers para que las apps testeen su flujo de actualización sin dispositivo.
 *
 * Se registran solos: `ApkUpdaterServiceProvider` llama a `register()` en
 * `boot()`, y el guard de `class_exists` deja el paquete inerte en producción.
 *
 * ```php
 * Native::fakeBridge()->withApkUpdaterDownloaded(id: '7');
 *
 * Native::test(Profile::class)
 *     ->press('Descargar actualización')
 *     ->assertApkUpdaterDownloaded('https://example.test/app.apk');
 * ```
 */
final class ApkUpdaterTestMacros
{
    /**
     * @var list<string>
     */
    private const BRIDGE_MACROS = [
        'withApkUpdaterDownloaded',
        'withApkUpdaterStatus',
        'withApkUpdaterDownloadError',
        'withApkUpdaterInstallError',
    ];

    /**
     * @var list<string>
     */
    private const COMPONENT_MACROS = [
        'assertApkUpdaterDownloaded',
        'assertApkUpdaterInstalled',
        'assertApkUpdaterSettingsOpened',
        'assertNoApkUpdaterCalls',
    ];

    public static function register(): void
    {
        if (! class_exists(FakeBridge::class) || ! class_exists(TestableComponent::class)) {
            return;
        }

        if (FakeBridge::hasMacro('withApkUpdaterDownloaded')) {
            return;
        }

        self::registerBridgeMacros();
        self::registerComponentMacros();
    }

    /**
     * Nombres registrados, para que la app pueda comprobar que las macros
     * existen sin llamarlas.
     *
     * @return array{bridge: list<string>, component: list<string>}
     */
    public static function macros(): array
    {
        return [
            'bridge' => self::BRIDGE_MACROS,
            'component' => self::COMPONENT_MACROS,
        ];
    }

    private static function registerBridgeMacros(): void
    {
        FakeBridge::macro('withApkUpdaterDownloaded', function (
            string $id = '42',
            string $fileName = 'update.apk',
        ): FakeBridge {
            /** @var FakeBridge $this */
            return $this->respondTo('ApkUpdater.Download', [
                'id' => $id,
                'status' => 'enqueued',
                'file_name' => $fileName,
            ]);
        });

        FakeBridge::macro('withApkUpdaterStatus', function (
            string $status = 'successful',
            int $progress = 100,
            string $id = '42',
        ): FakeBridge {
            /** @var FakeBridge $this */
            return $this->respondTo('ApkUpdater.Status', [
                'id' => $id,
                'status' => $status,
                'progress' => $progress,
            ]);
        });

        FakeBridge::macro('withApkUpdaterDownloadError', function (
            string $error = 'download_failed',
            string $message = 'Could not enqueue the download.',
        ): FakeBridge {
            /** @var FakeBridge $this */
            return $this->respondTo('ApkUpdater.Download', [
                'error' => $error,
                'message' => $message,
            ]);
        });

        FakeBridge::macro('withApkUpdaterInstallError', function (
            string $error = 'unknown_sources_disabled',
            string $message = 'Enable unknown sources first.',
        ): FakeBridge {
            /** @var FakeBridge $this */
            return $this->respondTo('ApkUpdater.Install', [
                'error' => $error,
                'message' => $message,
            ]);
        });
    }

    private static function registerComponentMacros(): void
    {
        TestableComponent::macro('assertApkUpdaterDownloaded', function (?string $url = null): TestableComponent {
            /** @var TestableComponent $this */
            $this->bridge()->assertCalled('ApkUpdater.Download', static function (array $params) use ($url): bool {
                return $url === null || str_contains((string) ($params['url'] ?? ''), $url);
            });

            return $this;
        });

        TestableComponent::macro('assertApkUpdaterInstalled', function (?string $id = null): TestableComponent {
            /** @var TestableComponent $this */
            $this->bridge()->assertCalled('ApkUpdater.Install', static function (array $params) use ($id): bool {
                return $id === null || (string) ($params['id'] ?? '') === $id;
            });

            return $this;
        });

        TestableComponent::macro('assertApkUpdaterSettingsOpened', function (): TestableComponent {
            /** @var TestableComponent $this */
            $this->bridge()->assertCalled('ApkUpdater.OpenSettings');

            return $this;
        });

        TestableComponent::macro('assertNoApkUpdaterCalls', function (): TestableComponent {
            /** @var TestableComponent $this */
            foreach (['ApkUpdater.Download', 'ApkUpdater.Status', 'ApkUpdater.Install'] as $function) {
                $this->bridge()->assertNotCalled($function);
            }

            return $this;
        });
    }
}
