package com.gufont.app.data.storage

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.gufont.app.data.json.JsonIO
import com.gufont.app.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ProjectStorage(
    private val context: Context,
    val rootUri: Uri
) {
    private val root: DocumentFile
        get() = DocumentFile.fromTreeUri(context, rootUri)
            ?: error("无法访问项目根目录")

    fun isProjectRoot(): Boolean = findRootFile("project.json") != null

    // ========== 项目 ==========

    suspend fun loadProject(): Project = withContext(Dispatchers.IO) {
        val f = findRootFile("project.json") ?: error("project.json 不存在")
        val text = context.contentResolver.openInputStream(f.uri)
            ?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: error("无法读取 project.json")
        JsonIO.json.decodeFromString<Project>(text)
    }

    suspend fun saveProject(project: Project): Project = withContext(Dispatchers.IO) {
        val updated = project.copy(updated = now())
        writeText("project.json", JsonIO.json.encodeToString(Project.serializer(), updated))
        updated
    }

    suspend fun createProject(project: Project): Project = withContext(Dispatchers.IO) {
        ensureDir("pages")
        ensureDir("glyphs")
        ensureDir("font")
        val p = project.copy(
            created = project.created.ifEmpty { now() },
            updated = now()
        )
        writeText("project.json", JsonIO.json.encodeToString(Project.serializer(), p))
        p
    }

    // ========== 页 ==========

    suspend fun loadPage(pageIndex: Int): Page? = withContext(Dispatchers.IO) {
        val dir = findSubDir("pages") ?: return@withContext null
        val f = findFileIn(dir, pageJsonName(pageIndex)) ?: return@withContext null
        try {
            val text = context.contentResolver.openInputStream(f.uri)
                ?.use { it.readBytes().toString(Charsets.UTF_8) }
                ?: return@withContext null
            JsonIO.json.decodeFromString<Page>(text)
        } catch (_: Exception) { null }
    }

    suspend fun savePage(page: Page) = withContext(Dispatchers.IO) {
        val dir = ensureDir("pages") ?: error("无法创建 pages")
        writeTextIn(
            dir, pageJsonName(page.pageIndex),
            JsonIO.json.encodeToString(Page.serializer(), page)
        )
    }

    suspend fun listPages(): List<Page> = withContext(Dispatchers.IO) {
        val dir = findSubDir("pages") ?: return@withContext emptyList()
        val files = try { dir.listFiles() } catch (_: Exception) { emptyArray() }
        files
            .filter {
                val n = it.name ?: return@filter false
                n.startsWith("page_") && n.endsWith(".json")
            }
            .sortedBy { it.name }
            .mapNotNull {
                try {
                    val t = context.contentResolver.openInputStream(it.uri)
                        ?.use { s -> s.readBytes().toString(Charsets.UTF_8) }
                        ?: return@mapNotNull null
                    JsonIO.json.decodeFromString<Page>(t)
                } catch (_: Exception) { null }
            }
    }

    // ========== 字位 ==========

    suspend fun loadGlyphIndex(): GlyphIndex = withContext(Dispatchers.IO) {
        val dir = findSubDir("glyphs") ?: return@withContext GlyphIndex()
        val f = findFileIn(dir, "glyphs.json") ?: return@withContext GlyphIndex()
        try {
            val t = context.contentResolver.openInputStream(f.uri)
                ?.use { s -> s.readBytes().toString(Charsets.UTF_8) }
                ?: return@withContext GlyphIndex()
            JsonIO.json.decodeFromString<GlyphIndex>(t)
        } catch (_: Exception) { GlyphIndex() }
    }

    suspend fun saveGlyphIndex(index: GlyphIndex) = withContext(Dispatchers.IO) {
        val dir = ensureDir("glyphs") ?: error("无法创建 glyphs")
        writeTextIn(
            dir, "glyphs.json",
            JsonIO.json.encodeToString(GlyphIndex.serializer(), index)
        )
    }

    // ========== 文件 ==========

    suspend fun writeImage(
        dirName: String,
        fileName: String,
        bytes: ByteArray,
        mime: String = "image/png"
    ) = withContext(Dispatchers.IO) {
        val dir = ensureDir(dirName) ?: error("无法创建 $dirName")
        deleteIfExists(dir, fileName)
        val f = dir.createFile(mime, fileName) ?: error("无法创建 $fileName")
        context.contentResolver.openOutputStream(f.uri, "w")?.use { it.write(bytes) }
            ?: error("无法写入 $fileName")
    }

    suspend fun writeBinary(
        dirName: String,
        fileName: String,
        mime: String,
        bytes: ByteArray
    ) = withContext(Dispatchers.IO) {
        val dir = ensureDir(dirName) ?: error("无法创建 $dirName")
        deleteIfExists(dir, fileName)
        val f = dir.createFile(mime, fileName) ?: error("无法创建 $fileName")
        context.contentResolver.openOutputStream(f.uri, "w")?.use { it.write(bytes) }
            ?: error("无法写入 $fileName")
    }

    suspend fun writeBytes(
        dirName: String,
        fileName: String,
        bytes: ByteArray
    ) = withContext(Dispatchers.IO) {
        val dir = ensureDir(dirName) ?: error("无法创建 $dirName")
        deleteIfExists(dir, fileName)
        val f = dir.createFile("application/octet-stream", fileName)
            ?: error("无法创建 $fileName")
        context.contentResolver.openOutputStream(f.uri, "w")?.use { it.write(bytes) }
            ?: error("无法写入 $fileName")
    }

    fun openFileUri(dirName: String, fileName: String): Uri? {
        val dir = findSubDir(dirName) ?: return null
        return findFileIn(dir, fileName)?.uri
    }

    suspend fun deleteFile(dirName: String, fileName: String) =
        withContext(Dispatchers.IO) {
            try {
                val dir = findSubDir(dirName) ?: return@withContext
                deleteIfExists(dir, fileName)
            } catch (_: Exception) { }
        }

    suspend fun readFileBytes(dirName: String, fileName: String): ByteArray? =
        withContext(Dispatchers.IO) {
            try {
                val uri = openFileUri(dirName, fileName) ?: return@withContext null
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            } catch (_: Exception) { null }
        }

    // ========== 内部工具（兼容带尾斜杠的目录名） ==========

    /** 在 root 里找子项（文件或目录），名字两侧 trim '/' 后匹配 */
    private fun findRootFile(name: String): DocumentFile? {
        val target = name.trimEnd('/')
        return try {
            root.listFiles().firstOrNull { it.name?.trimEnd('/') == target }
        } catch (_: Exception) { null }
    }

    /** 在 root 里找子目录 */
    private fun findSubDir(name: String): DocumentFile? {
        val target = name.trimEnd('/')
        return try {
            root.listFiles().firstOrNull {
                it.isDirectory && it.name?.trimEnd('/') == target
            }
        } catch (_: Exception) { null }
    }

    /** 在指定目录里找子项 */
    private fun findFileIn(dir: DocumentFile, name: String): DocumentFile? {
        val target = name.trimEnd('/')
        return try {
            dir.listFiles().firstOrNull { it.name?.trimEnd('/') == target }
        } catch (_: Exception) { null }
    }

    private fun deleteIfExists(dir: DocumentFile, name: String) {
        try {
            findFileIn(dir, name)?.delete()
        } catch (_: Exception) { }
    }

    private fun ensureDir(name: String): DocumentFile? {
        findSubDir(name)?.let { return it }
        // 若存在同名文件（非目录），删掉再建目录
        findRootFile(name)?.let {
            if (!it.isDirectory) try { it.delete() } catch (_: Exception) { }
        }
        return root.createDirectory(name)
    }

    private fun writeText(fileName: String, text: String) {
        deleteIfExists(root, fileName)
        val f = root.createFile("application/json", fileName)
            ?: error("无法创建 $fileName")
        context.contentResolver.openOutputStream(f.uri, "w")?.use {
            it.write(text.toByteArray(Charsets.UTF_8))
        } ?: error("无法写入 $fileName")
    }

    private fun writeTextIn(dir: DocumentFile, fileName: String, text: String) {
        deleteIfExists(dir, fileName)
        val f = dir.createFile("application/json", fileName)
            ?: error("无法创建 $fileName")
        context.contentResolver.openOutputStream(f.uri, "w")?.use {
            it.write(text.toByteArray(Charsets.UTF_8))
        } ?: error("无法写入 $fileName")
    }

    private fun pageJsonName(i: Int) = "page_${i.toString().padStart(3, '0')}.json"

    private fun now(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date())
}
