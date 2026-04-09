package com.nextpage.data.epub

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

class ZipEpubParserService : EpubParserService {
    override suspend fun extractMetadata(inputStream: InputStream): Result<EpubMetadata> = runCatching {
        val zipBytes = inputStream.use { it.readBytes() }
        val rootFilePath = readRootFilePath(zipBytes)
            ?: throw IllegalArgumentException("Invalid EPUB: missing rootfile in container.xml")

        val packageDocument = readZipEntryBytes(zipBytes, rootFilePath)
            ?: throw IllegalArgumentException("Invalid EPUB: missing OPF package document")

        val parsed = parsePackageDocument(packageDocument)
        val title = parsed.title?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("Invalid EPUB: metadata title missing")

        val coverBytes = parsed.coverHref
            ?.let { href ->
                resolvePath(rootFilePath, href)
            }
            ?.let { coverPath -> readZipEntryBytes(zipBytes, coverPath) }

        EpubMetadata(
            title = title,
            author = parsed.author?.takeIf { it.isNotBlank() },
            coverImageBytes = coverBytes
        )
    }

    private fun readRootFilePath(zipBytes: ByteArray): String? {
        val containerXml = readZipEntryBytes(zipBytes, CONTAINER_XML_PATH) ?: return null
        val parser = newParser(containerXml)

        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "rootfile") {
                return parser.getAttributeValue(null, "full-path")
                    ?: parser.getAttributeValue("", "full-path")
            }
            parser.next()
        }
        return null
    }

    private fun parsePackageDocument(opfBytes: ByteArray): ParsedPackageDocument {
        val parser = newParser(opfBytes)
        var title: String? = null
        var author: String? = null
        val manifestById = mutableMapOf<String, String>()
        var coverItemId: String? = null

        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "dc:title", "title" -> title = parser.readSimpleText()
                    "dc:creator", "creator" -> author = parser.readSimpleText()
                    "meta" -> {
                        val nameAttr = parser.getAttributeValue(null, "name")
                            ?: parser.getAttributeValue("", "name")
                        if (nameAttr == "cover") {
                            coverItemId = parser.getAttributeValue(null, "content")
                                ?: parser.getAttributeValue("", "content")
                        }
                    }
                    "item" -> {
                        val id = parser.getAttributeValue(null, "id")
                            ?: parser.getAttributeValue("", "id")
                        val href = parser.getAttributeValue(null, "href")
                            ?: parser.getAttributeValue("", "href")
                        if (!id.isNullOrBlank() && !href.isNullOrBlank()) {
                            manifestById[id] = href
                        }
                    }
                }
            }
            parser.next()
        }

        return ParsedPackageDocument(
            title = title,
            author = author,
            coverHref = coverItemId?.let(manifestById::get)
        )
    }

    private fun readZipEntryBytes(zipBytes: ByteArray, entryPath: String): ByteArray? {
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                if (entry.name == entryPath) {
                    return zis.readBytes()
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        return null
    }

    private fun resolvePath(basePath: String, relativePath: String): String {
        if (!relativePath.contains("../")) {
            val baseDir = basePath.substringBeforeLast('/', "")
            return if (baseDir.isBlank()) relativePath else "$baseDir/$relativePath"
        }

        val baseParts = basePath.substringBeforeLast('/', "")
            .split('/')
            .filter { it.isNotBlank() }
            .toMutableList()
        val relativeParts = relativePath.split('/').filter { it.isNotBlank() }

        relativeParts.forEach { part ->
            when (part) {
                "." -> Unit
                ".." -> if (baseParts.isNotEmpty()) baseParts.removeAt(baseParts.lastIndex)
                else -> baseParts.add(part)
            }
        }

        return baseParts.joinToString("/")
    }

    private fun newParser(bytes: ByteArray): XmlPullParser {
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = true
        return factory.newPullParser().apply {
            setInput(ByteArrayInputStream(bytes), Charsets.UTF_8.name())
        }
    }

    private fun XmlPullParser.readSimpleText(): String? {
        var captured: String? = null
        while (next() != XmlPullParser.END_TAG) {
            if (eventType == XmlPullParser.TEXT) {
                val value = text?.trim()
                if (!value.isNullOrBlank()) {
                    captured = value
                }
            }
        }
        return captured
    }

    private data class ParsedPackageDocument(
        val title: String?,
        val author: String?,
        val coverHref: String?
    )

    private companion object {
        const val CONTAINER_XML_PATH = "META-INF/container.xml"
    }
}
