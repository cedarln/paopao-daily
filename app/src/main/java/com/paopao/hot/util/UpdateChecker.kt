package com.paopao.hot.util

import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * 应用内更新：查询 GitHub Release -> 比较 -> 下载 -> 拉起安装。
 * 与泡泡题库（paopao-quiz）的 UpdateChecker 同一套方案（零第三方依赖）：
 * HttpsURLConnection 查 API，系统 DownloadManager 下载（自带通知栏进度、断点续传），
 * FileProvider 暴露 APK 给系统安装器。
 *
 * 本仓库是公开仓库，检查更新直接指向自己的 Release，不需要单独的分发仓库。
 * 发布新版本：CI 打 tag 自动构建「签名后的」APK 传到 Release，tag 形如 vX.Y.Z，
 * 资产名以 .apk 结尾。绝不往 APK 里嵌 token。
 */
object UpdateChecker {

    private const val OWNER = "cedarln"

    /** 检查更新查询的仓库（公开仓库，匿名可查） */
    const val REPO = "paopao-daily"

    private const val API_LATEST =
        "https://api.github.com/repos/$OWNER/$REPO/releases/latest"

    private const val UPDATE_DIR = "update"

    /** 更新包所在目录（应用专属外部存储 update/） */
    fun updateDir(ctx: Context): File = File(ctx.getExternalFilesDir(null), UPDATE_DIR)

    /**
     * 读取一个已下载 APK 的版本号（解析失败返回空串）。
     * 用于判断「安装包是否已经装完」：包版本 <= 当前安装版本 = 已装完，可清理。
     */
    fun archiveVersion(ctx: Context, apk: File): String = try {
        val pi = ctx.packageManager.getPackageArchiveInfo(apk.absolutePath, 0)
        pi?.versionName ?: ""
    } catch (_: Exception) {
        ""
    }

    /** 清空更新目录里所有「已装完或解析失败」的旧安装包，返回删除的文件数 */
    fun cleanInstalledArchives(ctx: Context): Int {
        val dir = updateDir(ctx)
        if (!dir.isDirectory) return 0
        val current = currentVersion(ctx)
        var deleted = 0
        dir.listFiles()?.forEach { f ->
            if (!f.name.lowercase().endsWith(".apk")) return@forEach
            val ver = archiveVersion(ctx, f)
            if (ver.isEmpty() || compareVersions(normalizeVersion(ver), current) <= 0) {
                if (f.delete()) deleted++
            }
        }
        return deleted
    }

    data class ReleaseInfo(
        val tag: String,
        val apkUrl: String,
        val apkSize: Long,
        val notes: String
    ) {
        /** "v1.2.3" -> "1.2.3" */
        val version: String get() = normalizeVersion(tag)
    }

    // ---------- 版本比较（纯逻辑） ----------

    /** 从 tag 里提取版本号：找到第一个数字后只保留数字和点，遇到其他字符就停 */
    fun normalizeVersion(tag: String?): String {
        if (tag == null) return ""
        val v = tag.trim()
        val start = v.indexOfFirst { it in '0'..'9' }
        if (start < 0) return ""
        val sb = StringBuilder()
        var lastWasDot = false
        for (i in start until v.length) {
            val ch = v[i]
            when {
                ch in '0'..'9' -> { sb.append(ch); lastWasDot = false }
                ch == '.' && !lastWasDot -> { sb.append('.'); lastWasDot = true }
                else -> break
            }
        }
        return sb.toString().trim('.')
    }

