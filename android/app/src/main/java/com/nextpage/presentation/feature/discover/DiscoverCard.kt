package com.nextpage.presentation.feature.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.nextpage.R
import com.nextpage.data.remote.catalog.CatalogBook
import com.nextpage.presentation.theme.NextPageColors

/**
 * Remote cover with the shared letter-initial fallback used by the results grid,
 * the IDLE rails and the detail sheet.
 *
 * Branches: the real Coil cover when [coverUrl] is present and loading succeeded,
 * otherwise the book's initial in the brand color on `bg_surface`. A failed load
 * degrades to the letter art instead of leaving a broken image box.
 *
 * @param letterSize Glyph size for the fallback. The design scales the initial
 *   with the surface (48sp for rail/grid covers, 28sp for the detail head).
 */
@Composable
internal fun DiscoverBookCover(
    coverUrl: String?,
    title: String,
    modifier: Modifier = Modifier,
    letterSize: TextUnit = 32.sp
) {
    var coverFailed by remember(coverUrl) { mutableStateOf(false) }
    val showCover = !coverUrl.isNullOrBlank() && !coverFailed

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(NextPageColors.surface),
        contentAlignment = Alignment.Center
    ) {
        if (showCover) {
            AsyncImage(
                model = coverUrl,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                placeholder = painterResource(R.drawable.cover_placeholder),
                error = painterResource(R.drawable.cover_error),
                onError = { coverFailed = true },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                text = bookInitial(title),
                style = MaterialTheme.typography.headlineLarge.copy(fontSize = letterSize),
                fontWeight = FontWeight.Bold,
                color = NextPageColors.primary
            )
        }
    }
}

/** First glyph of the title, uppercased; `?` for an empty title (never a blank box). */
internal fun bookInitial(title: String): String =
    title.trim().firstOrNull()?.uppercase() ?: "?"

/** Fixed results-grid cell: cover + title (`text_primary`) + author (`text_secondary`). */
@Composable
fun DiscoverCard(
    book: CatalogBook,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
    attributionNames: Map<String, String> = emptyMap()
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onOpen(book.id) },
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        DiscoverBookCover(
            coverUrl = book.coverUrl,
            title = book.title,
            letterSize = 48.sp,
            modifier = Modifier
                .fillMaxWidth()
                .height(GRID_COVER_HEIGHT)
        )
        Text(
            text = book.title,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = NextPageColors.textPrimary
        )
        Text(
            text = book.authors.joinToString(", "),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = NextPageColors.textSecondary
        )
        SourceAttributionBadge(
            provider = book.provider,
            attributionNames = attributionNames
        )
    }
}

/** Cover area height shared by the results grid and the section list. */
internal val GRID_COVER_HEIGHT = 160.dp
