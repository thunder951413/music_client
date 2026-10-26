package com.eddyizm.tempus.github.utils

import android.app.Activity
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.edit
import com.eddyizm.tempus.App
import com.eddyizm.tempus.R
import java.io.File

/** Shares downloaded update APKs with the system installer. */
class UpdateFileProvider : FileProvider()

/**
 * Downloads a release APK with the system DownloadManager and hands it to the package
 * installer once it has finished. The running download is remembered, so the settings screen
 * can show its progress and install it even if the completion broadcast was missed.
 */
object ApkUpdater {
    private const val APK_MIME = "application/vnd.android.package-archive"
    private const val KEY_ID = "update_download_id"
    private const val KEY_VERSION = "update_download_version"
    private const val KEY_INSTALLED = "update_download_install_launched"

    enum class Status { PENDING, RUNNING, PAUSED, SUCCESSFUL, FAILED }

    /** A snapshot of the update download; [total] is -1 until the server reports a size. */
    data class Progress(val version: String, val status: Status, val downloaded: Long, val total: Long, val reason: Int) {
        val fraction: Float get() = if (total > 0) (downloaded.toFloat() / total).coerceIn(0f, 1f) else -1f
    }

    @JvmStatic
    fun start(activity: Activity, url: String, version: String) {
        val context = activity.applicationContext

        if (!context.packageManager.canRequestPackageInstalls()) {
            Toast.makeText(activity, R.string.update_allow_install, Toast.LENGTH_LONG).show()
            activity.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            )
            return
        }

        val downloadManager = context.getSystemService(DownloadManager::class.java)
        // A second tap replaces the earlier download instead of running two side by side.
        prefs().getLong(KEY_ID, -1L).takeIf { it >= 0 }?.let { downloadManager.remove(it) }

        val file = apkFile(context, version)
        if (file.exists()) file.delete()

        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(context.getString(R.string.update_download_title, version))
            .setMimeType(APK_MIME)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, file.name)
        val downloadId = downloadManager.enqueue(request)
        prefs().edit {
            putLong(KEY_ID, downloadId)
            putString(KEY_VERSION, version)
            putBoolean(KEY_INSTALLED, false)
        }

        ContextCompat.registerReceiver(context, object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context, intent: Intent) {
                if (intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) != downloadId) return
                receiverContext.unregisterReceiver(this)
                if (progress(receiverContext)?.status == Status.SUCCESSFUL) {
                    installDownloaded(receiverContext)
                } else {
                    Toast.makeText(receiverContext, R.string.update_download_failed, Toast.LENGTH_LONG).show()
                }
            }
        }, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), ContextCompat.RECEIVER_EXPORTED)

        Toast.makeText(activity, R.string.update_downloading, Toast.LENGTH_SHORT).show()
    }

    /** The current update download, or null when there is none (or the system dropped it). */
    @JvmStatic
    fun progress(context: Context): Progress? {
        val id = prefs().getLong(KEY_ID, -1L)
        if (id < 0) return null
        val version = prefs().getString(KEY_VERSION, "") ?: ""
        val downloadManager = context.getSystemService(DownloadManager::class.java)
        downloadManager.query(DownloadManager.Query().setFilterById(id)).use { cursor ->
            if (!cursor.moveToFirst()) return null
            val status = when (cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))) {
                DownloadManager.STATUS_RUNNING -> Status.RUNNING
                DownloadManager.STATUS_PAUSED -> Status.PAUSED
                DownloadManager.STATUS_SUCCESSFUL -> Status.SUCCESSFUL
                DownloadManager.STATUS_FAILED -> Status.FAILED
                else -> Status.PENDING
            }
            return Progress(
                version = version,
                status = status,
                downloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)),
                total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)),
                reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)),
            )
        }
    }

    /** Opens the installer for the finished download, once, unless [force] asks again. */
    @JvmStatic
    @JvmOverloads
    fun installDownloaded(context: Context, force: Boolean = false) {
        if (!force && prefs().getBoolean(KEY_INSTALLED, false)) return
        val version = prefs().getString(KEY_VERSION, null) ?: return
        val file = apkFile(context, version)
        if (!file.exists()) return
        prefs().edit { putBoolean(KEY_INSTALLED, true) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates.provider", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, APK_MIME)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /** Forgets a finished or failed download so the progress row goes away. */
    @JvmStatic
    fun clear() {
        prefs().edit {
            remove(KEY_ID)
            remove(KEY_VERSION)
            remove(KEY_INSTALLED)
        }
    }

    private fun apkFile(context: Context, version: String) =
        File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "music_client-$version.apk")

    private fun prefs() = App.getInstance().preferences
}
