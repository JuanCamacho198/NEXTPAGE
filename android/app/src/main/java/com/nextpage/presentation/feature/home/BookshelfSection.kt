package com.nextpage.presentation.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nextpage.R
import com.nextpage.domain.model.Book
import com.nextpage.presentation.theme.NextPageDimens
import com.nextpage.ui.components.atoms.CoverThumbnail
import com.nextpage.ui.components.atoms.NextPageButton
import com.nextpage.ui.components.atoms.NextPageButtonVariant
import com.nextpage.ui.components.atoms.NextPageEmptyState

@Composable
fun MyBookshelfSection(books: List<Book>, onViewAll: () -> Unit, onBookSelected: (String, String, String) -> Unit) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(text = stringResource(R.string.home_my_bookshelf_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            NextPageButton(onClick = onViewAll, variant = NextPageButtonVariant.TEXT) { Text(text = stringResource(R.string.home_ver_todo)) }
        }
        if (books.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(NextPageDimens.spacingSm)) {
                items(books, key = { it.id }) { book -> BookshelfCard(book = book, onClick = { onBookSelected(book.id, book.filePath, book.format) }) }
            }
        } else {
            NextPageEmptyState(icon = ImageVector.vectorResource(R.drawable.ic_empty_library), title = stringResource(R.string.home_bookshelf_empty_title), subtitle = stringResource(R.string.home_bookshelf_empty_subtitle), modifier = Modifier.fillMaxWidth().padding(vertical = NextPageDimens.spacingMd))
        }
    }
}

@Composable
fun BookshelfCard(book: Book, onClick: () -> Unit) {
    Surface(modifier = Modifier.width(120.dp).clickable(onClick = onClick), shape = RoundedCornerShape(NextPageDimens.spacingSm), color = MaterialTheme.colorScheme.surfaceVariant, tonalElevation = 1.dp) {
        Column(modifier = Modifier.padding(NextPageDimens.spacingSm), horizontalAlignment = Alignment.CenterHorizontally) {
            CoverThumbnail(coverPath = book.coverPath, modifier = Modifier.fillMaxWidth().height(100.dp).clip(RoundedCornerShape(NextPageDimens.spacingXs)))
            Spacer(modifier = Modifier.height(NextPageDimens.spacingXs))
            Text(text = book.title, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        }
    }
}
