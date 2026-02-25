package com.voidcorp.voidplay.utils

import com.voidcorp.voidplay.data.model.Song

object SonicShuffleUtils {
    /**
     * Clusters songs based on Genre, BPM, and Mood to create a "path" for shuffling.
     * This is used as an offline alternative to random shuffle.
     */
    fun clusterShuffle(songs: List<Song>): List<Song> {
        if (songs.size <= 1) return songs

        // Simple clustering: Sort by Genre, then Mood, then BPM
        // In a real scenario, this would be a more complex clustering algorithm.
        return songs.sortedWith(
            compareBy<Song> { it.genre ?: "" }
                .thenBy { it.mood ?: "" }
                .thenBy { it.bpm ?: 0 }
        ).shuffled().let { shuffledList ->
            // Re-sort a bit to group similar things together but keep it "shuffled"
            shuffledList.sortedBy { it.genre }
        }
    }
}
