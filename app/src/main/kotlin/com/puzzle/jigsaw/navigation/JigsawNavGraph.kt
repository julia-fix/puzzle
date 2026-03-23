package com.puzzle.jigsaw.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.puzzle.jigsaw.data.catalog.AssetPuzzleCatalogRepository
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.puzzle.jigsaw.data.progress.DataStoreJigsawProgressStore
import com.puzzle.jigsaw.domain.jigsaw.JigsawCatalog
import com.puzzle.jigsaw.feature.gameplay.GameplayRoute
import com.puzzle.jigsaw.feature.gallery.GalleryScreen
import com.puzzle.jigsaw.feature.piececount.PieceCountScreen

@Composable
fun JigsawNavGraph() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val catalogRepository = remember(context.applicationContext) {
        AssetPuzzleCatalogRepository(context.applicationContext)
    }
    val images by produceState(initialValue = emptyList(), catalogRepository) {
        value = catalogRepository.loadImages()
    }
    val pieceCounts = remember { JigsawCatalog.pieceCountOptions() }
    val progressStore = remember(context.applicationContext) {
        DataStoreJigsawProgressStore.create(context.applicationContext)
    }
    val recentSessions = progressStore.recentSessions().collectAsStateWithLifecycle(initialValue = emptyList())

    NavHost(
        navController = navController,
        startDestination = JigsawDestination.galleryRoute,
    ) {
        composable(JigsawDestination.galleryRoute) {
            GalleryScreen(
                images = images,
                recentSessions = recentSessions.value,
                onImageSelected = { image ->
                    navController.navigate(JigsawDestination.pieceCount(image.id))
                },
            )
        }
        composable(
            route = JigsawDestination.pieceCountRoute,
            arguments = listOf(navArgument(JigsawDestination.imageIdArg) { type = NavType.StringType }),
        ) { backStackEntry ->
            val imageId = backStackEntry.arguments?.getString(JigsawDestination.imageIdArg)
            val image = images.firstOrNull { it.id == imageId }
            if (image == null) {
                MissingArgumentState()
            } else {
                PieceCountScreen(
                    pieceCountOptions = pieceCounts,
                    recentSessions = recentSessions.value.filter { it.imageId == image.id },
                    onBack = { navController.popBackStack() },
                    onPieceCountSelected = { option ->
                        navController.navigate(JigsawDestination.gameplay(image.id, option.totalPieces))
                    },
                )
            }
        }
        composable(
            route = JigsawDestination.gameplayRoute,
            arguments = listOf(
                navArgument(JigsawDestination.imageIdArg) { type = NavType.StringType },
                navArgument(JigsawDestination.pieceCountArg) { type = NavType.IntType },
            ),
        ) { backStackEntry ->
            val imageId = backStackEntry.arguments?.getString(JigsawDestination.imageIdArg)
            val pieceCount = backStackEntry.arguments?.getInt(JigsawDestination.pieceCountArg)
            val image = images.firstOrNull { it.id == imageId }
            val option = pieceCounts.firstOrNull { it.totalPieces == pieceCount }
            if (image == null || option == null) {
                MissingArgumentState()
            } else {
                GameplayRoute(
                    image = image,
                    pieceCount = option,
                    progressStore = progressStore,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}

@Composable
private fun MissingArgumentState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "This screen could not be opened.",
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(24.dp),
        )
    }
}
