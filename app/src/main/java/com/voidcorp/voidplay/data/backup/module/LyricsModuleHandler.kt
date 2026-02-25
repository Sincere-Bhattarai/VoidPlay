package com.voidcorp.voidplay.data.backup.module

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.voidcorp.voidplay.data.backup.model.BackupSection
import com.voidcorp.voidplay.data.database.LyricsDao
import com.voidcorp.voidplay.data.database.LyricsEntity
import com.voidcorp.voidplay.di.BackupGson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LyricsModuleHandler @Inject constructor(
    private val lyricsDao: LyricsDao,
    @BackupGson private val gson: Gson
) : BackupModuleHandler {

    override val section = BackupSection.LYRICS

    override suspend fun export(): String = withContext(Dispatchers.IO) {
        gson.toJson(lyricsDao.getAll())
    }

    override suspend fun countEntries(): Int = withContext(Dispatchers.IO) {
        lyricsDao.getAll().size
    }

    override suspend fun snapshot(): String = export()

    override suspend fun restore(payload: String) = withContext(Dispatchers.IO) {
        val type = TypeToken.getParameterized(List::class.java, LyricsEntity::class.java).type
        val lyrics: List<LyricsEntity> = gson.fromJson(payload, type)
        lyricsDao.deleteAll()
        if (lyrics.isNotEmpty()) {
            lyricsDao.insertAll(lyrics)
        }
    }

    override suspend fun rollback(snapshot: String) = restore(snapshot)
}
