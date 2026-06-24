package de.cyclingsir.cetrack.tour.domain

import de.cyclingsir.cetrack.common.errorhandling.ErrorCodesDomain
import de.cyclingsir.cetrack.common.errorhandling.ServiceException
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path

@Component
class ArchiveExtractor(
    @Value("\${app.mytourbook.max-decompressed-bytes}") val maxDecompressedBytes: Long
) {
    fun extract(inputStream: InputStream, destDir: Path): Path {
        try {
            unpack(inputStream, destDir)
        } catch (e: ServiceException) {
            throw e
        } catch (e: Exception) {
            throw ServiceException(ErrorCodesDomain.ARCHIVE_INVALID, "Archive unreadable: ${e.message}")
        }
        return Files.walk(destDir)
            .filter { Files.exists(it.resolve("service.properties")) }
            .findFirst()
            .orElseThrow { ServiceException(ErrorCodesDomain.ARCHIVE_INVALID, "No Derby database found in archive") }
    }

    private fun unpack(inputStream: InputStream, destDir: Path) {
        var totalBytes = 0L
        TarArchiveInputStream(BZip2CompressorInputStream(inputStream.buffered())).use { tar ->
            generateSequence { tar.nextEntry }
                .filterNot { it.isDirectory }
                .forEach { entry ->
                    totalBytes = extractEntry(tar, entry, destDir, totalBytes)
                }
        }
    }

    private fun extractEntry(
        tar: TarArchiveInputStream,
        entry: TarArchiveEntry,
        destDir: Path,
        bytesRead: Long
    ): Long {
        val dest = destDir.resolve(entry.name)
        if (!dest.normalize().startsWith(destDir.normalize())) {
            throw ServiceException(ErrorCodesDomain.ARCHIVE_INVALID, "Path traversal: ${entry.name}")
        }
        Files.createDirectories(dest.parent)
        return Files.newOutputStream(dest).use { out ->
            writeEntryData(tar, out, bytesRead)
        }
    }

    private fun writeEntryData(
        tar: TarArchiveInputStream,
        out: OutputStream,
        initialBytes: Long
    ): Long {
        var totalBytes = initialBytes
        val buf = ByteArray(8192)
        var n: Int
        while (tar.read(buf).also { n = it } != -1) {
            totalBytes += n
            if (totalBytes > maxDecompressedBytes) {
                throw ServiceException(
                    ErrorCodesDomain.ARCHIVE_EXCEEDS_SIZE_LIMIT,
                    "Archive exceeds size limit of ${maxDecompressedBytes.toMb()} MB when decompressed"
                )
            }
            out.write(buf, 0, n)
        }
        return totalBytes
    }

    private fun Long.toMb(): String = "%.2f".format(this / (1024.0 * 1024.0))
}
