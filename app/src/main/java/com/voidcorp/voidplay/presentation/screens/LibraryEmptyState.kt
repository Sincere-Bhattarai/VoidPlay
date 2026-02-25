package com.voidcorp.voidplay.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.voidcorp.voidplay.R
import com.voidcorp.voidplay.data.model.LibraryTabId
import com.voidcorp.voidplay.data.model.StorageFilter
import com.voidcorp.voidplay.presentation.components.MiniPlayerHeight
import com.voidcorp.voidplay.ui.theme.GoogleSansRounded

private data class LibraryEmptySpec(
    val iconRes: Int,
    val title: String,
    val subtitle: String
)

private fun libraryEmptySpec(
    tabId: LibraryTabId,
    storageFilter: StorageFilter
): LibraryEmptySpec {
    return when (tabId) {
        LibraryTabId.SONGS -> when (storageFilter) {
            StorageFilter.ALL -> LibraryEmptySpec(
                iconRes = R.drawable.rounded_music_off_24,
                title = "No songs yet",
                subtitle = "Add music to your device or sync a cloud source to start listening."
            )
            StorageFilter.OFFLINE -> LibraryEmptySpec(
                iconRes = R.drawable.rounded_music_off_24,
                title = "No local songs found",
                subtitle = "Try another source filter or rescan your device library."
            )
            StorageFilter.ONLINE -> LibraryEmptySpec(
                iconRes = R.drawable.rounded_music_off_24,
                title = "No cloud songs found",
                subtitle = "Sync Telegram or Netease songs, or switch to local source."
            )
        }

        LibraryTabId.ALBUMS -> when (storageFilter) {
            StorageFilter.ALL -> LibraryEmptySpec(
                iconRes = R.drawable.rounded_album_24,
                title = "No albums available",
                subtitle = "Albums will appear here as soon as your library has grouped tracks."
            )
            StorageFilter.OFFLINE -> LibraryEmptySpec(
                iconRes = R.drawable.rounded_album_24,
                title = "No local albums found",
                subtitle = "Local songs are required to build local album groups."
            )
            StorageFilter.ONLINE -> LibraryEmptySpec(
                iconRes = R.drawable.rounded_album_24,
                title = "No cloud albums found",
                subtitle = "Cloud songs with album data will appear here after sync."
            )
        }

        LibraryTabId.ARTISTS -> when (storageFilter) {
            StorageFilter.ALL -> LibraryEmptySpec(
                iconRes = R.drawable.rounded_artist_24,
                title = "No artists available",
                subtitle = "Artists are shown after songs are indexed from any source."
            )
            StorageFilter.OFFLINE -> LibraryEmptySpec(
                iconRes = R.drawable.rounded_artist_24,
                title = "No local artists found",
                subtitle = "No artist metadata is available for local songs right now."
            )
            StorageFilter.ONLINE -> LibraryEmptySpec(
                iconRes = R.drawable.rounded_artist_24,
                title = "No cloud artists found",
                subtitle = "Cloud artist entries appear when remote songs are synced."
            )
        }

        LibraryTabId.LIKED -> when (storageFilter) {
            StorageFilter.ALL -> LibraryEmptySpec(
                iconRes = R.drawable.rounded_favorite_24,
                title = "No liked songs yet",
                subtitle = "Tap the heart icon while playing a song to save it here."
            )
            StorageFilter.OFFLINE -> LibraryEmptySpec(
                iconRes = R.drawable.rounded_favorite_24,
                title = "No liked local songs",
                subtitle = "Switch source filter or like songs from your device."
            )
            StorageFilter.ONLINE -> LibraryEmptySpec(
                iconRes = R.drawable.rounded_favorite_24,
                title = "No liked cloud songs",
                subtitle = "Like Telegram or Netease tracks to see them in this view."
            )
        }

        LibraryTabId.FOLDERS -> LibraryEmptySpec(
            iconRes = R.drawable.ic_folder,
            title = "No folders found",
            subtitle = "Internal storage folders with music will appear here."
        )

        LibraryTabId.PLAYLISTS -> LibraryEmptySpec(
            iconRes = R.drawable.rounded_playlist_play_24,
            title = "No playlists yet",
            subtitle = "Create your first playlist to organize your library."
        )
    }
}

@Composable
internal fun LibraryExpressiveEmptyState(
    tabId: LibraryTabId,
    storageFilter: StorageFilter,
    bottomBarHeight: Dp,
    modifier: Modifier = Modifier
) {
    val spec = remember(tabId, storageFilter) { libraryEmptySpec(tabId, storageFilter) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(
                start = 28.dp,
                end = 28.dp,
                bottom = bottomBarHeight + MiniPlayerHeight + 24.dp
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                tonalElevation = 2.dp
            ) {
                Box(
                    modifier = Modifier.size(56.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = spec.iconRes),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = spec.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = spec.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
