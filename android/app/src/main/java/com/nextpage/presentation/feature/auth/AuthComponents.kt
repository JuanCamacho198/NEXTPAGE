package com.nextpage.presentation.feature.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.PaddingValues
import com.nextpage.R
import com.nextpage.presentation.screen.AuthTags
import com.nextpage.presentation.theme.NextPageTheme
import com.nextpage.presentation.viewmodel.AuthUiState
import com.nextpage.ui.icons.NextPageIcons

@Composable
fun AuthScreenScaffold(
    showBackArrow: Boolean,
    onNavigateBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val isDarkBackground = MaterialTheme.colorScheme.background.luminance() < 0.5f
    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(
                if (isDarkBackground) R.drawable.bg_auth_bookshelf_dark
                else R.drawable.bg_auth_bookshelf_light
            ),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = if (isDarkBackground) {
                            listOf(
                                MaterialTheme.colorScheme.background.copy(alpha = 0.25f),
                                Color.Black.copy(alpha = 0.65f)
                            )
                        } else {
                            listOf(Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.35f))
                        }
                    )
                )
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.Center
        ) {
            if (showBackArrow) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .testTag(AuthTags.BACK)
                ) {
                    Icon(
                        imageVector = NextPageIcons.ArrowBack,
                        contentDescription = stringResource(R.string.nav_back)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                content()
            }
        }
    }
}

@Composable
fun AuthLogo() {
    val glowColor = NextPageTheme.colors.welcomeBrandBlue
    Box(
        modifier = Modifier
            .size(176.dp)
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            glowColor.copy(alpha = 0.30f),
                            glowColor.copy(alpha = 0f)
                        ),
                        radius = size.minDimension / 2f
                    ),
                    radius = size.minDimension / 2f
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(NextPageTheme.colors.welcomeBrandBlue),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.app_logo_initials),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}

@Composable
fun AuthOrDivider(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant
        )
    }
}

@Composable
fun AuthErrorText(uiState: AuthUiState) {
    uiState.errorMessage?.let { error ->
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = authFailureMessageTemplateRes(uiState.failureKind)
                ?.let { messageTemplateRes -> stringResource(messageTemplateRes, error) }
                ?: error,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag(AuthTags.ERROR)
        )
    }
}

@Composable
fun AuthFooterLink(
    prefix: String,
    link: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = prefix,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(4.dp))
        TextButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 4.dp)) {
            Text(
                text = link,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun AuthConfigError(uiState: com.nextpage.presentation.viewmodel.AuthUiState) {
    if (!uiState.isConfigured) {
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.auth_config_error_google_unavailable),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
    } else {
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun AuthDevBypass(onContinueLocal: () -> Unit) {
    if (com.nextpage.BuildConfig.DEBUG) {
        Spacer(modifier = Modifier.height(16.dp))
        com.nextpage.ui.components.atoms.NextPageButton(
            onClick = onContinueLocal,
            variant = com.nextpage.ui.components.atoms.NextPageButtonVariant.TEXT,
            modifier = Modifier.testTag(AuthTags.DEV_BYPASS)
        ) {
            Icon(
                imageVector = NextPageIcons.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.auth_continue_local_dev),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Normal
            )
        }
    }
}
