package com.theveloper.voidplay.data.service.tile

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import com.theveloper.voidplay.MainActivity

/**
 * Quick Settings tile that shuffles and plays all songs.
 * Fires ACTION_SHUFFLE_ALL to MainActivity, which calls PlayerViewModel.shuffleAllSongs().
 * Works whether the app is open or not.
 */
@RequiresApi(Build.VERSION_CODES.N)
class ShuffleAllTileService : TileService() {

    override fun onStartListening() {
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            updateTile()
        }
    }

    override fun onClick() {
        val intent = Intent(this, MainActivity::class.java).apply {
            action = MainActivity.ACTION_SHUFFLE_ALL
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
