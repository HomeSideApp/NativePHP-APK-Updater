<?php

namespace Homeside\ApkUpdater\Facades;

use Homeside\ApkUpdater\ApkUpdater as Updater;
use Illuminate\Support\Facades\Facade;

/**
 * @method static array<string, string> download(string $url, array $options = [])
 * @method static array<string, string> status(string|int $id)
 * @method static array<string, string> install(string|int $id)
 * @method static array<string, string> clear(string|int $id)
 * @method static array<string, string> openInstallSettings()
 * @method static bool isSupported()
 *
 * @see Updater
 */
final class ApkUpdater extends Facade
{
    protected static function getFacadeAccessor(): string
    {
        return Updater::class;
    }
}
