package com.nextpage.ui.components.molecules

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.nextpage.presentation.theme.NextPageDimens
import com.nextpage.ui.components.atoms.NextPageSkeletonBox

/**
 * Book-shaped loading placeholders for Home and Library.
 *
 * These are the "per-section" skeletons required by the loading-skeletons
 * capability: each one mirrors the geometry of the real content so the layout
 * does not jump when the first Room emission arrives. They render static
 * blocks only (no lazy layouts) so they can safely live inside an existing
 * `LazyColumn` / `LazyVerticalStaggeredGrid` item without creating a nested
 * scroll container.
 *
 * All placeholders reuse [NextPageSkeletonBox] (the app's existing shimmer atom)
 * and carry no user-facing text, so no string resources are needed.
 */

/** Container alpha matching the real book cards' tinted surface. */
private const val CONTAINER_ALPHA = 0.3f

/** Corner radius matching [BookListCard] / [BookGridCard]. */
private val CARD_CORNER = 12.dp

/**
 * Home Continue Reading carousel placeholder: a section-title bar followed by
 * a row of horizontal book cards (cover + two text lines + progress bar).
 *
 * @param cards Number of card placeholders to render.
 */
@Composable
fun ContinueReadingSkeleton(modifier: Modifier = Modifier, cards: Int = 3) {
    Column(modifier = modifier.fillMaxWidth()) {
        NextPageSkeletonBox(modifier = Modifier.width(160.dp).height(20.dp))
        Spacer(modifier = Modifier.height(NextPageDimens.spacingSm))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(cards) { ContinueReadingCardSkeleton() }
        }
    }
}

@Composable
private fun ContinueReadingCardSkeleton() {
    Row(
        modifier = Modifier
            .width(240.dp)
            .clip(RoundedCornerShape(NextPageDimens.spacingSm))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = CONTAINER_ALPHA))
            .padding(NextPageDimens.spacingMd)
    ) {
        NextPageSkeletonBox(
            modifier = Modifier.width(80.dp).height(120.dp),
            radius = NextPageDimens.spacingXs
        )
        Spacer(modifier = Modifier.width(NextPageDimens.spacingMd))
        Column(modifier = Modifier.weight(1f)) {
            NextPageSkeletonBox(modifier = Modifier.fillMaxWidth().height(14.dp))
            Spacer(modifier = Modifier.height(NextPageDimens.spacingSm))
            NextPageSkeletonBox(modifier = Modifier.fillMaxWidth(0.6f).height(12.dp))
            Spacer(modifier = Modifier.height(NextPageDimens.spacingMd))
            NextPageSkeletonBox(modifier = Modifier.fillMaxWidth().height(8.dp), radius = 4.dp)
        }
    }
}

/**
 * Library list-view placeholder: [rows] stacked rows mirroring [BookListCard]
 * (60x80 cover + title/author lines).
 *
 * @param rows Number of row placeholders to render.
 */
@Composable
fun BookListSkeleton(modifier: Modifier = Modifier, rows: Int = 5) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        repeat(rows) { BookListRowSkeleton() }
    }
}

@Composable
private fun BookListRowSkeleton() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CARD_CORNER))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = CONTAINER_ALPHA))
            .padding(12.dp)
    ) {
        NextPageSkeletonBox(modifier = Modifier.width(60.dp).height(80.dp), radius = 8.dp)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            NextPageSkeletonBox(modifier = Modifier.fillMaxWidth().height(14.dp))
            Spacer(modifier = Modifier.height(6.dp))
            NextPageSkeletonBox(modifier = Modifier.fillMaxWidth(0.5f).height(12.dp))
        }
    }
}

/**
 * Library grid-view placeholder: two-column rows mirroring [BookGridCard]
 * (220dp cover + title/author lines). An odd [cards] count leaves the trailing
 * cell as an empty spacer so the last row keeps its half width.
 *
 * @param cards Number of card placeholders to render.
 */
@Composable
fun BookGridSkeleton(modifier: Modifier = Modifier, cards: Int = 6) {
    val rows = (cards + 1) / 2
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        repeat(rows) { rowIndex ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                repeat(2) { columnIndex ->
                    if (rowIndex * 2 + columnIndex < cards) {
                        BookGridCardSkeleton(modifier = Modifier.weight(1f))
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun BookGridCardSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(CARD_CORNER))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = CONTAINER_ALPHA))
    ) {
        NextPageSkeletonBox(modifier = Modifier.fillMaxWidth().height(220.dp), radius = 0.dp)
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            NextPageSkeletonBox(modifier = Modifier.fillMaxWidth().height(14.dp))
            NextPageSkeletonBox(modifier = Modifier.fillMaxWidth(0.5f).height(12.dp))
        }
    }
}

/**
 * Home Quick Access placeholder: a section-title bar above a 2x2 tile grid
 * mirroring the four [QuickAccessButton] tiles.
 */
@Composable
fun QuickAccessSkeleton(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        NextPageSkeletonBox(modifier = Modifier.width(120.dp).height(20.dp))
        Spacer(modifier = Modifier.height(NextPageDimens.spacingSm))
        Column(verticalArrangement = Arrangement.spacedBy(NextPageDimens.spacingSm)) {
            repeat(2) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(NextPageDimens.spacingSm)
                ) {
                    repeat(2) {
                        NextPageSkeletonBox(
                            modifier = Modifier.weight(1f).height(108.dp),
                            radius = NextPageDimens.spacingSm
                        )
                    }
                }
            }
        }
    }
}
