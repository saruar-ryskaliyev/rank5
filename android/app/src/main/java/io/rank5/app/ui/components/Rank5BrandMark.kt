package io.rank5.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import io.rank5.app.R
import io.rank5.app.ui.theme.Sizes
import io.rank5.app.ui.theme.Spacing

/** The bars-only Rank5 mark, seated on the same tile treatment as other home icons. */
@Composable
fun Rank5BrandMark(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.size(Sizes.avatarLarge),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Image(
            painter = painterResource(R.drawable.rank5_brand_mark),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .padding(Spacing.xs)
                .fillMaxSize(),
        )
    }
}
