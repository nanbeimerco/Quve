package com.superglobalpeq.app.ui.shootout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.superglobalpeq.app.R
import com.superglobalpeq.app.dsp.ShootoutProcessor
import com.superglobalpeq.app.model.ShootoutOutcome
import com.superglobalpeq.app.ui.haptics.Haptic
import com.superglobalpeq.app.ui.haptics.rememberHaptics
import com.superglobalpeq.app.ui.theme.LocalQuveExpressiveColorScheme

/** The A/B selector shared by the Shootout tab and the panel shown over other apps. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ShootoutSideSelector(
    side: Int,
    onSelect: (Int) -> Unit,
    height: Dp,
    labelSize: TextUnit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    val scheme = LocalQuveExpressiveColorScheme.current ?: MaterialTheme.colorScheme
    val colors = ToggleButtonDefaults.colors(
        containerColor = scheme.surfaceContainerHighest,
        contentColor = scheme.onSurfaceVariant,
        checkedContainerColor = scheme.primary,
        checkedContentColor = scheme.onPrimary,
    )
    val sides = listOf(
        ShootoutProcessor.SIDE_A to stringResource(R.string.shootout_side_a),
        ShootoutProcessor.SIDE_B to stringResource(R.string.shootout_side_b),
    )
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        sides.forEachIndexed { index, (value, label) ->
            ToggleButton(
                checked = side == value,
                onCheckedChange = {
                    if (side != value) {
                        haptics.play(Haptic.Select)
                        onSelect(value)
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .height(height),
                shapes = if (index == 0) {
                    ButtonGroupDefaults.connectedLeadingButtonShapes()
                } else {
                    ButtonGroupDefaults.connectedTrailingButtonShapes()
                },
                colors = colors,
                contentPadding = PaddingValues(0.dp),
            ) {
                Text(text = label, fontSize = labelSize, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            }
        }
    }
}

@Composable
fun ShootoutVoteButtons(
    enabled: Boolean,
    onVote: (ShootoutOutcome) -> Unit,
    height: Dp,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    val outer = 24.dp
    val inner = 6.dp
    val votes = listOf(
        Triple(
            ShootoutOutcome.A,
            R.string.shootout_vote_a,
            RoundedCornerShape(topStart = outer, bottomStart = outer, topEnd = inner, bottomEnd = inner),
        ),
        Triple(ShootoutOutcome.TIE, R.string.shootout_vote_tie, RoundedCornerShape(inner)),
        Triple(
            ShootoutOutcome.B,
            R.string.shootout_vote_b,
            RoundedCornerShape(topStart = inner, bottomStart = inner, topEnd = outer, bottomEnd = outer),
        ),
    )
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        votes.forEach { (outcome, label, shape) ->
            FilledTonalButton(
                onClick = {
                    haptics.play(Haptic.ToggleOn)
                    onVote(outcome)
                },
                enabled = enabled,
                shape = shape,
                colors = ButtonDefaults.filledTonalButtonColors(),
                contentPadding = PaddingValues(horizontal = 4.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(height),
            ) {
                Text(
                    text = stringResource(label),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
