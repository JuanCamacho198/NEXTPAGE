package com.nextpage.ui.components.atoms

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
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
import com.nextpage.debug.DebugLog

/**
 * Book cover image atom with three render branches: loading spinner,
 * remote `AsyncImage` (Coil) with explicit decode size, or a fallback
 * `Book` icon. Always clipped to a rounded 8dp shape with a
 * `surfaceVariant` background so the placeholder area looks like a
 * card even when the image is missing.
 *
 * @param coverUrl Remote cover URL. When `null` or blank, the fallback
 *   icon branch is rendered (unless [isLoading] is `true`).
 * @param title Book title, used as the `contentDescription` for the
 *   remote image and fallback icon. When `null`, falls back to
 *   `R.string.library_cover_content_description`.
 * @param modifier Modifier applied to the outer `Box`. The size must
 *   be set by the caller (e.g. `Modifier.size(width = 96.dp, height = 136.dp)`).
 * @param isLoading When `true`, the loading branch is forced (overrides
 *   [coverUrl]). Renders a 32dp `CircularProgressIndicator` centered
 *   inside the box. Pass `false` once the surrounding data is ready.
 *
 * **Visual**: rounded 8dp `Box` filled with `colorScheme.surfaceVariant`.
 * On loading: 32dp `CircularProgressIndicator` in `colorScheme.primary`.
 * On image: `AsyncImage` with `ContentScale.Crop`, decoded at
 * 128×180 px (matches the rendered size on typical phone DPIs) with
 * placeholder/error/fallback drawables, `crossfade(true)`, and both
 * memory and disk cache policies enabled.
 * On fallback: 40dp outlined `Book` icon in `colorScheme.onSurfaceVariant`.
 * **Behavior**: pure rendering. The `ImageRequest` is `remember`-ed
 * keyed on `(context, density, coverUrl)` so it's only rebuilt when
 * the URL changes.
 * **Recomposition**: recomposes when `coverUrl`, `title`, or
 * `isLoading` change.
 */
@Composable
fun NextPageBookCover(
    coverUrl: String? = null,
    title: String? = null,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val contentDescription = title ?: stringResource(R.string.library_cover_content_description)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        when {
            isLoading -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            }
            !coverUrl.isNullOrBlank() -> {
                val imageRequest = remember(context, density, coverUrl) {
                    ImageRequest.Builder(context)
                        .data(coverUrl)
                        .size(
                            width = with(density) { 128.dp.toPx().toInt() },
                            height = with(density) { 180.dp.toPx().toInt() }
                        )
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .placeholder(R.drawable.cover_placeholder)
                        .error(R.drawable.cover_error)
                        .fallback(R.drawable.cover_placeholder)
                        .crossfade(true)
                        .build()
                }
                AsyncImage(
                    model = imageRequest,
                    onState = { state ->
                        if (state is AsyncImagePainter.State.Error) {
                            DebugLog.error(TAG, "NextPageBookCover: cover load failed for $coverUrl")
                        }
                    },
                    contentDescription = contentDescription,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            else -> {
                Icon(
                    imageVector = Icons.Outlined.Book,
                    contentDescription = contentDescription,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private const val TAG = "NextPageBookCover"
