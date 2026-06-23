package com.nextpage.ui.components.atoms

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.VisualTransformation

/**
 * Material 3 `OutlinedTextField` preconfigured with the NextPage color
 * scheme (primary focused border, outline unfocused, error red).
 *
 * @param value Current text content. Must be a hoisted `String` state —
 *   the field does not own its value.
 * @param onValueChange Invoked on every keystroke with the new value.
 * @param modifier Modifier applied to the underlying `OutlinedTextField`.
 *   Note: a `fillMaxWidth()` is applied internally, so width-modifying
 *   modifiers should come before this composable in the call chain.
 * @param label Floating label rendered inside the border when focused or
 *   non-empty. `null` hides it.
 * @param placeholder Hint shown when the field is empty and unfocused.
 *   `null` hides it.
 * @param errorMessage When non-blank, the field enters the error visual
 *   state (red border, red supporting text) and renders the message
 *   below the field.
 * @param trailingIcon Optional icon at the end of the field (e.g. a
 *   visibility toggle for passwords).
 * @param trailingIconContentDescription Accessibility label for
 *   [trailingIcon]. Pass `null` for a decorative icon.
 * @param singleLine `true` (default) restricts input to one line and
 *   collapses newlines; `false` allows multi-line input.
 * @param enabled `false` disables input and dims the field.
 * @param readOnly `true` blocks editing but keeps the field visually
 *   active (used for selectable-only fields).
 * @param shape Optional corner shape. Falls back to
 *   `OutlinedTextFieldDefaults.shape` when `null`.
 * @param visualTransformation Visual transform applied to the value
 *   (e.g. `PasswordVisualTransformation`). Default `None`.
 *
 * **Visual**: outlined Material 3 field. Border is `colorScheme.primary`
 * when focused, `colorScheme.outline` when unfocused, and
 * `colorScheme.error` when [errorMessage] is non-blank. Supporting text
 * is shown only in the error state.
 * **Behavior**: drives the value via [onValueChange]; parent owns the
 * state. Trailing icon is purely decorative here (no click handling).
 * **Recomposition**: recomposes when any parameter changes; the
 * `OutlinedTextField` internally skips work if `value` is unchanged.
 */
@Composable
fun NextPageTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    errorMessage: String? = null,
    trailingIcon: ImageVector? = null,
    trailingIconContentDescription: String? = null,
    singleLine: Boolean = true,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    shape: Shape? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None
) {
    val isError = !errorMessage.isNullOrBlank()

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        readOnly = readOnly,
        singleLine = singleLine,
        isError = isError,
        label = label?.let { { Text(it) } },
        placeholder = placeholder?.let { { Text(it) } },
        trailingIcon = trailingIcon?.let { icon ->
            {
                Icon(
                    imageVector = icon,
                    contentDescription = trailingIconContentDescription
                )
            }
        },
        supportingText = errorMessage?.let { { Text(it) } },
        visualTransformation = visualTransformation,
        shape = shape ?: OutlinedTextFieldDefaults.shape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            errorBorderColor = MaterialTheme.colorScheme.error
        )
    )
}
