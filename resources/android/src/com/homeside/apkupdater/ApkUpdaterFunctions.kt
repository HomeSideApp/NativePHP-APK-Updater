package com.homeside.apkupdater

import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.fragment.app.FragmentActivity
import com.nativephp.mobile.bridge.BridgeFunction
import java.io.File
import java.security.MessageDigest

/**
 * NativePHP Mobile bridge for self-hosted APK updates on Android.
 *
 * The flow is deliberately split in three calls so PHP stays in charge of the
 * policy (when to offer an update) while Android owns the mechanics:
 *
 *   1. `Download` enqueues the APK in the system DownloadManager. The system
 *      shows the progress notification, keeps the download alive when the app
 *      is backgrounded, and survives process death — no foreground service, no
 *      notification permission handling on our side.
 *   2. `Status` reports progress/state so the app can render its own UI.
 *   3. `Install` verifies the optional SHA-256 and hands the finished file to
 *      the package installer through an ACTION_VIEW intent with a temporary
 *      read grant.
 *
 * Nothing here decides *whether* an update exists: that is the app's manifest.
 */
object ApkUpdaterFunctions {

    private const val PREFS = "homeside_apk_updater"
    private const val MIME_APK = "application/vnd.android.package-archive"

    /** `ApkUpdater.Download` — params: url, file_name?, sha256?, title? */
    class Download(private val context: FragmentActivity) : BridgeFunction {
        override fun execute(parameters: Map<String, Any>): Map<String, Any> {
            val url = parameters["url"] as? String
            if (url.isNullOrBlank()) {
                return error("missing_url", "No URL was provided.")
            }

            val fileName = (parameters["file_name"] as? String)?.takeIf { it.isNotBlank() }
                ?: "update.apk"
            val sha256 = (parameters["sha256"] as? String)?.lowercase()?.takeIf { it.isNotBlank() }
            val title = (parameters["title"] as? String) ?: "Downloading update"

            return try {
                val request = DownloadManager.Request(Uri.parse(url))
                    .setTitle(title)
                    .setMimeType(MIME_APK)
                    .setNotificationVisibility(
                        DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                    )
                    .setDestinationInExternalFilesDir(
                        context,
                        Environment.DIRECTORY_DOWNLOADS,
                        fileName
                    )

                val manager =
                    context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                val id = manager.enqueue(request)

                remember(context, id, sha256, fileName)

                mapOf(
                    "id" to id.toString(),
                    "status" to "enqueued",
                    "file_name" to fileName
                )
            } catch (exception: Exception) {
                error("download_failed", exception.message ?: "Could not enqueue the download.")
            }
        }
    }

