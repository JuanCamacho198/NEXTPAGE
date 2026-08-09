package com.nextpage.presentation.screen.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.unit.sp
import com.nextpage.R
import com.nextpage.domain.model.Book
import com.nextpage.ui.components.atoms.CoverThumbnail
import com.nextpage.ui.components.molecules.BookContextMenu
import com.nextpage.ui.icons.NextPageIcons

private const val READING_TARGET_MINUTES = 300L
private const val SURFACE_ALPHA = 0.3f

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookListCard(
    book: Book,
    minutesRead: Long,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onEdit: () -> Unit,
    onMarkCompleted: () -> Unit,
    onMarkPlanToRead: () -> Unit,
    onShare: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val progressFraction = if (minutesRead > 0L) {
        (minutesRead.toFloat() / READING_TARGET_MINUTES).coerceIn(0f, 1f)
    } else 0f

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongPress),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = SURFACE_ALPHA)
    ) {
        Row(modifier = Modifier.padding(12.dp)) {
            CoverThumbnail(
                coverPath = book.coverPath,
                modifier = Modifier
                    .width(60.dp)
                    .height(80.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = book.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = book.author ?: stringResource(R.string.library_author_unknown),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (progressFraction > 0f) {
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { progressFraction.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.library_status_in_progress, minutesRead),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = NextPageIcons.MoreVert,
                        contentDescription = stringResource(R.string.context_menu_more),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
                BookContextMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    onEdit = onEdit,
                    onMarkCompleted = onMarkCompleted,
                    onMarkPlanToRead = onMarkPlanToRead,
                    onShare = onShare,
                    onDelete = onLongPress
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookGridCard(
    book: Book,
    minutesRead: Long,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onEdit: () -> Unit,
    onMarkCompleted: () -> Unit,
    onMarkPlanToRead: () -> Unit,
    onShare: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val progressFraction = if (minutesRead > 0L) {
        (minutesRead.toFloat() / READING_TARGET_MINUTES).coerceIn(0f, 1f)
    } else 0f

    val statusText = when {
        progressFraction >= 1f -> stringResource(R.string.library_status_completed)
        minutesRead > 0L -> stringResource(R.string.library_status_in_progress, minutesRead)
        else -> null
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = SURFACE_ALPHA))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongPress
            )
    ) {
        Box {
            CoverThumbnail(
                coverPath = book.coverPath,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
            ) {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = NextPageIcons.MoreVert,
                        contentDescription = stringResource(R.string.context_menu_more),
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }
                BookContextMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    onEdit = onEdit,
                    onMarkCompleted = onMarkCompleted,
                    onMarkPlanToRead = onMarkPlanToRead,
                    onShare = onShare,
                    onDelete = onLongPress
                )
            }
        }

        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = book.title,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = book.author ?: stringResource(R.string.library_author_unknown),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (statusText != null) {
                Text(
                    text = statusText,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                if (progressFraction in 0.01f..0.99f) {
                    Spacer(modifier = Modifier.height(2.dp))
                    LinearProgressIndicator(
                        progress = { progressFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .clip(RoundedCornerShape(1.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }
        }
    }
}
