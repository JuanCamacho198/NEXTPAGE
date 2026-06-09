package com.nextpage.presentation.screen

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import com.nextpage.R

/**
 * PDF-specific reader content.
 *
 * Renders the current page as a [Bitmap] image and provides tap zones
 * for page navigation via [detectTapGestures].
 */
@Composable
fun PdfReaderContent(
    bitmap: Bitmap?,
    currentPage: Int,
    totalPages: Int,
    onTapZone: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val isLeftZone = offset.x < size.width / 2
                    onTapZone(isLeftZone)
                }
            }
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = stringResource(
                    R.string.pdf_page_description,
                    currentPage + 1,
                    totalPages
                ),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color(0xFFADC6FF))
            }
        }
    }
}
