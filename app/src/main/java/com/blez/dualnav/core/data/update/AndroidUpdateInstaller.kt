package com.blez.dualnav.core.data.update

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.blez.dualnav.R
import com.blez.dualnav.core.domain.model.AppUpdate
import com.blez.dualnav.core.domain.repository.UpdateInstaller
import com.blez.dualnav.core.domain.util.DataError
import com.blez.dualnav.core.domain.util.Logger
import com.blez.dualnav.core.domain.util.Result
import kotlinx.coroutines.delay
import java.io.File

class AndroidUpdateInstaller(
    private val context: Context,
    private val logger: Logger
) : UpdateInstaller {

    override fun canInstallPackages(): Boolean = context.packageManager.canRequestPackageInstalls()

    override fun requestInstallPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            "package:${context.packageName}".toUri()
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    override suspend fun downloadAndInstall(update: AppUpdate): Result<Unit, DataError> {
        val downloadUrl = update.downloadUrl ?: run {
            openReleasePage(update)
            return Result.Success(Unit)
        }

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val fileName = apkFileName(update)
        val request = DownloadManager.Request(downloadUrl.toUri())
            .setTitle(fileName)
            .setDescription(context.getString(R.string.settings_update_download_notification_description))
            // The system renders its own "downloading…" progress notification, followed by a
            // "download complete" one, for as long as this stays VISIBLE_NOTIFY_COMPLETED.
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, DOWNLOAD_SUBDIR, fileName)
        val downloadId = downloadManager.enqueue(request)

        while (true) {
            when (queryStatus(downloadManager, downloadId)) {
                DownloadState.IN_PROGRESS -> delay(POLL_INTERVAL_MS)
                DownloadState.SUCCESSFUL -> {
                    installApk(update)
                    return Result.Success(Unit)
                }

                DownloadState.FAILED -> {
                    logger.warn("APK download failed for DualNav ${update.versionName}")
                    return Result.Error(DataError.Remote.UNKNOWN)
                }
            }
        }
    }

    private fun queryStatus(downloadManager: DownloadManager, downloadId: Long): DownloadState {
        val cursor = downloadManager.query(DownloadManager.Query().setFilterById(downloadId))
            ?: return DownloadState.FAILED
        cursor.use {
            if (!it.moveToFirst()) return DownloadState.FAILED
            return when (it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))) {
                DownloadManager.STATUS_SUCCESSFUL -> DownloadState.SUCCESSFUL
                DownloadManager.STATUS_FAILED -> DownloadState.FAILED
                else -> DownloadState.IN_PROGRESS
            }
        }
    }

    /** Launches the system package installer directly — the user never has to go find the file
     * or tap the download-complete notification themselves. */
    private fun installApk(update: AppUpdate) {
        val file = File(context.getExternalFilesDir(DOWNLOAD_SUBDIR), apkFileName(update))
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }

    private fun openReleasePage(update: AppUpdate) {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, update.releasePageUrl.toUri())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private fun apkFileName(update: AppUpdate): String = "DualNav-${update.versionName}.apk"

    private enum class DownloadState { IN_PROGRESS, SUCCESSFUL, FAILED }

    private companion object {
        const val DOWNLOAD_SUBDIR = "updates"
        const val POLL_INTERVAL_MS = 500L
    }
}
