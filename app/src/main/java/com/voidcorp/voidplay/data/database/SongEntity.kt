package com.voidcorp.voidplay.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.voidcorp.voidplay.data.model.ArtistRef
import com.voidcorp.voidplay.data.model.Song
import com.voidcorp.voidplay.utils.normalizeMetadataText
import com.voidcorp.voidplay.utils.normalizeMetadataTextOrEmpty

@Entity(
    tableName = "songs",
    indices = [
        Index(value = ["title"], unique = false),
        Index(value = ["album_id"], unique = false),
        Index(value = ["artist_id"], unique = false),
        Index(value = ["artist_name"], unique = false),
        Index(value = ["genre"], unique = false),
        Index(value = ["parent_directory_path"], unique = false),
        Index(value = ["content_uri_string"], unique = false),
        Index(value = ["date_added"], unique = false),
        Index(value = ["duration"], unique = false)
    ],
    foreignKeys = [
        ForeignKey(
            entity = AlbumEntity::class,
            parentColumns = ["id"],
            childColumns = ["album_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ArtistEntity::class,
            parentColumns = ["id"],
            childColumns = ["artist_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ]
)
data class SongEntity(
    @PrimaryKey val id: Long,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "artist_name") val artistName: String,
    @ColumnInfo(name = "artist_id") val artistId: Long,
    @ColumnInfo(name = "album_artist") val albumArtist: String? = null,
    @ColumnInfo(name = "album_name") val albumName: String,
    @ColumnInfo(name = "album_id") val albumId: Long,
    @ColumnInfo(name = "content_uri_string") val contentUriString: String,
    @ColumnInfo(name = "album_art_uri_string") val albumArtUriString: String?,
    @ColumnInfo(name = "duration") val duration: Long,
    @ColumnInfo(name = "genre") val genre: String?,
    @ColumnInfo(name = "file_path") val filePath: String,
    @ColumnInfo(name = "parent_directory_path") val parentDirectoryPath: String,
    @ColumnInfo(name = "is_favorite", defaultValue = "0") val isFavorite: Boolean = false,
    @ColumnInfo(name = "lyrics", defaultValue = "null") val lyrics: String? = null,
    @ColumnInfo(name = "track_number", defaultValue = "0") val trackNumber: Int = 0,
    @ColumnInfo(name = "year", defaultValue = "0") val year: Int = 0,
    @ColumnInfo(name = "date_added", defaultValue = "0") val dateAdded: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "mime_type") val mimeType: String? = null,
    @ColumnInfo(name = "bitrate") val bitrate: Int? = null,
    @ColumnInfo(name = "sample_rate") val sampleRate: Int? = null,
    @ColumnInfo(name = "telegram_chat_id") val telegramChatId: Long? = null,
    @ColumnInfo(name = "telegram_file_id") val telegramFileId: Int? = null,
    @ColumnInfo(name = "bpm") val bpm: Int? = null,
    @ColumnInfo(name = "mood") val mood: String? = null
)

fun SongEntity.toSong(): Song {
    return Song(
        id = this.id.toString(),
        title = this.title.normalizeMetadataTextOrEmpty(),
        artist = this.artistName.normalizeMetadataTextOrEmpty(),
        artistId = this.artistId,
        artists = emptyList(),
        album = this.albumName.normalizeMetadataTextOrEmpty(),
        albumId = this.albumId,
        albumArtist = this.albumArtist?.normalizeMetadataText(),
        path = this.filePath,
        contentUriString = this.contentUriString,
        albumArtUriString = this.albumArtUriString,
        duration = this.duration,
        genre = this.genre.normalizeMetadataText(),
        lyrics = this.lyrics?.normalizeMetadataText(),
        isFavorite = this.isFavorite,
        trackNumber = this.trackNumber,
        dateAdded = this.dateAdded,
        year = this.year,
        telegramChatId = this.telegramChatId,
        telegramFileId = this.telegramFileId,
        mimeType = this.mimeType,
        bitrate = this.bitrate,
        sampleRate = this.sampleRate,
        bpm = this.bpm,
        mood = this.mood
    )
}

fun SongEntity.toSongWithArtistRefs(artists: List<ArtistEntity>, crossRefs: List<SongArtistCrossRef>): Song {
    val crossRefByArtistId = crossRefs.associateBy { it.artistId }
    val artistRefs = artists.map { artist ->
        val crossRef = crossRefByArtistId[artist.id]
        ArtistRef(
            id = artist.id,
            name = artist.name.normalizeMetadataTextOrEmpty(),
            isPrimary = crossRef?.isPrimary ?: false
        )
    }.sortedByDescending { it.isPrimary }
    
    return Song(
        id = this.id.toString(),
        title = this.title.normalizeMetadataTextOrEmpty(),
        artist = this.artistName.normalizeMetadataTextOrEmpty(),
        artistId = this.artistId,
        artists = artistRefs,
        album = this.albumName.normalizeMetadataTextOrEmpty(),
        albumId = this.albumId,
        albumArtist = this.albumArtist?.normalizeMetadataText(),
        path = this.filePath,
        contentUriString = this.contentUriString,
        albumArtUriString = this.albumArtUriString,
        duration = this.duration,
        genre = this.genre.normalizeMetadataText(),
        lyrics = this.lyrics?.normalizeMetadataText(),
        isFavorite = this.isFavorite,
        trackNumber = this.trackNumber,
        dateAdded = this.dateAdded,
        year = this.year,
        telegramChatId = this.telegramChatId,
        telegramFileId = this.telegramFileId,
        mimeType = this.mimeType,
        bitrate = this.bitrate,
        sampleRate = this.sampleRate,
        bpm = this.bpm,
        mood = this.mood
    )
}

fun List<SongEntity>.toSongs(): List<Song> {
    return this.map { it.toSong() }
}

fun Song.toEntity(filePathFromMediaStore: String, parentDirFromMediaStore: String): SongEntity {
    return SongEntity(
        id = this.id.toLong(),
        title = this.title,
        artistName = this.artist,
        artistId = this.artistId,
        albumArtist = this.albumArtist,
        albumName = this.album,
        albumId = this.albumId,
        contentUriString = this.contentUriString,
        albumArtUriString = this.albumArtUriString,
        duration = this.duration,
        genre = this.genre,
        lyrics = this.lyrics,
        filePath = filePathFromMediaStore,
        parentDirectoryPath = parentDirFromMediaStore,
        dateAdded = this.dateAdded,
        year = this.year,
        mimeType = this.mimeType,
        bitrate = this.bitrate,
        sampleRate = this.sampleRate,
        bpm = this.bpm,
        mood = this.mood
    )
}
