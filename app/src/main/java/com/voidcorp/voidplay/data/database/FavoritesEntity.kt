package com.voidcorp.voidplay.data.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "favorites",
    indices = [
        Index(value = ["timestamp"], unique = false)
    ]
)
data class FavoritesEntity(
    @PrimaryKey val songId: Long,
    val isFavorite: Boolean = true,
    val timestamp: Long = System.currentTimeMillis()
)
