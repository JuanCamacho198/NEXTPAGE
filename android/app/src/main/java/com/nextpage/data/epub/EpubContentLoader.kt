package com.nextpage.data.epub

import android.content.Context
import android.util.Log
import java.util.zip.ZipInputStream

class EpubContentLoader(private val context: Context) {
    companion object {
        private const val TAG = "EpubContentLoader"
    }

    data class Chapter(
        val index: Int,
        val id: String,
        val title: String,
        val href: String
    )

    data class EpubBook(
        val chapters: List<Chapter>,
        val basePath: String,
        val opfPath: String
    )

    private val cachedBook: MutableMap<String, EpubBook> = mutableMapOf()

    fun loadEpub(filePath: String): Result<EpubBook> {
        return try {
            val startTime = System.currentTimeMillis()

            cachedBook[filePath]?.let { return Result.success(it) }

            val epubFile = java.io.File(filePath)
            if (!epubFile.exists()) {
                Log.w(TAG, "EPUB file not found: $filePath")
                return Result.failure(Exception("EPUB file not found"))
            }

            val result = parseEpubStructure(filePath)
            result.onSuccess { book ->
                val elapsed = System.currentTimeMillis() - startTime
                Log.d(TAG, "EPUB loaded in ${elapsed}ms: ${book.chapters.size} chapters")
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load EPUB: ${e.message}")
            Result.failure(e)
        }
    }

    private fun parseEpubStructure(filePath: String): Result<EpubBook> {
        return try {
            val textEntries = mutableMapOf<String, String>()
            var fallbackOpfPath = ""

            java.io.FileInputStream(filePath).use { fis ->
                ZipInputStream(fis).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        val name = entry.name
                        if (!entry.isDirectory && isReadableTextEntry(name)) {
                            textEntries[name] = zis.bufferedReader().readText()
                            if (name.endsWith(".opf") && fallbackOpfPath.isBlank()) {
                                fallbackOpfPath = name
                            }
                        }
                        entry = zis.nextEntry
                    }
                }
            }

            val opfPath = parseContainerForOpfPath(textEntries["META-INF/container.xml"])
                ?: fallbackOpfPath
            val basePath = opfPath.substringBeforeLast("/", "").let {
                if (it.isNotBlank()) "$it/" else ""
            }
            val chapters = parseSpineChapters(
                opfPath = opfPath,
                opfContent = textEntries[opfPath],
                basePath = basePath
            ).ifEmpty {
                parseFallbackChapters(textEntries.keys)
            }

            if (chapters.isEmpty()) {
                return Result.failure(Exception("No chapters found in EPUB"))
            }

            val book = EpubBook(
                chapters = chapters,
                basePath = basePath,
                opfPath = opfPath
            )
            cachedBook[filePath] = book
            Result.success(book)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getChapterContent(filePath: String, chapterHref: String): Result<String> {
        return try {
            val startTime = System.currentTimeMillis()

            java.io.FileInputStream(filePath).use { fis ->
                ZipInputStream(fis).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        if (entry.name == chapterHref) {
                            val content = zis.bufferedReader().readText()
                            val elapsed = System.currentTimeMillis() - startTime
                            Log.d(TAG, "Chapter loaded in ${elapsed}ms: $chapterHref")
                            return Result.success(stripHtmlToPlainText(content))
                        }
                        entry = zis.nextEntry
                    }
                }
            }
            Result.failure(Exception("Chapter not found: $chapterHref"))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load chapter: ${e.message}")
            Result.failure(e)
        }
    }

    private fun isReadableTextEntry(entryName: String): Boolean {
        return entryName.endsWith(".opf") ||
            entryName.endsWith(".xhtml") ||
            entryName.endsWith(".html") ||
            entryName.endsWith(".htm") ||
            entryName == "META-INF/container.xml"
    }

    private fun parseContainerForOpfPath(containerXml: String?): String? {
        if (containerXml.isNullOrBlank()) return null
        val match = Regex("full-path\\s*=\\s*[\"']([^\"']+)[\"']").find(containerXml)
        return match?.groupValues?.getOrNull(1)
    }

    private fun parseSpineChapters(
        opfPath: String,
        opfContent: String?,
        basePath: String
    ): List<Chapter> {
        if (opfPath.isBlank() || opfContent.isNullOrBlank()) return emptyList()

        val manifest = mutableMapOf<String, String>()
        val itemRegex = Regex(
            "<item\\b[^>]*\\bid\\s*=\\s*[\"']([^\"']+)[\"'][^>]*\\bhref\\s*=\\s*[\"']([^\"']+)[\"'][^>]*/?>",
            RegexOption.IGNORE_CASE
        )
        itemRegex.findAll(opfContent).forEach { match ->
            manifest[match.groupValues[1]] = match.groupValues[2]
        }

        val idRefRegex = Regex(
            "<itemref\\b[^>]*\\bidref\\s*=\\s*[\"']([^\"']+)[\"'][^>]*/?>",
            RegexOption.IGNORE_CASE
        )
        val chapters = idRefRegex.findAll(opfContent)
            .mapNotNull { match ->
                val idRef = match.groupValues[1]
                val href = manifest[idRef] ?: return@mapNotNull null
                if (!isChapterFile(href)) return@mapNotNull null
                val resolved = resolveRelativePath(basePath, href)
                Chapter(
                    index = 0,
                    id = idRef,
                    title = titleFromHref(href),
                    href = resolved
                )
            }
            .toList()

        return chapters.mapIndexed { index, chapter -> chapter.copy(index = index) }
    }

    private fun parseFallbackChapters(entryNames: Set<String>): List<Chapter> {
        return entryNames
            .filter(::isChapterFile)
            .sorted()
            .mapIndexed { index, name ->
                Chapter(
                    index = index,
                    id = name.substringBeforeLast("."),
                    title = titleFromHref(name),
                    href = name
                )
            }
    }

    private fun isChapterFile(path: String): Boolean {
        return path.endsWith(".xhtml", ignoreCase = true) ||
            path.endsWith(".html", ignoreCase = true) ||
            path.endsWith(".htm", ignoreCase = true)
    }

    private fun titleFromHref(path: String): String {
        return path.substringAfterLast("/")
            .substringBeforeLast(".")
            .replace("-", " ")
            .replace("_", " ")
            .replaceFirstChar { it.uppercase() }
    }

    private fun resolveRelativePath(basePath: String, relativePath: String): String {
        if (relativePath.startsWith("/")) return relativePath.removePrefix("/")
        if (basePath.isBlank()) return relativePath

        val baseParts = basePath
            .removeSuffix("/")
            .split('/')
            .filter { it.isNotBlank() }
            .toMutableList()
        val relParts = relativePath.split('/')

        relParts.forEach { part ->
            when (part) {
                "", "." -> Unit
                ".." -> if (baseParts.isNotEmpty()) baseParts.removeAt(baseParts.lastIndex)
                else -> baseParts.add(part)
            }
        }

        return baseParts.joinToString("/")
    }

    private fun stripHtmlToPlainText(html: String): String {
        return html
            .replace(Regex("<script[^>]*>.*?</script>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<style[^>]*>.*?</style>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<[^>]+>"), "\n")
            .replace(Regex("&nbsp;"), " ")
            .replace(Regex("&amp;"), "&")
            .replace(Regex("&lt;"), "<")
            .replace(Regex("&gt;"), ">")
            .replace(Regex("&quot;"), "\"")
            .replace(Regex("&#\\d+;"), "")
            .replace(Regex("\\n\\s*\\n"), "\n\n")
            .trim()
    }

    fun clearCache() {
        cachedBook.clear()
    }
}
