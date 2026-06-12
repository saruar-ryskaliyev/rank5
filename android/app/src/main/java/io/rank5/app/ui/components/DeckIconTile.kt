package io.rank5.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Text
import io.rank5.app.ui.theme.Sizes

/** A stable content tile for deck identity; emoji never acts as an interface control. */
@Composable
fun DeckIconTile(
    emoji: String,
    deckName: String,
    modifier: Modifier = Modifier,
    size: Dp = Sizes.iconTile,
    emojiSize: TextUnit = 24.sp,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
) {
    Surface(
        modifier = modifier
            .size(size)
            .semantics { contentDescription = "$deckName deck icon" },
        shape = MaterialTheme.shapes.small,
        color = containerColor,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = emoji.ifBlank { "🃏" },
                fontSize = emojiSize,
                textAlign = TextAlign.Center,
            )
        }
    }
}
