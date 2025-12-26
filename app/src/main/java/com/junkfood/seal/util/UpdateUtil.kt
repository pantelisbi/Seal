package com.junkfood.seal.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import com.junkfood.seal.App
import com.junkfood.seal.App.Companion.context
import com.junkfood.seal.R
import com.junkfood.seal.util.FileUtil.getFileProvider
import com.junkfood.seal.util.PreferenceUtil.getInt
import com.junkfood.seal.util.PreferenceUtil.getString
import com.junkfood.seal.util.PreferenceUtil.updateLong
import com.yausername.youtubedl_android.YoutubeDL
import java.io.File
import java.util.regex.Pattern
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import okio.BufferedSink
import okio.buffer
import okio.sink
import java.net.URL
import java.util.Locale

object UpdateUtil {

    private const val OWNER = "pantelisbi"
    private const val REPO = "Seal"
    private const val ARM64 = "arm64-v8a"
    private const val ARM32 = "armeabi-v7a"
    private const val X86 = "x86"
    private const val X64 = "x86_64"
    private const val TAG = "UpdateUtil"

    private val client = OkHttpClient()
    private val requestForLatestRelease =
        Request.Builder()
            .url("https://api.github.com/repos/${OWNER}/${REPO}/releases/latest")
            .build()

    private val requestForReleases =
        Request.Builder().url("https://api.github.com/repos/${OWNER}/${REPO}/releases").build()

    private const val ytdlpNightlyBuildRelease =
        "https://api.github.com/repos/yt-dlp/yt-dlp-nightly-builds/releases/latest"

    private val jsonFormat = Json { ignoreUnknownKeys = true }

    private fun repoBranchToRawUrl(input: String): String? {
        // owner/repo@branch -> https://raw.githubusercontent.com/owner/repo/branch/yt-dlp
        val pattern = Regex("^([A-Za-z0-9_.-]+)/([A-Za-z0-9_.-]+)@(.+)$")
        val match = pattern.find(input) ?: return null
        val (owner, repo, branch) = match.destructured
        return "https://raw.githubusercontent.com/${owner}/${repo}/${branch}/yt-dlp"
    }

    private fun isReleasesApiUrl(url: String): Boolean {
        return url.contains("api.github.com") && url.contains("/releases/")
    }

    private fun isRawGithubUrl(url: String): Boolean {
        return url.startsWith("https://raw.githubusercontent.com/")
    }

    private fun getYtdlpBinaryFile(appContext: Context): File {
        val baseDir = File(appContext.noBackupFilesDir, YoutubeDL.baseName)
        val ytdlpDir = File(baseDir, YoutubeDL.ytdlpDirName)
        if (!ytdlpDir.exists()) ytdlpDir.mkdirs()
        return File(ytdlpDir, YoutubeDL.ytdlpBin)
    }

    private fun writeBytesToFile(bytes: ByteArray, file: File) {
        file.sink().buffer().use { sink: BufferedSink -> sink.write(bytes) }
        file.setExecutable(true, false)
    }

