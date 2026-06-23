package com.nextpage.ui.components.atoms

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/**
 * Circular user avatar: either a Coil-loaded remote image, or a
 * primary-colored disc showing the user's initials. The circle is
 * `colorScheme.primary` so the initials branch is legible in both
 * light and dark themes.
 *
 * @param imageUrl Remote image URL. When `null` or blank, the initials
 *   fallback is rendered.
 * @param initials String from which the first two characters are
 *   extracted, upper-cased, and rendered as the fallback. Should be
 *   short (e.g. `"JS"` for "Juan S.").
 * @param modifier Modifier applied to the outer `Box`.
 * @param size Diameter of the avatar. Default `48.dp`. Width and height
 *   are forced equal via `Modifier.size(size)` on the `Box`.
 *
 * **Visual**: circle clipped to `CircleShape`, filled with
 * `colorScheme.primary`. On image: `AsyncImage` with
 * `ContentScale.Crop` filling the circle. On fallback: `titleMedium`
 * bold text in `colorScheme.onPrimary`, showing
 * `initials.take(2).uppercase()`.
 * **Behavior**: pure rendering. The `imageUrl` is passed straight to
 * Coil — no explicit decode size, no cache policy overrides (this is
 * an avatar, the default Coil pipeline is fine).
 * **Recomposition**: recomposes when `imageUrl`, `initials`, or
 * `size` change.
 */
@Composable
fun NextPageAvatar(
    imageUrl: String?,
    initials: String,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center
    ) {
        if (!imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
        } else {
            Text(
                text = initials.take(2).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}
