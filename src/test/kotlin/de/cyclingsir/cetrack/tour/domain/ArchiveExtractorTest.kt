package de.cyclingsir.cetrack.tour.domain

import de.cyclingsir.cetrack.common.errorhandling.ErrorCodesDomain
import de.cyclingsir.cetrack.common.errorhandling.ServiceException
import de.cyclingsir.cetrack.tour.configuration.MyTourbookImportConfiguration
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path

@ExtendWith(MockKExtension::class)
class ArchiveExtractorTest {

    @MockK
    private lateinit var mockedConfig: MyTourbookImportConfiguration

    companion object {
        const val FIXTURE = "mytourbook-fixture/tourbook.tar.bz2"
        private const val LARGE_CAP = 500_000_000L
        private const val TINY_CAP = 512L
    }

    private fun makeTarBz2(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        BZip2CompressorOutputStream(out).use { bz2 ->
            TarArchiveOutputStream(bz2).use { tar ->
                for ((name, data) in entries) {
                    val entry = TarArchiveEntry(name)
                    entry.size = data.size.toLong()
                    tar.putArchiveEntry(entry)
                    tar.write(data)
                    tar.closeArchiveEntry()
                }
            }
        }
        return out.toByteArray()
    }

    // #15
    @Test
    fun `valid tar bz2 unpacks the tourbook dir to temp`(@TempDir tempDir: Path) {
        every { mockedConfig.maxDecompressedBytes } returns LARGE_CAP
        val extractor = ArchiveExtractor(mockedConfig)
        val fixture = ArchiveExtractorTest::class.java.classLoader.getResourceAsStream(FIXTURE)!!
        val tourBookDir = extractor.extract(fixture, tempDir)
        assertTrue(Files.exists(tourBookDir.resolve("service.properties")),
            "extracted tourbook dir must contain service.properties")
    }

    // #16
    @Test
    fun `path traversal entry is rejected`(@TempDir tempDir: Path) {
        every { mockedConfig.maxDecompressedBytes } returns LARGE_CAP
        val extractor = ArchiveExtractor(mockedConfig)
        val payload = makeTarBz2("../../evil.sh" to "rm -rf /".toByteArray())
        val ex = assertThrows<ServiceException> {
            extractor.extract(ByteArrayInputStream(payload), tempDir)
        }
        assertEquals(ErrorCodesDomain.ARCHIVE_INVALID, ex.getError())
    }

    // #17
    @Test
    fun `archive exceeding configured size cap yields ARCHIVE_EXCEEDS_SIZE_LIMIT`(@TempDir tempDir: Path) {
        every { mockedConfig.maxDecompressedBytes } returns TINY_CAP
        val extractor = ArchiveExtractor(mockedConfig)
        val bigData = ByteArray(TINY_CAP.toInt() + 1) { 65 }
        val payload = makeTarBz2("tourbook/service.properties" to bigData)
        val ex = assertThrows<ServiceException> {
            extractor.extract(ByteArrayInputStream(payload), tempDir)
        }
        assertEquals(ErrorCodesDomain.ARCHIVE_EXCEEDS_SIZE_LIMIT, ex.getError())
    }

    // #18
    @Test
    fun `corrupt or non-bz2 body yields ARCHIVE_INVALID`(@TempDir tempDir: Path) {
        every { mockedConfig.maxDecompressedBytes } returns LARGE_CAP
        val extractor = ArchiveExtractor(mockedConfig)
        val garbage = "this is not a bzip2 stream".toByteArray()
        val ex = assertThrows<ServiceException> {
            extractor.extract(ByteArrayInputStream(garbage), tempDir)
        }
        assertEquals(ErrorCodesDomain.ARCHIVE_INVALID, ex.getError())
    }

    // #19
    @Test
    fun `archive within configured cap stages without error`(@TempDir tempDir: Path) {
        every { mockedConfig.maxDecompressedBytes } returns LARGE_CAP
        val extractor = ArchiveExtractor(mockedConfig)
        val fixture = ArchiveExtractorTest::class.java.classLoader.getResourceAsStream(FIXTURE)!!
        val tourBookDir = extractor.extract(fixture, tempDir)
        assertTrue(Files.isDirectory(tourBookDir), "result must be a directory")
    }
}
