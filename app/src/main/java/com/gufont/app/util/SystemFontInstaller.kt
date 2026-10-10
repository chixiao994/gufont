package com.gufont.app.util

import android.content.Context
import java.io.File

/**
 * 通过 root 把字体写入 /system/fonts/ 并替换 Roboto-Regular.ttf。
 * 从工具 9 移植。
 */
object SystemFontInstaller {

    data class Result(val ok: Boolean, val message: String)

    private const val ROBOTO_REGULAR = "/system/fonts/Roboto-Regular.ttf"
    private const val BACKUP_PATH = "/data/local/tmp/Roboto-Regular.ttf.bak"

    fun install(context: Context, source: File, displayName: String): Result {
        if (!source.exists() || source.length() == 0L) {
            return Result(false, "字体文件不存在或为空")
        }

        val id = runSu("id")
        if (id.code != 0 || !id.out.contains("uid=0")) {
            return Result(
                false,
                "没有 root 权限。\n\n请确认手机已 root，" +
                        "并在 root 管理器中允许本应用。\n\nsu 输出：${id.out.ifBlank { "（无）" }}"
            )
        }

        val extDir = context.getExternalFilesDir(null) ?: context.cacheDir
        if (!extDir.exists() && !extDir.mkdirs()) {
            return Result(false, "无法创建暂存目录")
        }
        val stage = File(extDir, "system_font_stage.ttf")
        try {
            source.inputStream().use { input ->
                stage.outputStream().use { output -> input.copyTo(output) }
            }
        } catch (e: Exception) {
            return Result(false, "准备字体失败：${e.message}")
        }

        val stageQ = shQuote(stage.absolutePath)
        val safeName = sanitizeName(displayName)
        val dst = "/system/fonts/$safeName"
        val dstQ = shQuote(dst)

        runSu("mount -o rw,remount /system")
        runSu("mount -o rw,remount /")

        val writeTest = runSu(
            "touch /system/fonts/.fi_test 2>/dev/null && " +
                    "rm -f /system/fonts/.fi_test && echo OK || echo FAIL"
        )
        if (!writeTest.out.contains("OK")) {
            stage.delete()
            return Result(
                false,
                "/system 分区无法写入。\n\n" +
                        "• 动态分区机型需先在电脑上执行 adb disable-verity 后重启\n" +
                        "• 或 root 未授予挂载权限\n\n" +
                        "su 输出：${writeTest.out.ifBlank { "（无）" }}"
            )
        }

        val hasRoboto = runSu("test -f $ROBOTO_REGULAR && echo yes || echo no")
            .out.contains("yes")
        if (hasRoboto) runSu("cp -f $ROBOTO_REGULAR $BACKUP_PATH")

        val cp = runSu("cp -f $stageQ $dstQ")
        if (cp.code != 0) {
            stage.delete()
            return Result(false, "复制失败：${cp.out}")
        }
        runSu("chmod 644 $dstQ")
        runSu("chown root:root $dstQ")

        if (hasRoboto) {
            runSu("cp -f $stageQ $ROBOTO_REGULAR")
            runSu("chmod 644 $ROBOTO_REGULAR")
            runSu("chown root:root $ROBOTO_REGULAR")
        }

        runSu("restorecon -R /system/fonts/ 2>/dev/null")
        stage.delete()

        val verify = runSu("test -f $dstQ && echo yes || echo no")
        if (!verify.out.contains("yes")) {
            return Result(false, "写入后校验失败")
        }

        val sb = StringBuilder()
        sb.append("已安装：$safeName\n")
        if (hasRoboto) sb.append("已替换 Roboto-Regular.ttf\n")
        sb.append("原文件备份在 $BACKUP_PATH\n\n重启手机后生效。")
        return Result(true, sb.toString())
    }

    private fun sanitizeName(name: String): String {
        val cleaned = name.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return if (cleaned.isEmpty()) "CustomFont.ttf" else cleaned
    }

    private fun shQuote(s: String): String = "'" + s.replace("'", "'\\''") + "'"

    private data class SuResult(val code: Int, val out: String)

    private fun runSu(cmd: String): SuResult {
        return try {
            val pb = ProcessBuilder("su", "-c", cmd).redirectErrorStream(true)
            val p = pb.start()
            val out = p.inputStream.bufferedReader().use { it.readText() }
            val code = p.waitFor()
            SuResult(code, out.trim())
        } catch (e: Exception) {
            SuResult(-1, e.message ?: "执行失败")
        }
    }
}
