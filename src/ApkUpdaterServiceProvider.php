<?php

namespace Homeside\ApkUpdater;

use Illuminate\Support\ServiceProvider;

final class ApkUpdaterServiceProvider extends ServiceProvider
{
    public function register(): void
    {
        $this->app->singleton(ApkUpdater::class, static fn (): ApkUpdater => new ApkUpdater);
    }
}
