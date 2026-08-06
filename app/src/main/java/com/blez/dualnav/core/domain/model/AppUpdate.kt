package com.blez.dualnav.core.domain.model

/**
 * A GitHub release newer than the running app. [downloadUrl] is null when the release has no
 * attached APK asset — callers should fall back to sending the user to [releasePageUrl] instead.
 */
data class AppUpdate(
    val versionName: String,
    val releaseNotes: String,
    val downloadUrl: String?,
    val releasePageUrl: String
)
