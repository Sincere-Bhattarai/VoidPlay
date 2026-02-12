package com.theveloper.pixelplay.data.backup.validation

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.theveloper.pixelplay.data.backup.format.BackupFormatDetector
import com.theveloper.pixelplay.data.backup.model.BackupValidationResult
import com.theveloper.pixelplay.data.backup.model.Severity
import com.theveloper.pixelplay.data.backup.model.ValidationError
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupFileValidator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val formatDetector: BackupFormatDetector
) {
    companion object {
        const val MAX_BACKUP_SIZE_BYTES = 50L * 1024 * 1024 // 50 MB
        const val MAX_ZIP_RATIO = 100 // max decompressed/compressed ratio
    }

    fun validate(uri: Uri): BackupValidationResult {
        val errors = mutableListOf<ValidationError>()

        // Check URI accessibility
        val rawBytes = try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (e: Exception) {
            errors.add(ValidationError("FILE_ACCESS", "Cannot open backup file: ${e.message}"))
            return BackupValidationResult.Invalid(errors)
        }

        if (rawBytes == null) {
            errors.add(ValidationError("FILE_EMPTY", "Backup file is empty or inaccessible."))
            return BackupValidationResult.Invalid(errors)
        }

        // Check file size
        if (rawBytes.size.toLong() > MAX_BACKUP_SIZE_BYTES) {
            errors.add(ValidationError("FILE_TOO_LARGE", "Backup file exceeds the ${MAX_BACKUP_SIZE_BYTES / (1024 * 1024)}MB limit."))
            return BackupValidationResult.Invalid(errors)
        }

        // Check file name extension (if available)
        val docFile = DocumentFile.fromSingleUri(context, uri)
        val fileName = docFile?.name
        if (fileName != null && !fileName.endsWith(".pxpl", ignoreCase = true) &&
            !fileName.endsWith(".gz", ignoreCase = true)) {
            errors.add(ValidationError("FILE_EXTENSION", "File extension is not .pxpl. The file may not be a valid backup.", severity = Severity.WARNING))
        }

        // Detect format
        val header = rawBytes.copyOf(minOf(8, rawBytes.size))
        val format = formatDetector.detect(header)

        if (format == BackupFormatDetector.Format.UNKNOWN) {
            errors.add(ValidationError("FORMAT_UNKNOWN", "File is not a recognized PixelPlay backup format."))
            return BackupValidationResult.Invalid(errors)
        }

        // For ZIP format: validate zip structure safety
        if (format == BackupFormatDetector.Format.PXPL_V3_ZIP) {
            validateZipSafety(rawBytes, BackupFormatDetector.PXPL_MAGIC_SIZE, errors)
        }

        return if (errors.any { it.severity == Severity.ERROR }) {
            BackupValidationResult.Invalid(errors)
        } else if (errors.isNotEmpty()) {
            BackupValidationResult.Invalid(errors)
        } else {
            BackupValidationResult.Valid
        }
    }

    private fun validateZipSafety(rawBytes: ByteArray, offset: Int, errors: MutableList<ValidationError>) {
        try {
            val zipBytes = rawBytes.copyOfRange(offset, rawBytes.size)
            ZipInputStream(ByteArrayInputStream(zipBytes)).use { zip ->
                var entry = zip.nextEntry
                var totalDecompressed = 0L
                while (entry != null) {
                    val name = entry.name

                    // Path traversal check
                    if (name.contains("..") || name.startsWith("/") || name.startsWith("\\")) {
                        errors.add(ValidationError("ZIP_PATH_TRAVERSAL", "Suspicious zip entry path: $name"))
                        return
                    }

                    // Only allow .json files and manifest
                    if (!name.endsWith(".json")) {
                        errors.add(ValidationError("ZIP_UNEXPECTED_ENTRY", "Unexpected file in backup: $name", severity = Severity.WARNING))
                    }

                    // Track decompressed size
                    val content = zip.readBytes()
                    totalDecompressed += content.size

                    // Zip bomb detection
                    if (zipBytes.isNotEmpty() && totalDecompressed > zipBytes.size.toLong() * MAX_ZIP_RATIO) {
                        errors.add(ValidationError("ZIP_BOMB", "Backup file has suspicious compression ratio."))
                        return
                    }

                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        } catch (e: Exception) {
            errors.add(ValidationError("ZIP_CORRUPT", "Backup ZIP archive is corrupted: ${e.message}"))
        }
    }
}
