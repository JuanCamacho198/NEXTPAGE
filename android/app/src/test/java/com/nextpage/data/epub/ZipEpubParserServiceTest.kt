package com.nextpage.data.epub

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking

class ZipEpubParserServiceTest {
    private val parser = ZipEpubParserService()

    @Test
    fun extractMetadata_returnsTitleAuthorAndCoverWhenPresent() = runBlocking {
        val coverBytes = byteArrayOf(1, 2, 3, 4)
        val epubBytes = buildEpub(
            entries = mapOf(
                "META-INF/container.xml" to """
                    <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                        <rootfiles>
                            <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                        </rootfiles>
                    </container>
                """.trimIndent().toByteArray(),
                "OEBPS/content.opf" to """
                    <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                        <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                            <dc:title>Sample Book</dc:title>
                            <dc:creator>Jane Doe</dc:creator>
                            <meta name="cover" content="cover-image" />
                        </metadata>
                        <manifest>
                            <item id="cover-image" href="images/cover.jpg" media-type="image/jpeg"/>
                        </manifest>
                    </package>
                """.trimIndent().toByteArray(),
                "OEBPS/images/cover.jpg" to coverBytes
            )
        )

        val result = parser.extractMetadata(ByteArrayInputStream(epubBytes))

        assertTrue(result.isSuccess)
        val metadata = result.getOrThrow()
        assertEquals("Sample Book", metadata.title)
        assertEquals("Jane Doe", metadata.author)
        assertNotNull(metadata.coverImageBytes)
        assertArrayEquals(coverBytes, metadata.coverImageBytes)
    }

    @Test
    fun extractMetadata_returnsFailureForInvalidEpub() = runBlocking {
        val invalidEpubBytes = buildEpub(
            entries = mapOf(
                "book.txt" to "not an epub".toByteArray()
            )
        )

        val result = parser.extractMetadata(ByteArrayInputStream(invalidEpubBytes))

        assertTrue(result.isFailure)
    }

    private fun buildEpub(entries: Map<String, ByteArray>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zos ->
            entries.forEach { (path, bytes) ->
                zos.putNextEntry(ZipEntry(path))
                zos.write(bytes)
                zos.closeEntry()
            }
        }
        return output.toByteArray()
    }
}
