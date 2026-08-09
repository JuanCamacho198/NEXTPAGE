package com.nextpage.ui.components.atoms

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
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

/**
 * Primary app button with 5 visual variants. Thin wrapper over Material 3
 * Button/OutlinedButton/TextButton/IconButton that unifies theming and the
 * `RowScope`-shaped content slot under a single API.
 *
 * @param onClick Invoked on tap. Not called when [enabled] is `false`.
 * @param modifier Modifier applied to the underlying Material 3 button.
 * @param variant Visual style. `FILLED` (primary background), `OUTLINED`
 *   (transparent + outline), `TEXT` (no chrome), `TONAL` (secondary
 *   container background), or `ICON` (circular icon-only via `IconButton`).
 * @param enabled When `false`, no-op on click and content fades to
 *   `LocalContentColor` at `0.38f` alpha (Material 3 default).
 * @param shape Corner shape. Ignored by [NextPageButtonVariant.TEXT] (no
 *   container) and by [NextPageButtonVariant.ICON] (circle).
 * @param contentPadding Inner padding around [content]. Uses Material 3
 *   default (`ButtonDefaults.ContentPadding`) if not provided.
 * @param colors Optional `ButtonColors` override. `null` (default) keeps the
 *   variant's theme-derived colors; provide e.g.
 *   `ButtonDefaults.outlinedButtonColors(...)` to brand a specific instance.
 *   NOTE: the bundled Material3 `outlinedButtonColors` has no `borderColor`
 *   parameter — brand the outline via [border] instead.
 * @param border Optional [BorderStroke] override. `null` (default) keeps the
 *   variant's default border. Only applied to [NextPageButtonVariant.OUTLINED].
 * @param content Slot for the button label — typically one or two
 *   `Text`/`Icon` composables.
 *
 * **Visual**: rounded 4dp (default `shapes.small`), 48dp height. Variants
 * differ in background/border/text color: `FILLED` uses `primary`/
 * `onPrimary`; `TONAL` uses `secondaryContainer`/`onSecondaryContainer`;
 * `OUTLINED`/`TEXT` use `primary` on transparent; `ICON` is a bare
 * `IconButton` with no size or shape overrides.
 * **Behavior**: ripple on tap (Material 3 default `LocalIndication`).
 * Disabled state drops opacity to 0.38.
 * **Recomposition**: recomposes when `onClick`, `variant`, `enabled`,
 * `shape`, or `contentPadding` change; the inner content slot is a
 * subcomposition and only re-evaluates when its captured state changes.
 */
@Composable
fun NextPageButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: NextPageButtonVariant = NextPageButtonVariant.FILLED,
    enabled: Boolean = true,
    shape: Shape = MaterialTheme.shapes.small,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    colors: ButtonColors? = null,
    border: BorderStroke? = null,
    content: @Composable () -> Unit
) {
    when (variant) {
        NextPageButtonVariant.FILLED -> {
            Button(
                onClick = onClick,
                modifier = modifier,
                enabled = enabled,
                shape = shape,
                colors = colors ?: ButtonDefaults.buttonColors(
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
                colors = colors ?: ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary
                ),
                border = border,
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
                colors = colors ?: ButtonDefaults.buttonColors(
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

/**
 * Text-only overload of [NextPageButton] for the common case of a single
 * `Text` label. Delegates to the slot-based overload.
 *
 * @param text The string rendered inside the button (wrapped in a
 *   Material 3 `Text` with no style override — inherits the variant's
 *   `contentColor`).
 * @param onClick Invoked on tap. Not called when [enabled] is `false`.
 * @param modifier Modifier applied to the underlying Material 3 button.
 * @param variant Visual style; see [NextPageButton].
 * @param enabled When `false`, no-op on click and content fades to 0.38 alpha.
 *
 * **Visual**: identical to the slot-based [NextPageButton] for the same
 * `variant`, but with a plain `Text` as content (no icon slot).
 * **Behavior**: ripple on tap; disabled state drops opacity to 0.38.
 * **Recomposition**: recomposes when `text`, `onClick`, `variant`, or
 * `enabled` change.
 */
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
