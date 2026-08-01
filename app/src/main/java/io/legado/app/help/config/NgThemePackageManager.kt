package io.legado.app.help.config

import android.content.Context
import android.net.Uri
import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName
import io.legado.app.utils.GSON
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@Keep
private data class NgThemePackageManifest(
    @SerializedName("format") val format: String,
    @SerializedName("version") val version: Int,
    @SerializedName("theme") val theme: NgManagedTheme,
    @SerializedName("lightBackgroundAsset") val lightBackgroundAsset: String? = null,
    @SerializedName("darkBackgroundAsset") val darkBackgroundAsset: String? = null
)

/** Reading NG 自有主题包；MD3 和旧 JSON 的字段适配留到后续兼容层。 */
internal object NgThemePackageManager {

    const val PACKAGE_DIR = "ng_theme_packages"
    private const val MANIFEST_NAME = "manifest.json"
    private const val FORMAT = "reading-ng-theme"
    private const val VERSION = 1
    private const val MAX_ENTRY_COUNT = 32
    private const val MAX_ENTRY_BYTES = 32L * 1024 * 1024
    private const val MAX_TOTAL_BYTES = 64L * 1024 * 1024

    suspend fun exportTheme(
        context: Context,
        theme: NgManagedTheme,
        uri: Uri
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val backgroundEntries = linkedMapOf<String, ThemeAsset>()
            fun registerBackground(path: String?, role: String): String? {
                path?.takeIf(String::isNotBlank) ?: return null
                val existing = backgroundEntries.values.firstOrNull { it.source == path }
                if (existing != null) return existing.entryName
                val extension = backgroundExtension(path)
                val entryName = "assets/background-$role.$extension"
                backgroundEntries[entryName] = ThemeAsset(path, entryName)
                return entryName
            }

            val lightAsset = registerBackground(theme.lightBackground.path, "light")
            val darkAsset = registerBackground(theme.darkBackground.path, "dark")
            val portableTheme = theme.copy(
                packageRootPath = null,
                lightBackground = theme.lightBackground.copy(path = null),
                darkBackground = theme.darkBackground.copy(path = null)
            )
            val manifest = NgThemePackageManifest(
                format = FORMAT,
                version = VERSION,
                theme = portableTheme,
                lightBackgroundAsset = lightAsset,
                darkBackgroundAsset = darkAsset
            )
            val output = context.contentResolver.openOutputStream(uri, "wt")
                ?: error("无法创建主题包")
            ZipOutputStream(BufferedOutputStream(output)).use { zip ->
                zip.putNextEntry(ZipEntry(MANIFEST_NAME))
                zip.write(GSON.toJson(manifest).toByteArray(Charsets.UTF_8))
                zip.closeEntry()
                backgroundEntries.values.forEach { asset ->
                    zip.putNextEntry(ZipEntry(asset.entryName))
                    openThemeAsset(context, asset.source).use { input -> input.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
    }

    suspend fun importTheme(context: Context, uri: Uri): Result<NgManagedTheme> =
        withContext(Dispatchers.IO) {
            runCatching {
                val tempRoot = File(context.cacheDir, "ng_theme_import/${UUID.randomUUID()}")
                val installedRoot = File(
                    File(context.filesDir, PACKAGE_DIR),
                    UUID.randomUUID().toString()
                )
                try {
                    extract(context, uri, tempRoot)
                    val manifestFile = File(tempRoot, MANIFEST_NAME)
                    require(manifestFile.isFile) { "主题包缺少 manifest.json" }
                    val manifest = GSON.fromJson(
                        manifestFile.readText(),
                        NgThemePackageManifest::class.java
                    ) ?: error("主题包清单为空")
                    require(manifest.format == FORMAT && manifest.version == VERSION) {
                        "不支持的主题包格式"
                    }
                    require(manifest.theme.schemaVersion == NG_MANAGED_THEME_SCHEMA_VERSION) {
                        "不支持的主题数据版本"
                    }
                    check(tempRoot.copyRecursively(installedRoot, overwrite = false)) {
                        "无法安装主题包资源"
                    }
                    val imported = manifest.theme.copy(
                        id = "local.${UUID.randomUUID()}",
                        name = NgThemeLibraryStore.uniqueName(context, manifest.theme.name),
                        lightBackground = manifest.theme.lightBackground.copy(
                            path = resolveInstalledAsset(installedRoot, manifest.lightBackgroundAsset)
                        ),
                        darkBackground = manifest.theme.darkBackground.copy(
                            path = resolveInstalledAsset(installedRoot, manifest.darkBackgroundAsset)
                        ),
                        packageRootPath = installedRoot.absolutePath
                    ).normalized()
                    NgThemeLibraryStore.addOrReplace(context, imported)
                } catch (error: Throwable) {
                    installedRoot.deleteRecursively()
                    throw error
                } finally {
                    tempRoot.deleteRecursively()
                }
            }
        }

    private fun extract(context: Context, uri: Uri, root: File) {
        root.mkdirs()
        val canonicalRoot = root.canonicalFile
        val input = context.contentResolver.openInputStream(uri)
            ?: error("无法读取主题包")
        var entryCount = 0
        var totalBytes = 0L
        ZipInputStream(BufferedInputStream(input)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entryCount++
                require(entryCount <= MAX_ENTRY_COUNT) { "主题包文件数量过多" }
                val target = File(canonicalRoot, entry.name).canonicalFile
                require(target.toPath().startsWith(canonicalRoot.toPath())) {
                    "主题包包含越界路径"
                }
                if (entry.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile?.mkdirs()
                    var entryBytes = 0L
                    target.outputStream().buffered().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val read = zip.read(buffer)
                            if (read < 0) break
                            entryBytes += read
                            totalBytes += read
                            require(entryBytes <= MAX_ENTRY_BYTES) { "主题包单个文件过大" }
                            require(totalBytes <= MAX_TOTAL_BYTES) { "主题包体积过大" }
                            output.write(buffer, 0, read)
                        }
                    }
                }
                zip.closeEntry()
            }
        }
    }

    private fun resolveInstalledAsset(root: File, relativePath: String?): String? {
        relativePath ?: return null
        val canonicalRoot = root.canonicalFile
        val file = File(canonicalRoot, relativePath).canonicalFile
        require(file.toPath().startsWith(canonicalRoot.toPath()) && file.isFile) {
            "主题包背景资源不存在"
        }
        return file.absolutePath
    }

    private fun openThemeAsset(context: Context, source: String) = when {
        source.startsWith("asset://") -> context.assets.open(source.removePrefix("asset://"))
        else -> File(source).inputStream()
    }

    private fun backgroundExtension(path: String): String {
        val source = path.substringBefore('?').lowercase()
        return when {
            source.endsWith(".9.png") -> "9.png"
            source.endsWith(".png") -> "png"
            source.endsWith(".webp") -> "webp"
            source.endsWith(".gif") -> "gif"
            source.endsWith(".jpeg") -> "jpeg"
            else -> "jpg"
        }
    }

    private data class ThemeAsset(val source: String, val entryName: String)
}
