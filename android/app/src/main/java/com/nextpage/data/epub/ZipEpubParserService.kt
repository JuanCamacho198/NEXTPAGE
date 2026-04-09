package com.nextpage.data.epub

import java.io.ByteArrayInputStream
import java.io.InputStream
import javax.xml.XMLConstants
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

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
        val document = parseXml(containerXml)
        val rootFiles = document.getElementsByTagName("*")
        for (index in 0 until rootFiles.length) {
            val node = rootFiles.item(index)
            if (node is Element && node.localTagName() == "rootfile") {
                return node.getAttribute("full-path").takeIf { it.isNotBlank() }
            }
        }
        return null
    }

    private fun parsePackageDocument(opfBytes: ByteArray): ParsedPackageDocument {
        val document = parseXml(opfBytes)
        val title = firstElementTextByLocalName(document, "title")
        val author = firstElementTextByLocalName(document, "creator")
        val manifestById = mutableMapOf<String, String>()
        val allElements = document.getElementsByTagName("*")
        var coverItemId: String? = null

        for (index in 0 until allElements.length) {
            val node = allElements.item(index)
            if (node !is Element) continue

            when (node.localTagName()) {
                "meta" -> {
                    if (node.getAttribute("name") == "cover") {
                        coverItemId = node.getAttribute("content").takeIf { it.isNotBlank() }
                    }
                }
                "item" -> {
                    val id = node.getAttribute("id").takeIf { it.isNotBlank() }
                    val href = node.getAttribute("href").takeIf { it.isNotBlank() }
                    if (id != null && href != null) {
                        manifestById[id] = href
                    }
                }
            }
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

    private fun parseXml(bytes: ByteArray): Document {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        }
        return factory.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
    }

    private fun firstElementTextByLocalName(document: Document, localName: String): String? {
        val allElements = document.getElementsByTagName("*")
        for (index in 0 until allElements.length) {
            val node = allElements.item(index)
            if (node is Element && node.localTagName() == localName) {
                val value = node.textContent?.trim()
                if (!value.isNullOrBlank()) {
                    return value
                }
            }
        }
        return null
    }

    private fun Node.localTagName(): String = localName ?: nodeName.substringAfter(':', nodeName)

    private data class ParsedPackageDocument(
        val title: String?,
        val author: String?,
        val coverHref: String?
    )

    private companion object {
        const val CONTAINER_XML_PATH = "META-INF/container.xml"
    }
}
