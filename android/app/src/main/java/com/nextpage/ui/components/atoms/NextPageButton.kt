package com.nextpage.ui.components.atoms

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape

enum class NextPageButtonVariant {
    FILLED,
    OUTLINED,
    TEXT,
    TONAL,
    ICON
}

@Composable
fun NextPageButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: NextPageButtonVariant = NextPageButtonVariant.FILLED,
    enabled: Boolean = true,
    shape: Shape = MaterialTheme.shapes.small,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable () -> Unit
) {
    when (variant) {
        NextPageButtonVariant.FILLED -> {
            Button(
                onClick = onClick,
                modifier = modifier,
                enabled = enabled,
                shape = shape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                contentPadding = contentPadding
            ) {
                RowScopeContent(content = content)
            }
        }
        NextPageButtonVariant.OUTLINED -> {
            OutlinedButton(
                onClick = onClick,
                modifier = modifier,
                enabled = enabled,
                shape = shape,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary
                ),
                contentPadding = contentPadding
            ) {
                RowScopeContent(content = content)
            }
        }
        NextPageButtonVariant.TEXT -> {
            TextButton(
                onClick = onClick,
                modifier = modifier,
                enabled = enabled,
                contentPadding = contentPadding
            ) {
                RowScopeContent(content = content)
            }
        }
        NextPageButtonVariant.TONAL -> {
            Button(
                onClick = onClick,
                modifier = modifier,
                enabled = enabled,
                shape = shape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ),
                contentPadding = contentPadding
            ) {
                RowScopeContent(content = content)
            }
        }
        NextPageButtonVariant.ICON -> {
            IconButton(
                onClick = onClick,
                modifier = modifier,
                enabled = enabled,
                content = content
            )
        }
    }
}

@Composable
private fun RowScope.RowScopeContent(content: @Composable () -> Unit) {
    content()
}

@Composable
fun NextPageButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: NextPageButtonVariant = NextPageButtonVariant.FILLED,
    enabled: Boolean = true
) {
    NextPageButton(
        onClick = onClick,
        modifier = modifier,
        variant = variant,
        enabled = enabled
    ) {
        Text(text = text)
    }
}
