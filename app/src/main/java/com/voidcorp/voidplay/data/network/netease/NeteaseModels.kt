package com.voidcorp.voidplay.data.network.netease

import com.google.gson.annotations.SerializedName

// ─── Login ─────────────────────────────────────────────────────────────

data class NeteaseLoginResponse(
    val code: Int,
    val cookie: String?,
    val token: String?,
    val profile: NeteaseProfile?
)

data class NeteaseProfile(
    val userId: Long,
    val nickname: String,
    val avatarUrl: String?,
    val signature: String?
)

data class NeteaseLoginStatusResponse(
    val data: NeteaseLoginStatusData?
)

data class NeteaseLoginStatusData(
    val code: Int,
    val profile: NeteaseProfile?
)

// ─── Playlists ─────────────────────────────────────────────────────────

data class NeteaseUserPlaylistResponse(
    val code: Int,
    val playlist: List<NeteasePlaylist>?
)

data class NeteasePlaylist(
    val id: Long,
    val name: String,
    val coverImgUrl: String?,
    val trackCount: Int,
    val userId: Long,
    val description: String?,
    @SerializedName("playCount") val playCount: Long?
)

// ─── Tracks / Songs ────────────────────────────────────────────────────

data class NeteasePlaylistTrackResponse(
    val code: Int,
    val songs: List<NeteaseTrack>?
)

data class NeteaseTrack(
    val id: Long,
    val name: String,
    @SerializedName("ar") val artists: List<NeteaseArtist>?,
    @SerializedName("al") val album: NeteaseAlbum?,
    @SerializedName("dt") val duration: Long, // milliseconds
    @SerializedName("mv") val mvId: Long?,
    @SerializedName("publishTime") val publishTime: Long?
)

data class NeteaseArtist(
    val id: Long,
    val name: String
)

data class NeteaseAlbum(
    val id: Long,
    val name: String,
    val picUrl: String?
)

// ─── Song URL ──────────────────────────────────────────────────────────

data class NeteaseSongUrlResponse(
    val code: Int,
    val data: List<NeteaseSongUrl>?
)

data class NeteaseSongUrl(
    val id: Long,
    val url: String?,
    val br: Int?, // bitrate
    val size: Long?,
    val type: String?, // mp3, flac, etc.
    @SerializedName("encodeType") val encodeType: String?,
    val level: String? // standard, higher, exhigh, lossless, hires
)

// ─── Song Detail ───────────────────────────────────────────────────────

data class NeteaseSongDetailResponse(
    val code: Int,
    val songs: List<NeteaseTrack>?
)

// ─── Search ────────────────────────────────────────────────────────────

data class NeteaseSearchResponse(
    val code: Int,
    val result: NeteaseSearchResult?
)

data class NeteaseSearchResult(
    val songCount: Int?,
    val songs: List<NeteaseTrack>?
)

// ─── Lyrics ────────────────────────────────────────────────────────────

data class NeteaseLyricResponse(
    val code: Int,
    val lrc: NeteaseLyricContent?,
    val tlyric: NeteaseLyricContent? // translated lyrics
)

data class NeteaseLyricContent(
    val version: Int?,
    val lyric: String?
)

// ─── Like List ─────────────────────────────────────────────────────────

data class NeteaseLikeListResponse(
    val code: Int,
    val ids: List<Long>?
)

// ─── Base Response ─────────────────────────────────────────────────────

/** Generic response for simple operations (logout, SMS send, etc.) */
data class NeteaseBaseResponse(
    val code: Int,
    val message: String? = null,
    val data: Boolean? = null
)

// ─── Playlist Detail (v6 endpoint wraps tracks inside playlist) ───────

data class NeteasePlaylistDetailResponse(
    val code: Int,
    val playlist: NeteasePlaylistDetail?
)

data class NeteasePlaylistDetail(
    val id: Long,
    val name: String,
    val coverImgUrl: String?,
    val trackCount: Int,
    val tracks: List<NeteaseTrack>?
)

