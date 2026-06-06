package com.nextpage.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nextpage.R
import com.nextpage.domain.model.Highlight
import com.nextpage.presentation.theme.NextPageDimens
import com.nextpage.presentation.viewmodel.HighlightsViewModel

private val ColorQuotes = Color(0xFF3B82F6)
private val ColorIdeas = Color(0xFFA855F7)
private val ColorPassages = Color(0xFF10B981)
private val ColorFavorites = Color(0xFFF59E0B)

private data class TabItem(val label: String, val icon: ImageVector, val color: Color)

private val highlightTabs = listOf(
    TabItem("Todos", Icons.Outlined.AutoAwesome, ColorQuotes),
    TabItem("Citas", Icons.Outlined.FormatQuote, ColorQuotes),
    TabItem("Ideas", Icons.Outlined.Lightbulb, ColorIdeas),
    TabItem("Pasajes", Icons.Outlined.AutoAwesome, ColorPassages)
)

@Composable
fun HighlightsScreen(
    contentPadding: PaddingValues,
    viewModel: HighlightsViewModel = viewModel()
) {
    val highlights by viewModel.highlights.collectAsState()
    val bookmarks by viewModel.bookmarks.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentPadding = PaddingValues(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(NextPageDimens.spacingMd)
    ) {
        // Header: avatar + brand + search
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "NP",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.home_nextpage_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                IconButton(onClick = { showSearch = !showSearch }) {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // Search bar (toggle)
        if (showSearch) {
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(stringResource(R.string.library_search_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp)
                )
            }
        }

        // Title + Subtitle
        item {
            Column {
                Text(
                    text = stringResource(R.string.highlights_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.highlights_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Tabs (pills with icons)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                highlightTabs.forEachIndexed { index, tab ->
                    val isSelected = selectedTab == index
                    Surface(
                        onClick = { selectedTab = index },
                        shape = RoundedCornerShape(20.dp),
                        color = if (isSelected) tab.color else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.label,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = tab.label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }

        // Recent section
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.highlights_recent),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                TextButton(onClick = { /* TODO: view all highlights */ }) {
                    Text(text = stringResource(R.string.home_ver_todo))
                }
            }
        }

        // Recent cards
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                HighlightCard(
                    content = stringResource(R.string.highlights_quote_content),
                    attribution = stringResource(R.string.highlights_quote_author),
                    accentColor = ColorQuotes,
                    type = "quote"
                )
                HighlightCard(
                    content = stringResource(R.string.highlights_idea_content),
                    attribution = null,
                    accentColor = ColorIdeas,
                    type = "idea"
                )
            }
        }

        // Summary stats
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatWidget(
                    value = "${highlights.size}",
                    label = stringResource(R.string.highlights_summary_quotes),
                    color = ColorQuotes
                )
                StatWidget(
                    value = "${bookmarks.size}",
                    label = stringResource(R.string.highlights_summary_ideas),
                    color = ColorIdeas
                )
                StatWidget(
                    value = "12",
                    label = stringResource(R.string.highlights_summary_passages),
                    color = ColorPassages
                )
                StatWidget(
                    value = "8",
                    label = stringResource(R.string.highlights_summary_favorites),
                    color = ColorFavorites
                )
            }
        }

        // Highlights list
        if (selectedTab == 0 && highlights.isNotEmpty()) {
            val filtered = if (searchQuery.isNotBlank()) {
                highlights.filter { it.textContent.contains(searchQuery, ignoreCase = true) }
            } else highlights

            items(filtered, key = { it.id }) { highlight ->
                HighlightItemCard(
                    content = "\"${highlight.textContent}\"",
                    note = highlight.note,
                    accentColor = ColorQuotes
                )
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
private fun HighlightCard(
    content: String,
    attribution: String?,
    accentColor: Color,
    type: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(modifier = Modifier.padding(start = 0.dp)) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
                    .background(accentColor)
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = content,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (attribution != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "— $attribution",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun HighlightItemCard(
    content: String,
    note: String?,
    accentColor: Color
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(modifier = Modifier.padding(start = 0.dp)) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
                    .background(accentColor)
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = content,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                if (!note.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Note: $note",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun StatWidget(
    value: String,
    label: String,
    color: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
