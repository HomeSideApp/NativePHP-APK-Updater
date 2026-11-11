package com.homeside.apkupdater

/**
 * Estado de una descarga, sin depender de Android.
 *
 * La capa nativa traduce las constantes de `DownloadManager` a este enum, así
 * que la parte que decide qué se le informa a PHP se puede testear en una JVM
 * común, sin emulador ni Robolectric.
 */
enum class DownloadState(val wire: String) {
    Pending("pending"),
    Running("running"),
    Paused("paused"),
    Successful("successful"),
    Failed("failed"),
    Unknown("unknown"),
}

/**
 * Reglas puras del plugin: nada acá toca `Context`, `Intent` ni cursores.
 *
 * Todo lo que se puede equivocar en silencio —un porcentaje dividido por cero,
 * un id que llega como string, un checksum con mayúsculas, un nombre de archivo
 * con `../`— vive en este archivo y tiene test propio.
 */
object ApkUpdaterLogic {

    fun stateName(state: DownloadState): String = state.wire

    /**
     * Porcentaje 0..100. Total desconocido (0 o negativo) se reporta como 0 en
     * vez de dividir por cero; pasado el 100% se recorta.
     */
    fun progress(downloaded: Long, total: Long): Int {
        if (total <= 0L || downloaded <= 0L) {
            return 0
        }

        return ((downloaded.toDouble() / total.toDouble()) * 100).toInt().coerceIn(0, 100)
    }

    /**
     * Los ids llegan como número o como string según cómo los serialice el
     * puente; cualquier otra cosa (o un id no positivo) se rechaza.
     */
    fun parseId(raw: Any?): Long? {
        val id = when (raw) {
            // JSON puede entregar 42 como Double: se acepta si es entero, y se
            // rechaza 3.5 en vez de truncarlo a 3 en silencio.
            is Number -> raw.toLong().takeIf { candidate -> raw.toDouble() == candidate.toDouble() }
            is String -> raw.trim().toLongOrNull()
            else -> null
        } ?: return null

        return if (id > 0L) id else null
    }

    fun isValidSha256(value: String?): Boolean {
        val candidate = value?.trim() ?: return false

        return candidate.length == 64 && candidate.all { character ->
            character.isDigit() || character.lowercaseChar() in 'a'..'f'
        }
    }

    /**
     * Comparación tolerante a mayúsculas y espacios, que es como llegan los
     * hashes desde un manifiesto editado a mano.
     */
    fun checksumMatches(actual: String?, expected: String?): Boolean {
        if (actual == null || expected == null) {
            return false
        }

        return actual.trim().equals(expected.trim(), ignoreCase = true)
    }

    fun toHex(bytes: ByteArray): String = bytes.joinToString(separator = "") { byte ->
        "%02x".format(byte)
    }

    /**
     * El nombre del archivo viene de un manifiesto remoto, así que se trata como
     * entrada no confiable: sin rutas, sin caracteres raros, siempre `.apk`.
     */
    fun safeFileName(raw: String?, fallback: String = "update.apk"): String {
        val candidate = raw
            ?.trim()
            ?.substringAfterLast('/')
            ?.substringAfterLast('\\')
            ?.takeIf { it.isNotEmpty() && it != "." && it != ".." }

        if (candidate == null) {
            return fallback
        }

        val cleaned = candidate
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .trimStart('.')

        if (cleaned.isEmpty()) {
            return fallback
        }

        // El límite se aplica al nombre final, dejando lugar para ".apk".
        val maxLength = 120
        val base = cleaned
            .removeSuffix(".apk")
            .removeSuffix(".APK")
            .take(maxLength - 4)
            .trimEnd('.', '_', '-')

        return if (base.isEmpty()) fallback else "$base.apk"
    }
}
