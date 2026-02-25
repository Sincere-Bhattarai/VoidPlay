package com.voidcorp.voidplay.data.repository

import androidx.paging.PagingData
import com.voidcorp.voidplay.data.model.Song
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow

interface SongRepository {
    fun getSongs(): Flow<List<Song>>
    fun getSongsByAlbum(albumId: Long): Flow<List<Song>>
    fun getSongsByArtist(artistId: Long): Flow<List<Song>>
    suspend fun searchSongs(query: String): List<Song>
    fun getSongById(songId: Long): Flow<Song?>
    fun getPaginatedSongs(sortOption: com.voidcorp.voidplay.data.model.SortOption, storageFilter: com.voidcorp.voidplay.data.model.StorageFilter): Flow<PagingData<Song>>
    @OptIn(ExperimentalCoroutinesApi::class)
    fun getPaginatedSongs(): Flow<PagingData<Song>>
    fun getPaginatedFavoriteSongs(
        sortOption: com.voidcorp.voidplay.data.model.SortOption,
        storageFilter: com.voidcorp.voidplay.data.model.StorageFilter
    ): Flow<PagingData<Song>>
    suspend fun getFavoriteSongsOnce(
        storageFilter: com.voidcorp.voidplay.data.model.StorageFilter
    ): List<Song>
    fun getFavoriteSongCountFlow(
        storageFilter: com.voidcorp.voidplay.data.model.StorageFilter
    ): Flow<Int>
}
