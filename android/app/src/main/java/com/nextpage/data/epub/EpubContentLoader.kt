package com.nextpage.data.epub

import android.content.Context
import android.util.Log
import java.io.InputStream
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

    private var cachedBook: MutableMap<String, EpubBook> = mutableMapOf()

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
            val chapters = mutableListOf<Chapter>()
            var basePath = ""
            var opfPath = ""

            java.io.FileInputStream(filePath).use { fis ->
                ZipInputStream(fis).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        val name = entry.name
                        if (name.endsWith(".opf")) {
                            opfPath = name
                            basePath = name.substringBeforeLast("/")
                            if (basePath.isNotEmpty()) basePath += "/"
                        }
                        if (name.endsWith(".xhtml") || name.endsWith(".html") || name.endsWith(".htm")) {
                            val title = name.substringAfterLast("/")
                                .substringBeforeLast(".")
                                .replace("-", " ")
                                .replace("_", " ")
                                .replaceFirstChar { it.uppercase() }
                            chapters.add(
                                Chapter(
                                    index = chapters.size,
                                    id = name.substringBeforeLast("."),
                                    title = title,
                                    href = name
                                )
                            )
                        }
                        entry = zis.nextEntry
                    }
                }
            }

            if (chapters.isEmpty()) {
                return Result.failure(Exception("No chapters found in EPUB"))
            }

            val book = EpubBook(
                chapters = chapters.sortedBy { it.index },
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

    private fun stripHtmlToPlainText(html: String): String {
        return html
            .replace(Regex("<script[^>]*>.*?</script>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<style[^>]*>.*?</style>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<[^>]+>".toRegex()), "\n")
            .replace(Regex("&nbsp;"), " ")
            .replace(Regex("&amp;"), "&")
            .replace(Regex("&lt;"), "<")
            .replace(Regex("&gt;"), ">")
            .replace(Regex("&quot;"), "\"")
            .replace(Regex("&#\\d+;".toRegex()), "")
            .replace(Regex("\\n\\s*\\n"), "\n\n")
            .trim()
    }

    fun clearCache() {
        cachedBook.clear()
    }
}