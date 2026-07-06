package app.revanced.manager.patcher

import app.revanced.manager.patcher.logger.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object ZipContainerNormalizer {
    private val localHeader = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
    private const val maxScan = 1024 * 1024
    private const val maxEntries = 100_000
    private const val maxBytes = 4L * 1024L * 1024L * 1024L

    suspend fun normalize(input: File, workDir: File, logger: Logger): File = withContext(Dispatchers.IO) {
        if (!input.isFile) return@withContext input
        val offset = input.localHeaderOffset() ?: return@withContext input
        val output = workDir.resolve("input-normalized.apk")
        output.delete()

        try {
            input.rewriteZip(output, offset)
            logger.info("Input archive normalized before patching.")
            output
        } catch (error: Throwable) {
            output.delete()
            logger.warn("Input archive normalization failed; using original file: ${error.message ?: error::class.java.name}")
            input
        }
    }

    private fun File.localHeaderOffset(): Long? {
        FileInputStream(this).use { stream ->
            val length = maxScan.coerceAtMost(length().coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
            val buffer = ByteArray(length)
            val read = stream.read(buffer)
            if (read < localHeader.size) return null

            for (offset in 0..read - localHeader.size) {
                if (localHeader.indices.all { buffer[offset + it] == localHeader[it] }) return offset.toLong()
            }
        }
        return null
    }

    private fun File.rewriteZip(output: File, skipBytes: Long) {
        output.parentFile?.mkdirs()
        openZip(skipBytes).use { zipInput ->
            ZipOutputStream(output.outputStream().buffered()).use { zipOutput ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var entries = 0
                var bytes = 0L

                while (true) {
                    val sourceEntry = zipInput.nextEntry ?: break
                    entries++
                    require(entries <= maxEntries) { "Too many entries" }

                    zipOutput.putNextEntry(ZipEntry(sourceEntry.name).apply {
                        time = sourceEntry.time
                        comment = sourceEntry.comment
                        extra = sourceEntry.extra
                    })

                    while (!sourceEntry.isDirectory) {
                        val read = zipInput.read(buffer)
                        if (read < 0) break
                        bytes += read
                        require(bytes <= maxBytes) { "Archive too large" }
                        zipOutput.write(buffer, 0, read)
                    }

                    zipOutput.closeEntry()
                    zipInput.closeEntry()
                }
            }
        }
    }

    private fun File.openZip(skipBytes: Long): ZipInputStream {
        val input = BufferedInputStream(FileInputStream(this))
        var remaining = skipBytes
        while (remaining > 0) {
            val skipped = input.skip(remaining)
            if (skipped <= 0) break
            remaining -= skipped
        }
        return ZipInputStream(input)
    }
}
