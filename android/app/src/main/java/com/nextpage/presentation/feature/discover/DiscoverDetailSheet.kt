package com.nextpage.presentation.feature.discover

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nextpage.R
import com.nextpage.data.remote.catalog.BUILTIN_GUTENDEX
import com.nextpage.data.remote.catalog.BUILTIN_OPENLIBRARY
import com.nextpage.data.remote.catalog.CatalogBook
import com.nextpage.domain.usecase.DownloadImportState
import com.nextpage.presentation.feature.library.formatFileSize
import com.nextpage.presentation.theme.NextPageColors
import com.nextpage.presentation.viewmodel.DiscoverDetailStatus
import com.nextpage.ui.components.atoms.NextPageBookCover
import com.nextpage.ui.components.atoms.NextPageButton
import com.nextpage.ui.components.atoms.NextPageButtonVariant
import com.nextpage.ui.components.atoms.NextPageDivider
import com.nextpage.ui.components.atoms.NextPageProgressBar

/**
 * A derivable off-app link for a catalog book, with the label that names the
 * actual destination (the CTA must not claim Open Library when it points at
 * Project Gutenberg).
 */
internal data class DiscoverExternalLink(val url: String, @param:StringRes val labelRes: Int)

/**
 * Detail bottom sheet (base, existing fields only).
 *
 * Deliberately NOT [com.nextpage.ui.components.atoms.NextPageBottomSheet]: that
 * atom forces a centered `titleLarge` header plus uniform 24dp padding, which
 * fights the designed head-row layout. Uses M3 [ModalBottomSheet] directly with
 * `skipPartiallyExpanded = true` and composes its own content from the shared
 * atoms ([NextPageBookCover], [NextPageDivider], [NextPageButton],
 * [DiscoverChipRow]).
 *
 * Every optional element is hidden when its field is absent — no placeholder
 * text, no empty chips row, no empty label. The download CTA is state-driven:
 * it downloads and imports in-app (progress, cancel, retry) and falls back to
 * opening `downloadUrl` externally only when no handler is supplied.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun DiscoverDetailSheet(
    detail: CatalogBook?,
    detailStatus: DiscoverDetailStatus,
    download: DownloadImportState = DownloadImportState.Idle,
    onDownload: (() -> Unit)? = null,
    onCancelDownload: () -> Unit = {},
    onDismiss: () -> Unit
) {
    if (detailStatus == DiscoverDetailStatus.CLOSED) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        scrimColor = Color(0x66000000),
        containerColor = NextPageColors.surface
    ) {
        when {
            detailStatus == DiscoverDetailStatus.LOADING ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
            detailStatus == DiscoverDetailStatus.NOT_FOUND || detail == null ->
                Text(
                    text = stringResource(
                        if (detailStatus == DiscoverDetailStatus.NOT_FOUND) {
                            R.string.discover_detail_not_found
                        } else {
                            R.string.discover_error_upstream
                        }
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = NextPageColors.textSecondary,
                    modifier = Modifier.padding(24.dp)
                )
            else -> DiscoverDetailContent(
                detail = detail,
                download = download,
                onDownload = onDownload,
                onCancelDownload = onCancelDownload
            )
        }
    }
}

@Composable
private fun DiscoverDetailContent(
    detail: CatalogBook,
    download: DownloadImportState,
    onDownload: (() -> Unit)?,
    onCancelDownload: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    val externalLink = discoverExternalLink(detail)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        DiscoverDetailHead(detail = detail)

        if (detail.subjects.isNotEmpty()) {
            DiscoverDetailSubjectSection(subjects = detail.subjects)
        }

        DiscoverDetailParagraph(
            labelRes = R.string.discover_detail_description_label,
            body = discoverDescription(detail)
        )

        val formatLabels = discoverFormatLabels(detail.formats)
        if (formatLabels.isNotEmpty()) {
            DiscoverDetailChipRow(labels = formatLabels.map { stringResource(it) })
        }

        NextPageDivider()

        detail.downloadUrl?.let { url ->
            if (onDownload != null) {
                DiscoverDownloadCta(
                    download = download,
                    onDownload = onDownload,
                    onCancelDownload = onCancelDownload
                )
            } else {
                // Host without the download bridge (e.g. the "Ver todo" section
                // screen): preserve the external-open CTA rather than a dead one.
                NextPageButton(
                    onClick = { uriHandler.openUri(url) },
                    variant = NextPageButtonVariant.FILLED,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.discover_download),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        externalLink?.let { link ->
            NextPageButton(
                onClick = { uriHandler.openUri(link.url) },
                variant = NextPageButtonVariant.OUTLINED,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Rounded.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(link.labelRes),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

/**
 * State-driven download CTA: `Idle` enables the download, in-flight states show
 * progress plus an explicit cancel, `Success` is a disabled confirmation,
 * `Duplicate` is a neutral message and `Failure` is an inline error with retry.
 */