    private fun downloadBytes(url: String): ByteArray? {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return null
            return resp.body?.bytes()
        }
    }

    private suspend fun downloadAndInstallYtdlp(appContext: Context, sourceUrl: String): YoutubeDL.UpdateStatus? {
        return withContext(Dispatchers.IO) {
            try {
                val finalUrl = repoBranchToRawUrl(sourceUrl) ?: sourceUrl
                val bytes = downloadBytes(finalUrl) ?: return@withContext null
                val ytdlpFile = getYtdlpBinaryFile(appContext)
                writeBytesToFile(bytes, ytdlpFile)

                // Refresh version from library
                YoutubeDL.getInstance().version(appContext)?.let { PreferenceUtil.encodeString(YT_DLP_VERSION, it) }
                return@withContext YoutubeDL.UpdateStatus.DONE
            } catch (e: Exception) {
                e.printStackTrace()
                return@withContext null
            }
        }
    }

    suspend fun updateYtDlp(channelSelection: Int, stableUrl: String): YoutubeDL.UpdateStatus? =
        withContext(Dispatchers.IO) {
            when (channelSelection) {
                YT_DLP_NIGHTLY -> {
                    val result =
                        YoutubeDL.getInstance()
                            .updateYoutubeDL(appContext = context, updateChannel = YoutubeDL.UpdateChannel.NIGHTLY)
                    if (result == YoutubeDL.UpdateStatus.DONE) {
                        YoutubeDL.getInstance().version(context)?.let { PreferenceUtil.encodeString(YT_DLP_VERSION, it) }
                    }
                    YT_DLP_UPDATE_TIME.updateLong(System.currentTimeMillis())
                    return@withContext result
                }
                else -> {
                    // Stable branch: support releases API, repo@branch shorthand, and raw URLs
                    return@withContext if (isReleasesApiUrl(stableUrl)) {
                        val result =
                            YoutubeDL.getInstance()
                                .updateYoutubeDL(appContext = context, updateChannel = YoutubeDL.UpdateChannel(stableUrl))
                        if (result == YoutubeDL.UpdateStatus.DONE) {
                            YoutubeDL.getInstance().version(context)?.let { PreferenceUtil.encodeString(YT_DLP_VERSION, it) }
                        }
                        YT_DLP_UPDATE_TIME.updateLong(System.currentTimeMillis())
                        result
                    } else {
                        // raw URL or repo@branch shorthand
                        val result = downloadAndInstallYtdlp(context, stableUrl)
                        if (result == YoutubeDL.UpdateStatus.DONE) YT_DLP_UPDATE_TIME.updateLong(System.currentTimeMillis())
                        result
                    }
                }
            }
        }

    suspend fun updateYtDlp(): YoutubeDL.UpdateStatus? =
        updateYtDlp(YT_DLP_UPDATE_CHANNEL.getInt(), YT_DLP_STABLE_URL.getString())

    private fun getLatestRelease(): Release =
        client.newCall(requestForReleases).execute().body.use {
            val releaseList = jsonFormat.decodeFromString<List<Release>>(it.string())
            val stable = UPDATE_CHANNEL.getInt() == STABLE
            val latestRelease =
                releaseList
                    .filter { if (stable) it.name.toVersion() is Version.Stable else true }
                    .maxByOrNull { it.name.toVersion() } ?: throw Exception("null response")
            latestRelease
        }

    fun checkForUpdate(context: Context = App.context): Release? {
        val currentVersion = context.getCurrentVersion()
        val latestRelease = getLatestRelease()
        val latestVersion = latestRelease.name.toVersion()
        return if (currentVersion < latestVersion) latestRelease else null
    }

    private fun Context.getCurrentVersion(): Version =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager
                .getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
                .versionName
                .toVersion()
        } else {
            packageManager.getPackageInfo(packageName, 0).versionName.toVersion()
        }

    private fun Context.getLatestApk() = File(getExternalFilesDir("apk"), "latest.apk")

    fun installLatestApk(context: Context = App.context) =
        context.run {
            kotlin
                .runCatching {
                    val contentUri =
                        FileProvider.getUriForFile(this, getFileProvider(), getLatestApk())
                    val intent =
                        Intent(Intent.ACTION_VIEW).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            setDataAndType(contentUri, "application/vnd.android.package-archive")
                        }
                    startActivity(intent)
                }
                .onFailure { throwable: Throwable ->
                    throwable.printStackTrace()
                    ToastUtil.makeToast(R.string.app_update_failed)
                }
        }

    suspend fun deleteOutdatedApk(context: Context = App.context) =
        context.runCatching {
            val apkFile = getLatestApk()
            if (apkFile.exists()) {
                val apkVersion =
                    context.packageManager
                        .getPackageArchiveInfo(apkFile.absolutePath, 0)
                        ?.versionName
                        .toVersion()
                if (apkVersion <= context.getCurrentVersion()) {
                    apkFile.delete()
                }
            }
        }

    suspend fun downloadApk(
        context: Context = App.context,
        release: Release,
    ): Flow<DownloadStatus> =
        withContext(Dispatchers.IO) {
            val apkVersion =
                context.packageManager
                    .getPackageArchiveInfo(context.getLatestApk().absolutePath, 0)
                    ?.versionName
                    .toVersion()

            Log.d(TAG, apkVersion.toString())

            if (apkVersion >= release.name.toVersion()) {
                return@withContext flow<DownloadStatus> {
                    emit(DownloadStatus.Finished(context.getLatestApk()))
                }
            }

            val abiList = Build.SUPPORTED_ABIS
            val preferredArch = abiList.firstOrNull() ?: return@withContext emptyFlow()

            val targetUrl =
                release.assets
                    ?.find {
                        return@find it.name?.contains(preferredArch) ?: false
                    }
                    ?.browserDownloadUrl ?: return@withContext emptyFlow()
            val request = Request.Builder().url(targetUrl).build()
            try {
                val response = client.newCall(request).execute()
                val responseBody = response.body
                return@withContext responseBody.downloadFileWithProgress(context.getLatestApk())
            } catch (e: Exception) {
                e.printStackTrace()
            }
            emptyFlow()
        }

    private fun ResponseBody.downloadFileWithProgress(saveFile: File): Flow<DownloadStatus> =
        flow {
                emit(DownloadStatus.Progress(0))

                var deleteFile = true

                try {
                    byteStream().use { inputStream ->
                        saveFile.outputStream().use { outputStream ->
                            val totalBytes = contentLength()
                            val data = ByteArray(8_192)
                            var progressBytes = 0L

                            while (true) {
                                val bytes = inputStream.read(data)

                                if (bytes == -1) {
                                    break
                                }

                                outputStream.channel
                                outputStream.write(data, 0, bytes)
                                progressBytes += bytes
                                emit(
                                    DownloadStatus.Progress(
                                        percent = ((progressBytes * 100) / totalBytes).toInt()
                                    )
                                )
                            }

                            when {
                                progressBytes < totalBytes -> throw Exception("missing bytes")
                                progressBytes > totalBytes -> throw Exception("too many bytes")
                                else -> deleteFile = false
                            }
                        }
                    }

                    emit(DownloadStatus.Finished(saveFile))
                } finally {
                    if (deleteFile) {
                        saveFile.delete()
                    }
                }
            }
            .flowOn(Dispatchers.IO)
            .distinctUntilChanged()

    @Serializable
    data class Release(
        @SerialName("html_url") val htmlUrl: String? = null,
        @SerialName("tag_name") val tagName: String? = null,
        val name: String? = null,
        val draft: Boolean? = null,
        @SerialName("prerelease") val preRelease: Boolean? = null,
        @SerialName("created_at") val createdAt: String? = null,
        @SerialName("published_at") val publishedAt: String? = null,
        val assets: List<AssetsItem>? = null,
        val body: String? = null,
    )

    @Serializable
    data class AssetsItem(
        val name: String? = null,
        @SerialName("content_type") val contentType: String? = null,
        val size: Int? = null,
        @SerialName("download_count") val downloadCount: Int? = null,
        @SerialName("created_at") val createdAt: String? = null,
        @SerialName("updated_at") val updatedAt: String? = null,
        @SerialName("browser_download_url") val browserDownloadUrl: String? = null,
    )

    sealed class DownloadStatus {
        object NotYet : DownloadStatus()

        data class Progress(val percent: Int) : DownloadStatus()

        data class Finished(val file: File) : DownloadStatus()
    }

    private val pattern = Pattern.compile("""v?(\d+)\.(\d+)\.(\d+)(-(\w+)\.(\d+))?""")
    private val EMPTY_VERSION = Version.Stable()

    fun String?.toVersion(): Version =
        this?.run {
            val matcher = pattern.matcher(this)
            if (matcher.find()) {
                val major = matcher.group(1)?.toInt() ?: 0
                val minor = matcher.group(2)?.toInt() ?: 0
                val patch = matcher.group(3)?.toInt() ?: 0
                val buildNumber = matcher.group(6)?.toInt() ?: 0
                when (matcher.group(5)) {
                    "alpha" -> Version.Alpha(major, minor, patch, buildNumber)
                    "beta" -> Version.Beta(major, minor, patch, buildNumber)
                    "rc" -> Version.ReleaseCandidate(major, minor, patch, buildNumber)
                    else -> Version.Stable(major, minor, patch)
                }
            } else EMPTY_VERSION
        } ?: EMPTY_VERSION

    sealed class Version(val major: Int, val minor: Int, val patch: Int, val build: Int = 0) :
        Comparable<Version> {
        companion object {
            // private const val ABI = 1L
            private const val BUILD = 10L
            private const val VARIANT = 100L
            private const val PATCH = 10_000L
            private const val MINOR = 1_000_000L
            private const val MAJOR = 100_000_000L

            private const val STABLE = VARIANT * 4
            private const val ALPHA = VARIANT * 1
            private const val BETA = VARIANT * 2
            private const val RELEASE_CANDIDATE = VARIANT * 3
        }

        abstract fun toVersionName(): String

        abstract fun toNumber(): Long

        class Alpha(
            versionMajor: Int = 0,
            versionMinor: Int = 0,
            versionPatch: Int = 0,
            versionBuild: Int = 0,
        ) : Version(versionMajor, versionMinor, versionPatch, versionBuild) {
            override fun toVersionName(): String = "${major}.${minor}.${patch}-alpha.$build"

            override fun toNumber(): Long =
                major * MAJOR + minor * MINOR + patch * PATCH + build * BUILD + ALPHA
        }

        class Beta(versionMajor: Int, versionMinor: Int, versionPatch: Int, versionBuild: Int) :
            Version(versionMajor, versionMinor, versionPatch, versionBuild) {
            override fun toVersionName(): String = "${major}.${minor}.${patch}-beta.$build"

            override fun toNumber(): Long =
                major * MAJOR + minor * MINOR + patch * PATCH + build * BUILD + BETA
        }

        class ReleaseCandidate(
            versionMajor: Int,
            versionMinor: Int,
            versionPatch: Int,
            versionBuild: Int,
        ) : Version(versionMajor, versionMinor, versionPatch, versionBuild) {
            override fun toVersionName(): String = "${major}.${minor}.${patch}-rc.$build"

            override fun toNumber(): Long =
                major * MAJOR + minor * MINOR + patch * PATCH + build * BUILD + RELEASE_CANDIDATE
        }

        class Stable(versionMajor: Int = 0, versionMinor: Int = 0, versionPatch: Int = 0) :
            Version(versionMajor, versionMinor, versionPatch) {
            override fun toVersionName(): String = "${major}.${minor}.${patch}"

            override fun toNumber(): Long =
                major * MAJOR + minor * MINOR + patch * PATCH + build * BUILD + STABLE
            // Prioritize stable versions

        }

        override operator fun compareTo(other: Version): Int =
            this.toNumber().compareTo(other.toNumber())
    }
}
