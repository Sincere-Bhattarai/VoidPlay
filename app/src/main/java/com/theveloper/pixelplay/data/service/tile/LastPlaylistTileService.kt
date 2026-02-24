package com.theveloper.voidplay.data.service.tile

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import com.theveloper.voidplay.MainActivity
import com.theveloper.voidplay.data.preferences.UserPreferencesRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Quick Settings tile that resumes the most recently played playlist.
 * Reads the last playlist ID from DataStore and fires ACTION_OPEN_PLAYLIST to MainActivity.
 * Works whether the app is open or not.
 */
@RequiresApi(Build.VERSION_CODES.N)
class LastPlaylistTileService : TileService() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface LastPlaylistTileEntryPoint {
        fun userPreferencesRepository(): UserPreferencesRepository
    }

    private val prefsRepo: UserPreferencesRepository by lazy {
        val appContext = applicationContext
        val entryPoint = EntryPointAccessors.fromApplication(
            appContext,
            LastPlaylistTileEntryPoint::class.java
        )
        entryPoint.userPreferencesRepository()
    }

    override fun onStartListening() {
        val lastPlaylistId = runBlocking {
            prefsRepo.lastPlaylistIdFlow.first()
        }
        qsTile?.apply {
            state = if (lastPlaylistId != null) Tile.STATE_INACTIVE else Tile.STATE_UNAVAILABLE
            updateTile()
        }
    }

    override fun onClick() {
        val (playlistId, _) = runBlocking {
            val id = prefsRepo.lastPlaylistIdFlow.first()
            val name = prefsRepo.lastPlaylistNameFlow.first()
            Pair(id, name)
        }

        if (playlistId == null) return

        val intent = Intent(this, MainActivity::class.java).apply {
            action = MainActivity.ACTION_OPEN_PLAYLIST
            putExtra(MainActivity.EXTRA_PLAYLIST_ID, playlistId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        startActivityAndCollapse(pendingIntent)
    }
}
