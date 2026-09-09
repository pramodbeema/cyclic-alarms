package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object AppUpdateManager {
    private const val GITHUB_REPO = "pramodbeema/cyclicalarms"
    private const val CURRENT_VERSION_NAME = "1.6"

    data class UpdateInfo(
        val isUpdateAvailable: Boolean,
        val latestVersion: String,
        val releasePageUrl: String,
        val releaseNotes: String
    )

    suspend fun checkForUpdates(): Result<UpdateInfo> = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://api.github.com/repos/$GITHUB_REPO/releases/latest")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
            conn.connectTimeout = 8000
            conn.readTimeout = 8000

            if (conn.responseCode != 200) {
                return@withContext Result.failure(
                    Exception("HTTP ${conn.responseCode}: ${conn.responseMessage}")
                )
            }

            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            val tagName = json.optString("tag_name", "").removePrefix("v").trim()
            val bodyText = json.optString("body", "")
            val htmlUrl = json.optString("html_url",
                "https://github.com/$GITHUB_REPO/releases/latest")

            Result.success(
                UpdateInfo(
                    isUpdateAvailable = isVersionNewer(tagName, CURRENT_VERSION_NAME),
                    latestVersion = tagName,
                    releasePageUrl = htmlUrl,
                    releaseNotes = bodyText
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Open the GitHub releases page in the default browser. */
    fun openReleasePage(context: Context, url: String) {
        val uri = url.ifBlank { "https://github.com/$GITHUB_REPO/releases/latest" }
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        )
    }

    private fun isVersionNewer(remote: String, current: String): Boolean {
        if (remote.isEmpty()) return false
        val r = remote.split(".").mapNotNull { it.toIntOrNull() }
        val c = current.split(".").mapNotNull { it.toIntOrNull() }
        for (i in 0 until maxOf(r.size, c.size)) {
            val rv = r.getOrElse(i) { 0 }
            val cv = c.getOrElse(i) { 0 }
            if (rv > cv) return true
            if (rv < cv) return false
        }
        return false
    }
}
