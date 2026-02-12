package com.theveloper.pixelplay.data.backup.format

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.theveloper.pixelplay.data.backup.model.BackupManifest
import com.theveloper.pixelplay.di.BackupGson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupReader @Inject constructor(
    @ApplicationContext private val context: Context,
    @BackupGson private val gson: Gson,
    private val formatDetector: BackupFormatDetector,
    private val legacyAdapter: LegacyPayloadAdapter
) {
    /**
     * Reads only the manifest from a backup file (efficient for inspection/preview).
     */
    suspend fun readManifest(uri: Uri): Result<BackupManifest> = withContext(Dispatchers.IO) {
        runCatching {
            val rawBytes = readRawBytes(uri)
            val header = rawBytes.copyOf(minOf(8, rawBytes.size))
            val format = formatDetector.detect(header)

            when (format) {
                BackupFormatDetector.Format.PXPL_V3_ZIP -> {
                    readManifestFromZip(rawBytes, BackupFormatDetector.PXPL_MAGIC_SIZE)
                }
                BackupFormatDetector.Format.PXPL_V2_GZIP,
                BackupFormatDetector.Format.LEGACY_GZIP,
                BackupFormatDetector.Format.LEGACY_RAW -> {
                    val json = decompressLegacy(rawBytes, format)
                    val (manifest, _) = legacyAdapter.adapt(json, gson)
                    manifest
                }
                BackupFormatDetector.Format.UNKNOWN -> {
                    throw IllegalArgumentException("Unrecognized backup file format")
                }
            }
        }
    }

    /**
     * Reads a specific module's JSON payload from the backup.
     */
    suspend fun readModulePayload(uri: Uri, moduleKey: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val rawBytes = readRawBytes(uri)
            val header = rawBytes.copyOf(minOf(8, rawBytes.size))
            val format = formatDetector.detect(header)

            when (format) {
                BackupFormatDetector.Format.PXPL_V3_ZIP -> {
                    readEntryFromZip(rawBytes, BackupFormatDetector.PXPL_MAGIC_SIZE, "$moduleKey.json")
                        ?: throw IllegalArgumentException("Module '$moduleKey' not found in backup")
                }
                BackupFormatDetector.Format.PXPL_V2_GZIP,
                BackupFormatDetector.Format.LEGACY_GZIP,
                BackupFormatDetector.Format.LEGACY_RAW -> {
                    val json = decompressLegacy(rawBytes, format)
                    val (_, modules) = legacyAdapter.adapt(json, gson)
                    modules[moduleKey]
                        ?: throw IllegalArgumentException("Module '$moduleKey' not found in legacy backup")
                }
                BackupFormatDetector.Format.UNKNOWN -> {
                    throw IllegalArgumentException("Unrecognized backup file format")
                }
            }
        }
    }

    /**
     * Reads all module payloads from the backup.
     */
    suspend fun readAllModulePayloads(uri: Uri): Result<Map<String, String>> = withContext(Dispatchers.IO) {
        runCatching {
            val rawBytes = readRawBytes(uri)
            val header = rawBytes.copyOf(minOf(8, rawBytes.size))
            val format = formatDetector.detect(header)

            when (format) {
                BackupFormatDetector.Format.PXPL_V3_ZIP -> {
                    readAllEntriesFromZip(rawBytes, BackupFormatDetector.PXPL_MAGIC_SIZE)
                }
                BackupFormatDetector.Format.PXPL_V2_GZIP,
                BackupFormatDetector.Format.LEGACY_GZIP,
                BackupFormatDetector.Format.LEGACY_RAW -> {
                    val json = decompressLegacy(rawBytes, format)
                    val (_, modules) = legacyAdapter.adapt(json, gson)
                    modules
                }
                BackupFormatDetector.Format.UNKNOWN -> {
                    throw IllegalArgumentException("Unrecognized backup file format")
                }
            }
        }
    }

    /**
     * Detects the format of the backup file.
     */
    suspend fun detectFormat(uri: Uri): Result<BackupFormatDetector.Format> = withContext(Dispatchers.IO) {
        runCatching {
            val rawBytes = readRawBytes(uri)
            val header = rawBytes.copyOf(minOf(8, rawBytes.size))
            formatDetector.detect(header)
        }
    }

    private fun readRawBytes(uri: Uri): ByteArray {
        return context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("Unable to open backup file")
    }

    private fun readManifestFromZip(rawBytes: ByteArray, offset: Int): BackupManifest {
        val json = readEntryFromZip(rawBytes, offset, BackupManifest.MANIFEST_FILENAME)
            ?: throw IllegalArgumentException("Manifest not found in backup archive")
        return gson.fromJson(json, BackupManifest::class.java)
    }

    private fun readEntryFromZip(rawBytes: ByteArray, offset: Int, entryName: String): String? {
        val zipBytes = rawBytes.copyOfRange(offset, rawBytes.size)
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == entryName) {
                    return zip.bufferedReader(Charsets.UTF_8).readText()
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return null
    }

    private fun readAllEntriesFromZip(rawBytes: ByteArray, offset: Int): Map<String, String> {
        val entries = mutableMapOf<String, String>()
        val zipBytes = rawBytes.copyOfRange(offset, rawBytes.size)
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val name = entry.name
                if (name != BackupManifest.MANIFEST_FILENAME && name.endsWith(".json")) {
                    val key = name.removeSuffix(".json")
                    entries[key] = zip.bufferedReader(Charsets.UTF_8).readText()
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return entries
    }

    private fun decompressLegacy(rawBytes: ByteArray, format: BackupFormatDetector.Format): String {
        return when (format) {
            BackupFormatDetector.Format.PXPL_V2_GZIP -> {
                val compressed = rawBytes.copyOfRange(BackupFormatDetector.PXPL_MAGIC_SIZE, rawBytes.size)
                GZIPInputStream(ByteArrayInputStream(compressed)).bufferedReader().use { it.readText() }
            }
            BackupFormatDetector.Format.LEGACY_GZIP -> {
                GZIPInputStream(ByteArrayInputStream(rawBytes)).bufferedReader().use { it.readText() }
            }
            BackupFormatDetector.Format.LEGACY_RAW -> {
                rawBytes.toString(Charsets.UTF_8)
            }
            else -> throw IllegalArgumentException("Cannot decompress format: $format")
        }
    }
}
