package com.voidcorp.voidplay.presentation.components.scoped

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

@Composable
internal fun rememberPrewarmFullPlayer(currentSongId: String?): Boolean {
    var prewarmFullPlayer by remember { mutableStateOf(false) }

    LaunchedEffect(currentSongId) {
        if (currentSongId != null) {
            prewarmFullPlayer = true
        }
    }
    LaunchedEffect(currentSongId, prewarmFullPlayer) {
        if (prewarmFullPlayer) {
            delay(32)
            prewarmFullPlayer = false
        }
    }

    return prewarmFullPlayer
}
