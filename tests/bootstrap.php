<?php

declare(strict_types=1);

/**
 * The package talks to the device through `nativephp_call()`. These shims let
 * the suite exercise the PHP side without booting Laravel or a device: each
 * test scripts the next native response and inspects what was sent.
 */
$GLOBALS['apk_updater_calls'] = [];
$GLOBALS['apk_updater_response'] = null;
$GLOBALS['apk_updater_android'] = true;

if (! function_exists('nativephp_call')) {
    function nativephp_call(string $function, ?string $payload = null): string|false
    {
        $GLOBALS['apk_updater_calls'][] = [
            'function' => $function,
            'payload' => $payload,
        ];

        $response = $GLOBALS['apk_updater_response'];

        return $response === null ? '{}' : (string) $response;
    }
}

require dirname(__DIR__).'/vendor/autoload.php';
