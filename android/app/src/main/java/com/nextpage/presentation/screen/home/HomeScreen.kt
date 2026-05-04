package com.nextpage.presentation.screen.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nextpage.domain.model.Book
import com.nextpage.presentation.theme.NextPageTheme
import com.nextpage.ui.components.molecules.BookCard
import com.nextpage.ui.components.atoms.NextPageProgressBar

// --- Data Models ---

data class HomeUiState(
    val greeting: String = "Hola, Juan",
    val minutesRead: Int = 120,
    val sessions: Int = 5,
    val dailyProgressPercent: Float = 0.6f,
    val currentBook: Book? = null,
    val recentBooks: List<Book> = emptyList()
)

val mockBooks = listOf(
    Book(id = "1", title = "1984", author = "George Orwell", coverPath = null, filePath = "", format = "EPUB", updatedAtEpochMillis = 0L),
    Book(id = "2", title = "Brave New World", author = "Aldous Huxley", coverPath = null, filePath = "", format = "EPUB", updatedAtEpochMillis = 0L)
)

val mockUiState = HomeUiState(
    currentBook = Book(id = "0", title = "Atomic Habits", author = "James Clear", coverPath = null, filePath = "", format = "EPUB", updatedAtEpochMillis = 0L),
    recentBooks = mockBooks
)

// --- Screen ---

@Composable
fun HomeScreen(
    uiState: HomeUiState = mockUiState,
    onBookClick: (String) -> Unit = {}
) {
    Scaffold(
        bottomBar = { HomeBottomNavigation() },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            item { HomeHeader(greeting = uiState.greeting) }
            item { TodaySummarySection(uiState) }
            item { ContinueReadingSection(uiState.currentBook, onBookClick) }
            item { BookshelfCarousel(uiState.recentBooks, onBookClick) }
            item { QuickActionsGrid() }
        }
    }
}

// --- Molecules ---

@Composable
fun HomeHeader(greeting: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.Person,
                contentDescription = "Profile",
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = greeting,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold
            )
        }
        IconButton(onClick = { /* TODO */ }) {
            Icon(
                imageVector = Icons.Outlined.Notifications,
                contentDescription = "Notifications",
                tint = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}

@Composable
fun TodaySummarySection(state: HomeUiState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SummaryCard(
            title = "Minutos",
            value = state.minutesRead.toString(),
            modifier = Modifier.weight(1f)
        )
        SummaryCard(
            title = "Sesiones",
            value = state.sessions.toString(),
            modifier = Modifier.weight(1f)
        )
        Card(
            modifier = Modifier.weight(1.5f),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Progreso diario", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(8.dp))
                NextPageProgressBar(progress = state.dailyProgressPercent)
            }
        }
    }
}

@Composable
fun SummaryCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ContinueReadingSection(book: Book?, onBookClick: (String) -> Unit) {
    if (book == null) return
    Column(modifier = Modifier.padding(top = 24.dp)) {
        Text(
            text = "Continuar leyendo",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        BookCard(
            title = book.title,
            author = book.author ?: "",
            progress = 0.4f,
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .clickable { onBookClick(book.id) }
        )
    }
}

@Composable
fun BookshelfCarousel(books: List<Book>, onBookClick: (String) -> Unit) {
    if (books.isEmpty()) return
    Column(modifier = Modifier.padding(top = 24.dp)) {
        Text(
            text = "Mi estantería",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(books) { book ->
                BookCard(
                    title = book.title,
                    author = book.author ?: "",
                    progress = 0f,
                    modifier = Modifier
                        .width(140.dp)
                        .clickable { onBookClick(book.id) }
                )
            }
        }
    }
}

@Composable
fun QuickActionsGrid() {
    Column(modifier = Modifier.padding(top = 24.dp, bottom = 16.dp)) {
        Text(
            text = "Accesos rápidos",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            QuickActionItem("Resaltados", Icons.Outlined.Create, Modifier.weight(1f))
            QuickActionItem("Notas", Icons.Outlined.List, Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            QuickActionItem("Estadísticas", Icons.Outlined.DateRange, Modifier.weight(1f))
            QuickActionItem("Importar", Icons.Outlined.Add, Modifier.weight(1f))
        }
    }
}

@Composable
fun QuickActionItem(label: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        onClick = { /* TODO */ }
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun HomeBottomNavigation() {
    var selectedItem by remember { mutableStateOf(0) }
    val items = listOf(
        Triple("Inicio", Icons.Filled.Home, Icons.Outlined.Home),
        Triple("Estantería", Icons.Filled.List, Icons.Outlined.List),
        Triple("Resaltados", Icons.Filled.Star, Icons.Outlined.Star),
        Triple("Ajustes", Icons.Filled.Settings, Icons.Outlined.Settings)
    )

    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        items.forEachIndexed { index, item ->
            NavigationBarItem(
                icon = {
                    Icon(
                        if (selectedItem == index) item.second else item.third,
                        contentDescription = item.first
                    )
                },
                label = { Text(item.first) },
                selected = selectedItem == index,
                onClick = { selectedItem = index },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    }
}
