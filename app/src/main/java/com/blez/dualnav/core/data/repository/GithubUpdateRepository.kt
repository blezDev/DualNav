package com.blez.dualnav.core.data.repository

import com.blez.dualnav.BuildConfig
import com.blez.dualnav.core.domain.model.AppUpdate
import com.blez.dualnav.core.domain.repository.UpdateRepository
import com.blez.dualnav.core.domain.util.DataError
import com.blez.dualnav.core.domain.util.Logger
import com.blez.dualnav.core.domain.util.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

class GithubUpdateRepository(
    private val json: Json,
    private val logger: Logger
) : UpdateRepository {

    override suspend fun checkForUpdate(): Result<AppUpdate?, DataError> =
        withContext(Dispatchers.IO) {
            val connection = try {
                (URL(LATEST_RELEASE_URL).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = TIMEOUT_MS
                    readTimeout = TIMEOUT_MS
                    setRequestProperty("Accept", "application/vnd.github+json")
                }
            } catch (e: Exception) {
                logger.warn("Could not open connection to the GitHub releases API", e)
                return@withContext Result.Error(DataError.Remote.NETWORK_ERROR)
            }

            try {
                connection.connect()
                when (connection.responseCode) {
                    HttpURLConnection.HTTP_OK -> {
                        val body = connection.inputStream.bufferedReader().use { it.readText() }
                        val release = json.decodeFromString(GithubReleaseDto.serializer(), body)
                        Result.Success(release.toAppUpdateOrNull())
                    }

                    HttpURLConnection.HTTP_NOT_FOUND -> Result.Error(DataError.Remote.NOT_FOUND)
                    else -> Result.Error(DataError.Remote.UNKNOWN)
                }
            } catch (e: Exception) {
                logger.warn("Failed to fetch or parse the latest GitHub release", e)
                Result.Error(DataError.Remote.NETWORK_ERROR)
            } finally {
                connection.disconnect()
            }
        }

    private fun GithubReleaseDto.toAppUpdateOrNull(): AppUpdate? {
        val remoteVersion = tagName.removePrefix("v").removePrefix("V")
        if (!isNewerVersion(remoteVersion, BuildConfig.VERSION_NAME)) return null

        return AppUpdate(
            versionName = remoteVersion,
            releaseNotes = body,
            downloadUrl = assets.firstOrNull { it.name.endsWith(".apk") }?.browserDownloadUrl,
            releasePageUrl = htmlUrl
        )
    }

    /** Lenient dotted-integer comparison — "1.2" beats "1.10" is the one thing SemVer gets right
     * here that naive string comparison wouldn't, so this compares the parsed integer parts,
     * padding the shorter version with zeros. Anything after a "-" (pre-release tag) is ignored. */
    private fun isNewerVersion(remote: String, local: String): Boolean {
        val remoteParts = remote.substringBefore('-').split(".").map { it.toIntOrNull() ?: 0 }
        val localParts = local.substringBefore('-').split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(remoteParts.size, localParts.size)) {
            val r = remoteParts.getOrElse(i) { 0 }
            val l = localParts.getOrElse(i) { 0 }
            if (r != l) return r > l
        }
        return false
    }

    @Serializable
    private data class GithubReleaseDto(
        @SerialName("tag_name") val tagName: String,
        @SerialName("body") val body: String = "",
        @SerialName("html_url") val htmlUrl: String = "",
        @SerialName("assets") val assets: List<GithubReleaseAssetDto> = emptyList()
    )

    @Serializable
    private data class GithubReleaseAssetDto(
        @SerialName("name") val name: String,
        @SerialName("browser_download_url") val browserDownloadUrl: String
    )

    private companion object {
        const val TIMEOUT_MS = 8000
        val LATEST_RELEASE_URL =
            "https://api.github.com/repos/${BuildConfig.GITHUB_OWNER}/${BuildConfig.GITHUB_REPO}/releases/latest"
    }
}
