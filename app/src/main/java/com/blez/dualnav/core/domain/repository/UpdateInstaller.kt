package com.blez.dualnav.core.domain.repository

import com.blez.dualnav.core.domain.model.AppUpdate
import com.blez.dualnav.core.domain.util.DataError
import com.blez.dualnav.core.domain.util.Result

interface UpdateInstaller {
    /** Whether this app is allowed to prompt the package installer with a downloaded APK. */
    fun canInstallPackages(): Boolean

    /** Sends the user to the system settings screen for granting the "install unknown apps"
     * permission for this app. */
    fun requestInstallPermission()

    /**
     * Downloads [update]'s APK and launches the package installer as soon as it lands, showing a
     * native download-progress notification throughout. When [AppUpdate.downloadUrl] is absent
     * (no APK attached to the release), opens the GitHub release page instead.
     */
    suspend fun downloadAndInstall(update: AppUpdate): Result<Unit, DataError>
}
