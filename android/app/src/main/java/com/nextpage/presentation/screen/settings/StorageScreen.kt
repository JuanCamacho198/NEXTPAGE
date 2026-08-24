package com.nextpage.presentation.screen.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nextpage.R
import com.nextpage.presentation.theme.NextPageTheme
import com.nextpage.ui.components.molecules.NextPageSettingsSubPage

private data class StorageBook(
    val titleRes: Int,
    val sizeRes: Int
)

@Composable
fun StorageScreen(
    onBack: () -> Unit,
    onClearAll: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val books = listOf(
        StorageBook(R.string.storage_book_odyssey, R.string.storage_book_odyssey_size),
        StorageBook(R.string.storage_book_pride, R.string.storage_book_pride_size),
        StorageBook(R.string.storage_book_moby, R.string.storage_book_moby_size)
    )

    NextPageSettingsSubPage(
        title = stringResource(R.string.settings_storage_title),
        onBack = onBack
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header 312MB
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.storage_header_used),
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.storage_header_subtitle),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Lista por libro 12dp entre filas
            books.forEach { book ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(book.titleRes),
                                style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = stringResource(book.sizeRes),
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(
                            onClick = {
                                Toast.makeText(
                                    context,
                                    "${context.getString(book.titleRes)} ${context.getString(R.string.storage_action_delete)}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        ) {
                            Text(
                                text = stringResource(R.string.storage_action_delete),
                                style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp),
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            // Acciones Limpiar todo
            Spacer(modifier = Modifier.height(4.dp))
            Button(
                onClick = {
                    Toast.makeText(context, context.getString(R.string.storage_cleared), Toast.LENGTH_SHORT).show()
                    onClearAll?.invoke()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) {
                Text(text = stringResource(R.string.storage_action_clear_all))
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun StorageScreenDarkPreview() {
    NextPageTheme(darkTheme = true) {
        StorageScreen(onBack = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun StorageScreenLightPreview() {
    NextPageTheme(darkTheme = false) {
        StorageScreen(onBack = {})
    }
}
