package com.voidcorp.voidplay.presentation.components.scoped

import com.voidcorp.voidplay.presentation.navigation.navigateSafely

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavHostController
import com.voidcorp.voidplay.presentation.navigation.Screen
import com.voidcorp.voidplay.presentation.viewmodel.PlayerViewModel
import kotlinx.coroutines.flow.collectLatest

@Composable
internal fun PlayerArtistNavigationEffect(
    navController: NavHostController,
    sheetCollapsedTargetY: Float,
    sheetMotionController: SheetMotionController,
    playerViewModel: PlayerViewModel
) {
    LaunchedEffect(navController, sheetCollapsedTargetY) {
        playerViewModel.artistNavigationRequests.collectLatest { artistId ->
            sheetMotionController.snapCollapsed(sheetCollapsedTargetY)
            playerViewModel.collapsePlayerSheet()

            navController.navigateSafely(Screen.ArtistDetail.createRoute(artistId)) {
                launchSingleTop = true
            }
        }
    }
}