@Composable
private fun DiscoverDownloadCta(
    download: DownloadImportState,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        when (download) {
            is DownloadImportState.Idle -> {
                NextPageButton(
                    onClick = onDownload,
                    variant = NextPageButtonVariant.FILLED,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.discover_download),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            is DownloadImportState.Downloading -> {
                val total = download.totalBytes
                if (total != null && total > 0L) {
                    NextPageProgressBar(
                        progress = download.bytesSoFar.toFloat() / total.toFloat(),
                        showPercentage = true
                    )
                    Text(
                        text = "${formatFileSize(download.bytesSoFar)} / ${formatFileSize(total)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = NextPageColors.textSecondary
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(
                        text = formatFileSize(download.bytesSoFar),
                        style = MaterialTheme.typography.labelSmall,
                        color = NextPageColors.textSecondary
                    )
                }
                NextPageButton(
                    onClick = onCancelDownload,
                    variant = NextPageButtonVariant.OUTLINED,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.discover_download_cancel),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            is DownloadImportState.Importing -> {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                NextPageButton(
                    onClick = onCancelDownload,
                    variant = NextPageButtonVariant.OUTLINED,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.discover_download_cancel),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            is DownloadImportState.Success -> {
                NextPageButton(
                    onClick = { },
                    enabled = false,
                    variant = NextPageButtonVariant.FILLED,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.discover_imported),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            is DownloadImportState.Duplicate -> {
                Text(
                    text = stringResource(R.string.discover_duplicate),
                    style = MaterialTheme.typography.bodyMedium,
                    color = NextPageColors.textSecondary
                )
            }
            is DownloadImportState.Failure -> {
                Text(
                    text = stringResource(R.string.discover_download_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = NextPageColors.errorSoft
                )
                NextPageButton(
                    onClick = onDownload,
                    variant = NextPageButtonVariant.OUTLINED,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.discover_retry),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

/** Head row: 72x108 cover, title, author line and the language chips. */
@Composable
private fun DiscoverDetailHead(detail: CatalogBook) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (detail.coverUrl.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .size(width = 72.dp, height = 108.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(NextPageColors.surface),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = bookInitial(detail.title),
                    style = MaterialTheme.typography.headlineMedium.copy(fontSize = 28.sp),
                    fontWeight = FontWeight.Bold,
                    color = NextPageColors.primary
                )
            }
        } else {
            NextPageBookCover(
                coverUrl = detail.coverUrl,
                title = detail.title,
                modifier = Modifier.size(width = 72.dp, height = 108.dp)
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = detail.title,
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
                fontWeight = FontWeight.SemiBold,
                color = NextPageColors.textPrimary
            )
            if (detail.authors.isNotEmpty()) {
                Text(
                    text = stringResource(
                        R.string.discover_by_authors,
                        detail.authors.joinToString(", ")
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = NextPageColors.textSecondary
                )
            }
            if (detail.languages.isNotEmpty()) {
                Spacer(modifier = Modifier.size(2.dp))
                DiscoverDetailChipRow(
                    labels = detail.languages,
                    contentPadding = PaddingValues(horizontal = 0.dp)
                )
            }
        }
    }
}

/** "TEMAS" label over the subject chips, both on the `bg_card_hover` token. */
@Composable
private fun DiscoverDetailSubjectSection(subjects: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DiscoverDetailLabel(text = stringResource(R.string.discover_detail_subjects_label))
        DiscoverDetailChipRow(labels = subjects)
    }
}

/**
 * Labeled paragraph block. Hides entirely when [body] is null — the hiding rule
 * for a field the model cannot provide yet.
 */
@Composable
private fun DiscoverDetailParagraph(@StringRes labelRes: Int, body: String?) {
    if (body.isNullOrBlank()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        DiscoverDetailLabel(text = stringResource(labelRes))
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
            color = NextPageColors.textSecondary
        )
    }
}

@Composable
private fun DiscoverDetailLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
        color = NextPageColors.textSecondary
    )
}

/** Non-interactive chip row rendered on `bg_card_hover`. */
@Composable
private fun DiscoverDetailChipRow(
    labels: List<String>,
    contentPadding: PaddingValues = PaddingValues(horizontal = 0.dp)
) {
    DiscoverChipRow(
        chips = labels.map { DiscoverChip(label = it) },
        onChipClick = { },
        contentPadding = contentPadding,
        chipBackground = NextPageColors.surfaceVariant,
        chipLabelColor = NextPageColors.textSecondary
    )
}

/**
 * Off-app URL derivable from fields that already exist on the model. Returns null
 * for anything that is not a resolvable first-party id, which hides the CTA.
 */
internal fun discoverExternalLink(book: CatalogBook): DiscoverExternalLink? {
    if (book.provider == BUILTIN_GUTENDEX) {
        val numericId = book.id.removePrefix("gutendex:").toIntOrNull()
        if (numericId != null && numericId > 0) {
            return DiscoverExternalLink(
                url = "https://www.gutenberg.org/ebooks/$numericId",
                labelRes = R.string.discover_detail_external_gutenberg
            )
        }
        return null
    }
    if (book.provider == BUILTIN_OPENLIBRARY) {
        val key = book.id.removePrefix("openlibrary:")
        if (key.startsWith("/")) {
            return DiscoverExternalLink(
                url = "https://openlibrary.org$key",
                labelRes = R.string.discover_detail_external_openlibrary
            )
        }
        return null
    }
    return null
}

/**
 * Format labels derived from the real `formats` map keys, in a stable display
 * order. Unknown keys are skipped; an empty map yields an empty list so the
 * format row hides.
 */
internal fun discoverFormatLabels(formats: Map<String, String>): List<Int> {
    val byMime = listOf(
        "application/epub+zip" to R.string.discover_format_epub,
        "application/x-mobipocket-ebook" to R.string.discover_format_mobi,
        "text/plain; charset=utf-8" to R.string.discover_format_txt,
        "text/html" to R.string.discover_format_html
    )
    return byMime.filter { formats.containsKey(it.first) }.map { it.second }
}

/**
 * Description text for the DESCRIPCIÓN block. Null/blank hides the block rather
 * than showing a placeholder paragraph.
 */
internal fun discoverDescription(book: CatalogBook): String? =
    book.description?.takeIf { it.isNotBlank() }