    /** 按数字段比较版本：负数 = a 更旧，0 = 相同，正数 = a 更新 */
    fun compareVersions(a: String, b: String): Int {
        val pa = a.split(".")
        val pb = b.split(".")
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val x = if (i < pa.size) pa[i].trim().toIntOrNull() ?: 0 else 0
            val y = if (i < pb.size) pb[i].trim().toIntOrNull() ?: 0 else 0
            if (x != y) return x - y
        }
        return 0
    }

    /** 最新 Release 的 tag 是否比当前安装的版本新 */
    fun hasNewer(latestTag: String, currentVersion: String?): Boolean =
        !currentVersion.isNullOrEmpty() &&
            compareVersions(normalizeVersion(latestTag), currentVersion) > 0

    // ---------- GitHub API ----------

    fun currentVersion(ctx: Context): String = try {
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: ""
    } catch (_: Exception) {
        ""
    }

    /** 查询最新 Release（阻塞网络调用，请在协程 Dispatchers.IO 上执行） */
    suspend fun fetchLatest(): ReleaseInfo = withContext(Dispatchers.IO) {
        val conn = URL(API_LATEST).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            val code = conn.responseCode
            if (code != 200) {
                throw IllegalStateException(
                    if (code == 404) "仓库不可见或还没有 Release" else "HTTP $code"
                )
            }
            val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            parseLatest(body)
        } finally {
            conn.disconnect()
        }
    }

    fun parseLatest(json: String): ReleaseInfo {
        val root = JSONObject(json)
        val tag = root.optString("tag_name", "")
        if (tag.isEmpty()) throw IllegalStateException("Release 数据里没有 tag_name")
        var url = ""
        var size = 0L
        root.optJSONArray("assets")?.let { assets ->
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name", "").lowercase().endsWith(".apk")) {
                    url = a.optString("browser_download_url", "")
                    size = a.optLong("size", 0L)
                    break
                }
            }
        }
        if (url.isEmpty()) throw IllegalStateException("最新 Release 里没有 APK 文件")
        return ReleaseInfo(tag, url, size, root.optString("body", ""))
    }

    // ---------- 下载（系统 DownloadManager，自带通知栏进度与断点续传） ----------

    /** 下载到应用私有目录 update/ 下，返回下载 id（-1 表示无法入队） */
    fun download(ctx: Context, url: String, fileName: String): Long {
        // 入队前先清掉旧的已安装包，防止 update/ 目录越积越多
        cleanInstalledArchives(ctx)
        val req = DownloadManager.Request(Uri.parse(url)).apply {
            setTitle(fileName)
            setDescription("泡泡热榜 更新包")
            setMimeType("application/vnd.android.package-archive")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalFilesDir(ctx, null, "$UPDATE_DIR/$fileName")
        }
        val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
        return dm?.enqueue(req) ?: -1L
    }

    fun downloadedFile(ctx: Context, fileName: String): File =
        File(updateDir(ctx), fileName)

    data class DownloadProgress(
        val status: Int,
        val downloaded: Long,
        val total: Long
    ) {
        val percent: Int get() = if (total > 0) (downloaded * 100 / total).toInt() else 0
    }

    /** 查询下载状态与进度；返回 null 表示查询不到该任务 */
    fun queryDownload(ctx: Context, id: Long): DownloadProgress? {
        val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager ?: return null
        val cur = dm.query(DownloadManager.Query().setFilterById(id)) ?: return null
        return cur.use {
            if (!it.moveToFirst()) return null
            DownloadProgress(
                status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)),
                downloaded = it.getLong(
                    it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                ),
                total = it.getLong(
                    it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                )
            )
        }
    }

    // ---------- 安装（Android 8+ 需要用户允许「安装未知应用」） ----------

    fun canInstallApk(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            ctx.packageManager.canRequestPackageInstalls()

    /** 引导用户去开「安装未知应用」权限（回来后重试安装即可） */
    fun requestInstallPermission(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            activity.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${activity.packageName}")
                )
            )
        }
    }

    /** 拉起系统安装器；权限未开时跳设置页并返回 false */
    fun promptInstall(activity: Activity, apk: File): Boolean {
        if (!canInstallApk(activity)) {
            requestInstallPermission(activity)
            return false
        }
        val uri = FileProvider.getUriForFile(
            activity, "${activity.packageName}.fileprovider", apk
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        activity.startActivity(intent)
        return true
    }
}
