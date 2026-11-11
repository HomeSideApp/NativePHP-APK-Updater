<?php

namespace Homeside\ApkUpdater;

use Homeside\ApkUpdater\Testing\ApkUpdaterTestMacros;
use Illuminate\Support\ServiceProvider;

final class ApkUpdaterServiceProvider extends ServiceProvider
{
    public function register(): void
    {
        $this->app->singleton(ApkUpdater::class, static fn (): ApkUpdater => new ApkUpdater);
    }

    public function boot(): void
    {
        // Sólo hace algo cuando las clases de testing de NativePHP están
        // cargadas; en producción el guard las saltea.
        ApkUpdaterTestMacros::register();
    }
}
