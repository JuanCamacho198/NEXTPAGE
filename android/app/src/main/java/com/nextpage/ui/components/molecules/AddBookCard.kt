package com.nextpage.ui.components.molecules

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nextpage.R

@Composable
fun AddBookCard(
    onImportClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp)
            .clip(RoundedCornerShape(12.dp))
            .dashedBorder(
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                strokeWidth = 1.5.dp,
                cornerRadius = 12.dp,
                dashLength = 8.dp,
                gapLength = 4.dp
            )
            .clickable { onImportClick() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Add,
                contentDescription = stringResource(R.string.library_import_book),
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = stringResource(R.string.library_import_book),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = stringResource(R.string.library_import_formats),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun Modifier.dashedBorder(
    color: Color,
    strokeWidth: Dp = 1.dp,
    cornerRadius: Dp = 12.dp,
    dashLength: Dp = 8.dp,
    gapLength: Dp = 4.dp
) = this.drawBehind {
    val rect = androidx.compose.ui.geometry.Rect(
        offset = androidx.compose.ui.geometry.Offset.Zero,
        size = size
    )
    val path = androidx.compose.ui.graphics.Path().apply {
        addRoundRect(
            androidx.compose.ui.geometry.RoundRect(
                rect = rect,
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius.toPx())
            )
        )
    }
    drawPath(
        path = path,
        color = color,
        style = Stroke(
            width = strokeWidth.toPx(),
            pathEffect = PathEffect.dashPathEffect(
                floatArrayOf(dashLength.toPx(), gapLength.toPx()),
                0f
            )
        )
    )
}
