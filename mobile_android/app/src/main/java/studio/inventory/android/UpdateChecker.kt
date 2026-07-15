package studio.inventory.android

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

data class UpdateCheckResult(
    val latestVersion: String,
    val releaseName: String,
    val releaseUrl: String,
    val updateAvailable: Boolean,
)

class UpdateChecker(
    private val http: OkHttpClient = WebDavClient.defaultHttpClient(),
    private val gson: Gson = Gson(),
) {
    suspend fun check(currentVersion: String): Result<UpdateCheckResult> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(LatestReleaseApi)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "maker-studio-inventory-android")
                .get()
                .build()
            http.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "GitHub 返回 HTTP ${response.code}" }
                val release = gson.fromJson(response.body.string(), GitHubRelease::class.java)
                val latestVersion = release.tagName.removePrefix("v").ifBlank { release.tagName }
                UpdateCheckResult(
                    latestVersion = latestVersion,
                    releaseName = release.name.ifBlank { release.tagName },
                    releaseUrl = release.htmlUrl.ifBlank { RepositoryReleasesUrl },
                    updateAvailable = isVersionNewer(latestVersion, currentVersion),
                )
            }
        }
    }
}

internal fun isVersionNewer(candidate: String, current: String): Boolean {
    val candidateParts = versionParts(candidate)
    val currentParts = versionParts(current)
    val count = maxOf(candidateParts.size, currentParts.size)
    for (index in 0 until count) {
        val candidatePart = candidateParts.getOrElse(index) { 0 }
        val currentPart = currentParts.getOrElse(index) { 0 }
        if (candidatePart != currentPart) return candidatePart > currentPart
    }
    return false
}

private fun versionParts(value: String): List<Int> {
    return value.removePrefix("v")
        .split(Regex("[^0-9]+"))
        .filter(String::isNotBlank)
        .mapNotNull(String::toIntOrNull)
}

private data class GitHubRelease(
    @SerializedName("tag_name") val tagName: String = "",
    @SerializedName("html_url") val htmlUrl: String = "",
    val name: String = "",
)

const val RepositoryUrl = "https://github.com/llleeeqi/maker-studio-inventory"
const val RepositoryReleasesUrl = "$RepositoryUrl/releases"
private const val LatestReleaseApi = "https://api.github.com/repos/llleeeqi/maker-studio-inventory/releases/latest"
