package com.puzzle.jigsaw

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import com.puzzle.jigsaw.core.designsystem.theme.PuzzleTheme
import com.puzzle.jigsaw.navigation.JigsawNavGraph

@Composable
fun JigsawPuzzleApp() {
    PuzzleTheme {
        Surface {
            JigsawNavGraph()
        }
    }
}

