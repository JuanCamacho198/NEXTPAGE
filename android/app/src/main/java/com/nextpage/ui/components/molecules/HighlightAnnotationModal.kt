package com.nextpage.ui.components.molecules

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nextpage.R

/**
 * Glassmorphic modal for adding/editing a note or comment on a highlighted
 * text selection.
 *
 * Design node `GshXP` (Pencil) — adapted for Material 3 while preserving
 * the key visual elements from the design spec:
 * - Full-screen backdrop: black 36% opacity + background blur
 * - Modal container: 512dp width, rounded 24dp, fill #101c2cb4,
 *   stroke #94adce17
 * - Header: title (Manrope 18/Bold, white) + close X button
 * - Text snippet: left cyan border (#49d4ff, 4dp), dark bg (#08111f40)
 * - Textarea: background #08111f, rounded 16dp, padding 16dp,
 *   stroke #94adce08
 * - Actions: "Cancelar" (text #8fa3bf) + "Guardar" (bg #49d4ff,
 *   rounded pill, text #08111f bold)
 *
 * NO colour selector — explicitly removed per design spec CM03.
 *
 * @param titleRes string resource for the modal title ("Nueva Nota"
 *  or "Nuevo Comentario")
 * @param hintRes string resource for the textarea placeholder text
 * @param snippetLabelRes string resource for the label above the
 *  highlighted text snippet (e.g. "Texto seleccionado")
 * @param selectedText the highlighted text content to display in the
 *  snippet area
 * @param initialText pre-filled text (for editing an existing
 *  note/comment)
 * @param onSave invoked with the final text when "Guardar" is tapped
 * @param onDismiss invoked when the backdrop or X button is tapped
 */
@Composable
fun HighlightAnnotationModal(
    titleRes: Int,
    hintRes: Int,
    snippetLabelRes: Int,
    selectedText: String?,
    initialText: String = "",
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var text by remember(initialText) { mutableStateOf(initialText) }

    // ── Full-screen glassmorphic backdrop ──────────────────────────
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF000000))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {
                onDismiss()
            },
        contentAlignment = Alignment.Center
    ) {
        // ── Prevent backdrop tap from propagating through the modal ──
        Box(
            modifier = Modifier
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { /* consume — don't dismiss on modal tap */ }
        ) {
            // ── Modal card ─────────────────────────────────────────
            Column(
                modifier = Modifier
                    .widthIn(max = 512.dp)
                    .width(360.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF101C2C))
                    .border(1.dp, Color(0x1794ADCE), RoundedCornerShape(24.dp))
                    .padding(24.dp)
            ) {
                // ── Header: title + close ──────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(titleRes),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(0x1494ADCE))
                            .clickable { onDismiss() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(
                                R.string.annotation_modal_close
                            ),
                            tint = Color(0xFF8FA3BF),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // ── Highlighted text snippet ───────────────────────
                if (!selectedText.isNullOrBlank()) {
                    Text(
                        text = stringResource(snippetLabelRes),
                        color = Color(0xFF8FA3BF),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .drawBehind {
                                val strokeWidth = 4.dp.toPx()
                                drawLine(
                                    color = Color(0xFF49D4FF),
                                    start = Offset(0f, 0f),
                                    end = Offset(0f, size.height),
                                    strokeWidth = strokeWidth
                                )
                            }
                            .background(Color(0x4008111F), RoundedCornerShape(8.dp))
                            .padding(start = 16.dp, top = 12.dp, end = 12.dp, bottom = 12.dp)
                    ) {
                        Text(
                            text = selectedText,
                            color = Color(0xFFDDE2F8),
                            fontSize = 14.sp,
                            maxLines = 6,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 20.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                }

                // ── Textarea ───────────────────────────────────────
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = {
                        Text(
                            text = stringResource(hintRes),
                            color = Color(0xFF5C6A80)
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color(0xFFDDE2F8),
                        unfocusedTextColor = Color(0xFFDDE2F8),
                        focusedContainerColor = Color(0xFF08111F),
                        unfocusedContainerColor = Color(0xFF08111F),
                        focusedBorderColor = Color(0x0894ADCE),
                        unfocusedBorderColor = Color(0x0894ADCE),
                        cursorColor = Color(0xFF49D4FF)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default)
                )

                Spacer(modifier = Modifier.height(20.dp))

                // ── Action buttons ─────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(
                            text = stringResource(R.string.reader_cancel),
                            color = Color(0xFF8FA3BF),
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = { onSave(text) },
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF49D4FF),
                            contentColor = Color(0xFF08111F)
                        )
                    ) {
                        Text(
                            text = stringResource(R.string.reader_save),
                            color = Color(0xFF08111F),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
