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

    fun isProjectRoot(): Boolean = root.findFile("project.json") != null

    // ---------- 项目 ----------

    suspend fun loadProject(): Project = withContext(Dispatchers.IO) {
        val f = root.findFile("project.json") ?: error("project.json 不存在")
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

    // ---------- 页 ----------

    suspend fun loadPage(pageIndex: Int): Page? = withContext(Dispatchers.IO) {
        val dir = root.findFile("pages") ?: return@withContext null
        val f = dir.findFile(pageJsonName(pageIndex)) ?: return@withContext null
        try {
            val text = context.contentResolver.openInputStream(f.uri)
                ?.use { it.readBytes().toString(Charsets.UTF_8) }
                ?: return@withContext null
            JsonIO.json.decodeFromString<Page>(text)
        } catch (_: Exception) { null }
    }

    suspend fun savePage(page: Page) = withContext(Dispatchers.IO) {
        val dir = ensureDir("pages") ?: error("无法创建 pages")
        writeTextIn(dir, pageJsonName(page.pageIndex),
            JsonIO.json.encodeToString(Page.serializer(), page))
    }

    suspend fun listPages(): List<Page> = withContext(Dispatchers.IO) {
        val dir = root.findFile("pages") ?: return@withContext emptyList()
        dir.listFiles()
            .filter {
                it.name?.startsWith("page_") == true &&
                        it.name?.endsWith(".json") == true
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

    // ---------- 字位 ----------

    suspend fun loadGlyphIndex(): GlyphIndex = withContext(Dispatchers.IO) {
        val dir = root.findFile("glyphs") ?: return@withContext GlyphIndex()
        val f = dir.findFile("glyphs.json") ?: return@withContext GlyphIndex()
        try {
            val t = context.contentResolver.openInputStream(f.uri)
                ?.use { s -> s.readBytes().toString(Charsets.UTF_8) }
                ?: return@withContext GlyphIndex()
            JsonIO.json.decodeFromString<GlyphIndex>(t)
        } catch (_: Exception) { GlyphIndex() }
    }

    suspend fun saveGlyphIndex(index: GlyphIndex) = withContext(Dispatchers.IO) {
        val dir = ensureDir("glyphs") ?: error("无法创建 glyphs")
        writeTextIn(dir, "glyphs.json",
            JsonIO.json.encodeToString(GlyphIndex.serializer(), index))
    }

    // ---------- 文件 ----------

    suspend fun writeImage(dirName: String, fileName: String, bytes: ByteArray) =
        withContext(Dispatchers.IO) {
            val dir = ensureDir(dirName) ?: error("无法创建 $dirName")
            dir.findFile(fileName)?.delete()
            val f = dir.createFile("image/png", fileName)
                ?: error("无法创建 $fileName")
            context.contentResolver.openOutputStream(f.uri, "w")?.use { it.write(bytes) }
                ?: error("无法写入 $fileName")
        }

    suspend fun writeBytes(dirName: String, fileName: String, bytes: ByteArray) =
        withContext(Dispatchers.IO) {
            val dir = ensureDir(dirName) ?: error("无法创建 $dirName")
            dir.findFile(fileName)?.delete()
            val f = dir.createFile("application/octet-stream", fileName)
                ?: error("无法创建 $fileName")
            context.contentResolver.openOutputStream(f.uri, "w")?.use { it.write(bytes) }
                ?: error("无法写入 $fileName")
        }

    fun openFileUri(dirName: String, fileName: String): Uri? =
        root.findFile(dirName)?.findFile(fileName)?.uri

    suspend fun deleteFile(dirName: String, fileName: String) =
        withContext(Dispatchers.IO) {
            try {
                root.findFile(dirName)?.findFile(fileName)?.delete()
            } catch (_: Exception) { }
        }

    // ---------- 内部工具 ----------

    private fun ensureDir(name: String): DocumentFile? {
        val r = root
        r.findFile(name)?.let { if (it.isDirectory) return it else it.delete() }
        return r.createDirectory(name)
    }

    private fun writeText(fileName: String, text: String) {
        val r = root
        r.findFile(fileName)?.delete()
        val f = r.createFile("application/json", fileName)
            ?: error("无法创建 $fileName")
        context.contentResolver.openOutputStream(f.uri, "w")?.use {
            it.write(text.toByteArray(Charsets.UTF_8))
        } ?: error("无法写入 $fileName")
    }

    private fun writeTextIn(dir: DocumentFile, fileName: String, text: String) {
        dir.findFile(fileName)?.delete()
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
