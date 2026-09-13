package com.nextpage.presentation.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nextpage.R
import com.nextpage.domain.model.Book
import com.nextpage.presentation.theme.NextPageDimens
import com.nextpage.ui.components.atoms.CoverThumbnail
import com.nextpage.ui.components.atoms.NextPageButton
import com.nextpage.ui.components.atoms.NextPageButtonVariant
import com.nextpage.ui.components.atoms.NextPageProgressBar
import com.nextpage.ui.components.molecules.BookContextMenu
import com.nextpage.ui.icons.NextPageIcons

@Composable
fun ContinueReadingSection(books: List<Book>, progressPercentByBook: Map<String, Float> = emptyMap(), onBookSelected: (String, String, String) -> Unit, onContinueReading: (String, String?, String) -> Unit, onEdit: (Book) -> Unit = {}, onMarkCompleted: (Book) -> Unit = {}, onMarkPlanToRead: (Book) -> Unit = {}, onShare: (Book) -> Unit = {}, onDelete: (Book) -> Unit = {}) {
    Column {
        Text(text = stringResource(R.string.home_continue_reading), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(NextPageDimens.spacingSm))
        if (books.isNotEmpty()) {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(books, key = { it.id }) { book ->
                    val canonicalProgress = progressPercentByBook[book.id] ?: 0f
                    ContinueReadingCard(book = book, progressFraction = (canonicalProgress / 100f).coerceIn(0f, 1f), onBookSelected = onBookSelected, onContinueReading = onContinueReading, onEdit = onEdit, onMarkCompleted = onMarkCompleted, onMarkPlanToRead = onMarkPlanToRead, onShare = onShare, onDelete = onDelete)
                }
            }
        } else {
            Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(NextPageDimens.spacingSm), color = MaterialTheme.colorScheme.surfaceVariant) {
                Text(text = stringResource(R.string.home_no_current_book), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(NextPageDimens.spacingMd))
            }
        }
    }
}

@Composable
fun ContinueReadingCard(book: Book, progressFraction: Float = 0f, onBookSelected: (String, String, String) -> Unit, onContinueReading: (String, String?, String) -> Unit, onEdit: (Book) -> Unit = {}, onMarkCompleted: (Book) -> Unit = {}, onMarkPlanToRead: (Book) -> Unit = {}, onShare: (Book) -> Unit = {}, onDelete: (Book) -> Unit = {}) {
    var showMenu by remember { mutableStateOf(false) }
    Surface(modifier = Modifier.width(240.dp).clickable { onBookSelected(book.id, book.filePath, book.format) }, shape = RoundedCornerShape(NextPageDimens.spacingSm), color = MaterialTheme.colorScheme.surfaceVariant, tonalElevation = 1.dp) {
        Row(modifier = Modifier.padding(NextPageDimens.spacingMd)) {
            CoverThumbnail(coverPath = book.coverPath, modifier = Modifier.width(80.dp).height(120.dp).clip(RoundedCornerShape(NextPageDimens.spacingXs)))
            Spacer(modifier = Modifier.width(NextPageDimens.spacingMd))
            Column(modifier = Modifier.weight(1f)) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(text = book.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Box {
                        IconButton(onClick = { showMenu = true }, modifier = Modifier.size(32.dp)) {
                            Icon(imageVector = NextPageIcons.MoreVert, contentDescription = stringResource(R.string.context_menu_more), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                        }
                        // Shared shelf menu (same 5 items as BookListCard/BookGridCard).
                        // DropdownMenu renders in a popup window, so no LazyRow clipping.
                        BookContextMenu(expanded = showMenu, onDismissRequest = { showMenu = false }, onEdit = { onEdit(book) }, onMarkCompleted = { onMarkCompleted(book) }, onMarkPlanToRead = { onMarkPlanToRead(book) }, onShare = { onShare(book) }, onDelete = { onDelete(book) })
                    }
                }
                book.author?.let { author -> Text(text = author, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                Spacer(modifier = Modifier.height(NextPageDimens.spacingSm))
                NextPageProgressBar(progress = progressFraction, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(NextPageDimens.spacingSm))
                NextPageButton(onClick = { onContinueReading(book.id, book.filePath, book.format) }, variant = NextPageButtonVariant.FILLED, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp), modifier = Modifier.height(36.dp)) { Text(text = stringResource(R.string.home_continuar), style = MaterialTheme.typography.labelMedium) }
            }
        }
    }
}
