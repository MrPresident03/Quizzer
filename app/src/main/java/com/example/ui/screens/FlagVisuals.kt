package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.CorrectGreen
import com.example.ui.theme.WrongRed

/*
 * Flags are emoji, so they blend into whatever is behind them: white flags (Japan, Cyprus) vanish on a light
 * card and dark ones (Germany, Afghanistan) on a dark card. Every flag therefore sits on the same light-grey
 * "stage" in both themes, with a soft drop shadow that outlines white edges.
 */
private val FlagStageTop = Color(0xFFE6EAF0)
private val FlagStageBottom = Color(0xFFC9D0DA)

/** A flag emoji drawn large on a neutral backdrop. [height] sets the frame; the flag fills most of it. */
@Composable
fun FlagStage(
    flag: String,
    height: Dp,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 16.dp
) {
    val shape = RoundedCornerShape(cornerRadius)
    val density = LocalDensity.current
    // Sized in dp, not sp: a flag is a picture, so the large-text setting shouldn't push it out of its frame.
    val style = with(density) {
        TextStyle(
            fontSize = (height * 0.7f).toSp(),
            textAlign = TextAlign.Center,
            shadow = Shadow(
                color = Color.Black.copy(alpha = 0.35f),
                offset = Offset(0f, 2.dp.toPx()),
                blurRadius = 6.dp.toPx()
            )
        )
    }
    Box(
        modifier = modifier
            .height(height)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(FlagStageTop, FlagStageBottom)))
            .border(1.dp, Color.Black.copy(alpha = 0.08f), shape),
        contentAlignment = Alignment.Center
    ) {
        Text(text = flag, style = style)
    }
}

/** "Country to Flag" answers as a 2x2 grid of big flag tiles instead of small emoji in a list. */
@Composable
fun FlagOptionsGrid(
    options: List<String>,
    correctIndex: Int,
    hiddenOptions: Set<Int>,
    selectedOption: Int?,
    isRevealed: Boolean,
    onPick: (Int) -> Unit
) {
    val visible = options.indices.filterNot { it in hiddenOptions }
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        visible.chunked(2).forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                row.forEach { index ->
                    FlagOptionTile(
                        flag = options[index],
                        label = optionLabel(index),
                        isRevealed = isRevealed,
                        isCorrect = index == correctIndex,
                        isPicked = index == selectedOption,
                        onClick = { onPick(index) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("quiz_option_${optionLabel(index).lowercase()}")
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun FlagOptionTile(
    flag: String,
    label: String,
    isRevealed: Boolean,
    isCorrect: Boolean,
    isPicked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = when {
        isRevealed && isCorrect -> CorrectGreen
        isRevealed && isPicked -> WrongRed
        else -> null
    }
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = accent?.copy(alpha = 0.2f)
                ?: MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isRevealed) 0.1f else 0.4f)
        ),
        border = accent?.let { BorderStroke(3.dp, it) },
        modifier = modifier
    ) {
        Box(Modifier.padding(8.dp)) {
            FlagStage(
                flag = flag,
                height = 108.dp,
                cornerRadius = 12.dp,
                // After the answer, fade the flags that don't matter so the right one stands out.
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(if (isRevealed && accent == null) 0.45f else 1f)
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(accent ?: Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center
            ) {
                when {
                    isRevealed && isCorrect -> Icon(Icons.Default.Check, contentDescription = "Correct", tint = Color.White, modifier = Modifier.size(16.dp))
                    isRevealed && isPicked -> Icon(Icons.Default.Close, contentDescription = "Wrong", tint = Color.White, modifier = Modifier.size(16.dp))
                    else -> Text(label, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
