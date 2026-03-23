package com.puzzle.jigsaw.core.designsystem.components

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import com.puzzle.jigsaw.core.designsystem.R

@Composable
fun PuzzleBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier,
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_caret_left_bold),
            contentDescription = "Back",
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
}
