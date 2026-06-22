package com.nextpage.ui.components.atoms

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.nextpage.R
import java.io.File

@Composable
fun CoverThumbnail(
    coverPath: String?,
    modifier: Modifier = Modifier,
    onImageState: ((AsyncImagePainter.State) -> Unit)? = null
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val coverFile = remember(coverPath) {
        coverPath
            ?.takeIf { it.isNotBlank() }
            ?.let(::File)
    }
    val imageRequest = remember(context, density, coverFile, coverPath) {
        ImageRequest.Builder(context)
            .data(coverFile)
            .size(
                width = with(density) { 80.dp.toPx().toInt() },
                height = with(density) { 120.dp.toPx().toInt() }
            )
            .placeholder(R.drawable.cover_placeholder)
            .error(R.drawable.cover_error)
            .fallback(R.drawable.cover_placeholder)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .crossfade(true)
            .build()
    }

    AsyncImage(
        model = imageRequest,
        onState = { state -> onImageState?.invoke(state) },
        contentDescription = stringResource(R.string.library_cover_content_description),
        contentScale = ContentScale.Crop,
        modifier = modifier.clip(MaterialTheme.shapes.small)
    )
}
