package com.nextpage.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.nextpage.R
import com.nextpage.presentation.theme.NextPageDimens
import com.nextpage.ui.components.atoms.NextPageButton

/**
 * Onboarding daily-goal step (REQ-daily-reading-goal-2, SCEN-daily-reading-goal-1).
 *
 * Shown only when no goal is stored. Preselects 30 minutes; [onSave] persists
 * the choice and navigates to Home.
 */
@Composable
fun OnboardingGoalScreen(
    onSave: (minutes: Int) -> Unit,
    modifier: Modifier = Modifier,
    initialMinutes: Int = DEFAULT_GOAL_MINUTES
) {
    val options = GOAL_OPTIONS
    var selectedMinutes by rememberSaveable { mutableIntStateOf(initialMinutes) }

    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = NextPageDimens.spacingLg),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(NextPageDimens.spacingXl))

            Text(
                text = stringResource(R.string.onboarding_goal_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(NextPageDimens.spacingLg))

            Column(
                modifier = Modifier.selectableGroup().fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(NextPageDimens.spacingSm)
            ) {
                options.forEach { minutes ->
                    GoalOptionRow(
                        label = "$minutes ${stringResource(R.string.onboarding_goal_minutes)}",
                        selected = selectedMinutes == minutes,
                        onSelect = { selectedMinutes = minutes }
                    )
                }
            }

            Spacer(modifier = Modifier.height(NextPageDimens.spacingXl))

            NextPageButton(
                onClick = { onSave(selectedMinutes) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.onboarding_goal_continue))
            }
        }
    }
}

@Composable
private fun GoalOptionRow(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect)
            .padding(vertical = NextPageDimens.spacingSm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Spacer(modifier = Modifier.width(NextPageDimens.spacingXs))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

private val GOAL_OPTIONS = listOf(15, 30, 45, 60, 90, 120)
private const val DEFAULT_GOAL_MINUTES = 30
