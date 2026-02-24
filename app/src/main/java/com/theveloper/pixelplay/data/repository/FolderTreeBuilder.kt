package com.theveloper.voidplay.data.repository

import android.content.Context
import android.os.Environment
import com.theveloper.voidplay.data.database.FolderSongRow
import com.theveloper.voidplay.data.model.FolderSource
import com.theveloper.voidplay.data.model.MusicFolder
import com.theveloper.voidplay.data.model.Song
import com.theveloper.voidplay.utils.DirectoryRuleResolver
import com.theveloper.voidplay.utils.StorageType
import com.theveloper.voidplay.utils.StorageUtils
import kotlinx.collections.immutable.toImmutableList
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FolderTreeBuilder @Inject constructor() {

    fun buildFolderTree(
        folderSongs: List<FolderSongRow>,
        allowedDirs: Set<String>,
        blockedDirs: Set<String>,
        isFolderFilterActive: Boolean,
        folderSource: FolderSource,
        context: Context
    ): List<MusicFolder> {
        // 1. Resolve Rules
        val resolver = DirectoryRuleResolver(allowedDirs, blockedDirs)

        // 2. Filter Songs based on Directory Rules
        val filteredSongs = if (isFolderFilterActive && blockedDirs.isNotEmpty()) {
            folderSongs.filter { song ->
                val normalizedParent = normalizePath(song.parentDirectoryPath)
                normalizedParent.isNotBlank() && !resolver.isBlocked(normalizedParent)
            }
        } else {
            folderSongs
        }

        if (filteredSongs.isEmpty()) return emptyList()

        // 3. Determine Root Path based on Source
        val storages = StorageUtils.getAvailableStorages(context)
        val internalStorageRoot = storages
            .firstOrNull { it.storageType == StorageType.INTERNAL }
            ?.path
            ?.path
            ?: Environment.getExternalStorageDirectory().path
        val sdStorageRoot = storages
            .firstOrNull { it.storageType == StorageType.SD_CARD }
            ?.path
            ?.path

        val selectedRootPath = when (folderSource) {
            FolderSource.INTERNAL -> internalStorageRoot
            FolderSource.SD_CARD -> sdStorageRoot ?: return emptyList()
        }
        
        val normalizedSelectedRoot = normalizePath(selectedRootPath)

        // 4. Group Songs and Build Tree
        val songsToProcess = filteredSongs.filter { song ->
            normalizePath(song.parentDirectoryPath).startsWith(normalizedSelectedRoot)
        }

        if (songsToProcess.isEmpty()) return emptyList()
        
        val folderMap = mutableMapOf<String, TempFolder>()
        val rootFolder = getOrCreateTempFolder(normalizedSelectedRoot, folderMap, getNameFromPath(normalizedSelectedRoot))

        for (song in songsToProcess) {
            val parentPath = normalizePath(song.parentDirectoryPath)
            if (parentPath.isBlank()) continue

            // Get or create the folder for this song
            val folder = getOrCreateTempFolder(parentPath, folderMap, getNameFromPath(parentPath))
            folder.songs.add(song.toFolderStubSong())
            
            // Ensure hierarchy
            var currentPath = parentPath
            while (currentPath.length > normalizedSelectedRoot.length && currentPath.startsWith(normalizedSelectedRoot)) {
                val parentOfCurrent = getParentPath(currentPath) ?: break
                
                // If we went above root, stop
                if (parentOfCurrent.length < normalizedSelectedRoot.length) break
                
                val parentFolder = getOrCreateTempFolder(parentOfCurrent, folderMap, getNameFromPath(parentOfCurrent))
                val added = parentFolder.subFolderPaths.add(currentPath)
                
                if (!added) break 
                
                currentPath = parentOfCurrent
            }
        }
        
        return rootFolder.subFolderPaths
            .mapNotNull { path -> buildImmutableFolder(path, folderMap) }
            .filter { it.totalSongCount > 0 }
            .sortedBy { it.name.lowercase() }
    }

    private fun getOrCreateTempFolder(path: String, map: MutableMap<String, TempFolder>, name: String): TempFolder {
        return map.getOrPut(path) {
            TempFolder(path, name)
        }
    }

    private fun buildImmutableFolder(path: String, map: Map<String, TempFolder>): MusicFolder? {
        val temp = map[path] ?: return null
        
        // Recursively build subfolders
        val subFolders = temp.subFolderPaths
            .mapNotNull { subPath -> buildImmutableFolder(subPath, map) }
            .sortedBy { it.name.lowercase() }
            .toImmutableList()
            
        return MusicFolder(
            path = temp.path,
            name = temp.name,
            songs = temp.songs
                .sortedWith(
                    compareBy<Song> { if (it.trackNumber > 0) it.trackNumber else Int.MAX_VALUE }
                        .thenBy { it.title.lowercase() }
                )
                .toImmutableList(),
            subFolders = subFolders
        )
    }

    private fun getParentPath(path: String): String? {
        val lastSeparatorIndex = path.lastIndexOf('/')
        if (lastSeparatorIndex <= 0) return null 
        return path.substring(0, lastSeparatorIndex)
    }

    private fun getNameFromPath(path: String): String {
        val lastSeparatorIndex = path.lastIndexOf('/')
        if (lastSeparatorIndex == -1) return path
        return path.substring(lastSeparatorIndex + 1)
    }
    
    private fun normalizePath(path: String): String {
        return if (path.endsWith("/")) path.dropLast(1) else path
    }

    private fun FolderSongRow.toFolderStubSong(): Song {
        val parentPath = normalizePath(parentDirectoryPath)
        val syntheticPath = if (parentPath.isBlank()) title else "$parentPath/$title"
        return Song(
            id = id.toString(),
            title = title,
            artist = "",
            artistId = -1L,
            album = "",
            albumId = -1L,
            path = syntheticPath,
            contentUriString = "",
            albumArtUriString = albumArtUriString,
            duration = 0L,
            trackNumber = 0,
            year = 0,
            dateAdded = 0L,
            dateModified = 0L,
            mimeType = null,
            bitrate = null,
            sampleRate = null
        )
    }

    private class TempFolder(
        val path: String,
        val name: String,
        val songs: MutableList<Song> = ArrayList(), 
        val subFolderPaths: MutableSet<String> = HashSet() 
    )
}