    /** `ApkUpdater.Status` — params: id */
    class Status(private val context: FragmentActivity) : BridgeFunction {
        override fun execute(parameters: Map<String, Any>): Map<String, Any> {
            val id = idOf(parameters) ?: return error("missing_id", "No download id was provided.")
            val manager =
                context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

            return try {
                manager.query(DownloadManager.Query().setFilterById(id)).use { cursor ->
                    if (cursor == null || !cursor.moveToFirst()) {
                        return mapOf("id" to id.toString(), "status" to "unknown")
                    }

                    val status = cursor.getInt(
                        cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
                    )
                    val downloaded = cursor.getLong(
                        cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                    )
                    val total = cursor.getLong(
                        cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                    )
                    val progress =
                        if (total > 0) ((downloaded * 100) / total).toInt() else 0

                    val result = mutableMapOf<String, Any>(
                        "id" to id.toString(),
                        "status" to statusName(status),
                        "progress" to progress.coerceIn(0, 100)
                    )

                    if (status == DownloadManager.STATUS_FAILED) {
                        val reason = cursor.getInt(
                            cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)
                        )
                        result["reason"] = reason.toString()
                    }

                    result
                }
            } catch (exception: Exception) {
                error("status_failed", exception.message ?: "Could not read the download status.")
            }
        }
    }

    /** `ApkUpdater.Install` — params: id */
    class Install(private val context: FragmentActivity) : BridgeFunction {
        override fun execute(parameters: Map<String, Any>): Map<String, Any> {
            val id = idOf(parameters) ?: return error("missing_id", "No download id was provided.")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                !context.packageManager.canRequestPackageInstalls()
            ) {
                return error(
                    "unknown_sources_disabled",
                    "This app is not allowed to install packages. Open the settings and enable it."
                )
            }

            val manager =
                context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val uri: Uri? = try {
                manager.getUriForDownloadedFile(id)
            } catch (exception: Exception) {
                null
            }

            if (uri == null) {
                return error("not_ready", "The APK is still downloading or was removed.")
            }

            verifyChecksum(context, id, uri)?.let { return it }

            return try {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, MIME_APK)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

                if (context !is Activity) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

                context.startActivity(intent)

                mapOf("started" to "true")
            } catch (exception: Exception) {
                error("no_installer", exception.message ?: "No package installer available.")
            }
        }
    }

    /** `ApkUpdater.Clear` — params: id */
    class Clear(private val context: FragmentActivity) : BridgeFunction {
        override fun execute(parameters: Map<String, Any>): Map<String, Any> {
            val id = idOf(parameters) ?: return error("missing_id", "No download id was provided.")

            return try {
                val manager =
                    context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                manager.remove(id)
                forget(context, id)
                mapOf("removed" to "true")
            } catch (exception: Exception) {
                error("clear_failed", exception.message ?: "Could not remove the download.")
            }
        }
    }

    /** `ApkUpdater.OpenSettings` — no params. */
    class OpenSettings(private val context: FragmentActivity) : BridgeFunction {
        override fun execute(parameters: Map<String, Any>): Map<String, Any> {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                return mapOf("opened" to "false", "reason" to "not_required")
            }

            return try {
                val intent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }

                context.startActivity(intent)

                mapOf("opened" to "true")
            } catch (exception: Exception) {
                error("settings_unavailable", exception.message ?: "Could not open the settings.")
            }
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun statusName(status: Int): String = when (status) {
        DownloadManager.STATUS_PENDING -> "pending"
        DownloadManager.STATUS_RUNNING -> "running"
        DownloadManager.STATUS_PAUSED -> "paused"
        DownloadManager.STATUS_SUCCESSFUL -> "successful"
        DownloadManager.STATUS_FAILED -> "failed"
        else -> "unknown"
    }

    private fun idOf(parameters: Map<String, Any>): Long? {
        val raw = parameters["id"] ?: return null

        return when (raw) {
            is Number -> raw.toLong()
            is String -> raw.toLongOrNull()
            else -> null
        }
    }

    private fun remember(context: Context, id: Long, sha256: String?, fileName: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        prefs.putString("file_$id", fileName)
        if (sha256 != null) {
            prefs.putString("sha_$id", sha256)
        }
        prefs.apply()
    }

    private fun forget(context: Context, id: Long) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove("file_$id")
            .remove("sha_$id")
            .apply()
    }

    /**
     * The installer verifies the APK signature, not its integrity: a truncated
     * or swapped file has to be caught here, before the user sees the prompt.
     */
    private fun verifyChecksum(
        context: Context,
        id: Long,
        uri: Uri
    ): Map<String, Any>? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val expected = prefs.getString("sha_$id", null) ?: return null

        val actual = try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val digest = MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(64 * 1024)
                var read = stream.read(buffer)
                while (read > 0) {
                    digest.update(buffer, 0, read)
                    read = stream.read(buffer)
                }
                digest.digest().joinToString("") { "%02x".format(it) }
            }
        } catch (exception: Exception) {
            null
        }

        if (actual == null) {
            return error("checksum_unreadable", "Could not read the downloaded file.")
        }

        if (!actual.equals(expected, ignoreCase = true)) {
            try {
                val manager =
                    context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                manager.remove(id)
            } catch (_: Exception) {
                // Best effort: the mismatch already aborts the install.
            }
            forget(context, id)

            return error("checksum_mismatch", "The downloaded file does not match the published checksum.")
        }

        return null
    }

    private fun error(code: String, message: String): Map<String, Any> =
        mapOf("error" to code, "message" to message)
}
