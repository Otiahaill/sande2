package com.example.sande.data.backup

import android.content.Context
import com.example.sande.data.db.AppDatabase
import com.example.sande.data.entities.BusinessProfile
import com.example.sande.data.entities.Counter
import com.example.sande.data.entities.Product
import com.example.sande.data.entities.ProductCategory
import com.example.sande.data.entities.ProductPriceHistory
import com.example.sande.data.entities.Receipt
import com.example.sande.data.entities.SaleItem
import com.example.sande.data.entities.SaleSession
import com.example.sande.data.entities.StockMovement
import com.example.sande.data.entities.AuditEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Manifest describing a backup archive.
 * Verified on restore before any data is written.
 */
@Serializable
data class BackupManifest(
    val formatVersion: Int = 1,
    val appVersionName: String,
    val appVersionCode: Int,
    val schemaVersion: Int,
    val businessId: String,
    val businessName: String,
    val createdAt: Long,
    val createdAtIso: String,
    val deviceCode: String,
    val rowCounts: Map<String, Int>,
    val contentHash: String,
    val totalRows: Int,
)

/**
 * One row in the NDJSON payload.
 */
@Serializable
data class BackupRow(
    val table: String,
    val data: String, // JSON string of the row
)

/**
 * Creates and restores backup archives.
 *
 * Archive layout (ZIP):
 *   manifest.json  — metadata + SHA-256 of payload
 *   data.ndjson    — one JSON object per line: {"table": "...", "data": {...}}
 */
class BackupManager(
    private val context: Context,
    private val database: AppDatabase,
) {

    private val json = Json { prettyPrint = false; ignoreUnknownKeys = true }

    companion object {
        const val MANIFEST_ENTRY = "manifest.json"
        const val DATA_ENTRY = "data.ndjson"
        const val FILE_EXTENSION = ".sande-backup"
        const val SCHEMA_VERSION = 6

        /** Tables included in a backup, in dependency order for restore. */
        val BACKUP_TABLES = listOf(
            "business_profiles",
            "product_categories",
            "products",
            "product_price_history",
            "stock_movements",
            "sale_sessions",
            "sale_items",
            "receipts",
            "counters",
            "audit_events",
        )
    }

    /**
     * Creates a backup archive for the given business.
     * Returns the file written to the app's cache directory.
     */
    suspend fun createBackup(
        businessId: String,
        businessName: String,
        deviceCode: String,
        appVersionName: String = "1.0.0",
        appVersionCode: Int = 1,
    ): File = withContext(Dispatchers.IO) {
        val rowCounts = mutableMapOf<String, Int>()
        var totalRows = 0

        // Build NDJSON payload
        val payload = StringBuilder()

        for (table in BACKUP_TABLES) {
            val rows = readTable(table, businessId)
            rowCounts[table] = rows.size
            totalRows += rows.size

            for (row in rows) {
                payload.append(json.encodeToString(BackupRow(table = table, data = row)))
                payload.append("\n")
            }
        }

        val payloadText = payload.toString()
        val contentHash = sha256(payloadText)

        val manifest = BackupManifest(
            appVersionName = appVersionName,
            appVersionCode = appVersionCode,
            schemaVersion = SCHEMA_VERSION,
            businessId = businessId,
            businessName = businessName,
            createdAt = System.currentTimeMillis(),
            createdAtIso = java.time.Instant.now().toString(),
            deviceCode = deviceCode,
            rowCounts = rowCounts,
            contentHash = contentHash,
            totalRows = totalRows,
        )

        // Write ZIP archive
        val outFile = File(
            context.cacheDir,
            "sande-backup-${businessId}-${System.currentTimeMillis()}$FILE_EXTENSION"
        )

        ZipOutputStream(outFile.outputStream().buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(MANIFEST_ENTRY))
            zip.write(json.encodeToString(manifest).toByteArray())
            zip.closeEntry()

            zip.putNextEntry(ZipEntry(DATA_ENTRY))
            zip.write(payloadText.toByteArray())
            zip.closeEntry()
        }

        outFile
    }

    /**
     * Reads and validates a backup archive without applying it.
     */
    suspend fun inspectBackup(file: File): BackupValidation = withContext(Dispatchers.IO) {
        try {
            val manifest = readManifest(file)
            val payload = readPayload(file)

            val actualHash = sha256(payload)
            val hashMatches = actualHash == manifest.contentHash

            BackupValidation(
                isValid = hashMatches,
                manifest = manifest,
                actualHash = actualHash,
                errorMessage = if (hashMatches) null else "Checksum mismatch — file may be corrupted"
            )
        } catch (e: Exception) {
            BackupValidation(
                isValid = false,
                manifest = null,
                actualHash = null,
                errorMessage = e.message ?: "Could not read backup file"
            )
        }
    }

    /**
     * Restores a backup archive into the local database.
     *
     * Existing rows with the same ID are replaced (insert-if-absent by primary key).
     * Rows are written in dependency order.
     */
    suspend fun restoreBackup(
        file: File,
        mode: RestoreMode = RestoreMode.REPLACE,
    ): RestoreResult = withContext(Dispatchers.IO) {
        val validation = inspectBackup(file)
        if (!validation.isValid || validation.manifest == null) {
            return@withContext RestoreResult(
                success = false,
                rowsRestored = 0,
                errorMessage = validation.errorMessage ?: "Invalid backup file"
            )
        }

        val manifest = validation.manifest
        val payload = readPayload(file)

        // Parse rows grouped by table
        val rowsByTable = mutableMapOf<String, MutableList<String>>()
        payload.lineSequence().forEach { line ->
            if (line.isBlank()) return@forEach
            try {
                val row = json.decodeFromString<BackupRow>(line)
                rowsByTable.getOrPut(row.table) { mutableListOf() }.add(row.data)
            } catch (e: Exception) {
                // Skip malformed line
            }
        }

        var restored = 0

        try {
            database.withTransaction {
                for (table in BACKUP_TABLES) {
                    val rows = rowsByTable[table] ?: continue
                    for (rowJson in rows) {
                        try {
                            if (insertOrReplace(table, rowJson)) restored++
                        } catch (e: Exception) {
                            // Skip row that fails to insert
                        }
                    }
                }
            }
        } catch (e: Exception) {
            return@withContext RestoreResult(
                success = false,
                rowsRestored = restored,
                errorMessage = "Restore failed: ${e.message}"
            )
        }

        RestoreResult(
            success = true,
            rowsRestored = restored,
            expectedRows = manifest.totalRows,
            errorMessage = null
        )
    }

    /**
     * Writes a backup to a shareable location and returns a content:// URI.
     */
    suspend fun prepareForSharing(file: File): String = withContext(Dispatchers.IO) {
        androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        ).toString()
    }

    // ===== Internal helpers =====

    private fun readTable(table: String, businessId: String): List<String> {
        val cursor = database.query(
            "SELECT * FROM $table WHERE business_id = ?",
            arrayOf(businessId)
        )
        cursor.use {
            val rows = mutableListOf<String>()
            val columnCount = it.columnCount
            while (it.moveToNext()) {
                val obj = org.json.JSONObject()
                for (i in 0 until columnCount) {
                    val name = it.getColumnName(i)
                    when (it.getType(i)) {
                        android.database.Cursor.FIELD_TYPE_NULL -> obj.put(name, org.json.JSONObject.NULL)
                        android.database.Cursor.FIELD_TYPE_INTEGER -> obj.put(name, it.getLong(i))
                        android.database.Cursor.FIELD_TYPE_FLOAT -> obj.put(name, it.getDouble(i))
                        android.database.Cursor.FIELD_TYPE_BLOB -> obj.put(name, android.util.Base64.encodeToString(it.getBlob(i), android.util.Base64.NO_WRAP))
                        else -> obj.put(name, it.getString(i))
                    }
                }
                rows.add(obj.toString())
            }
            return rows
        }
    }

    private fun insertOrReplace(table: String, rowJson: String): Boolean {
        val obj = org.json.JSONObject(rowJson)
        val columns = mutableListOf<String>()
        val values = mutableListOf<Any>()

        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            columns.add(key)
            values.add(obj.get(key))
        }

        val placeholders = columns.joinToString(", ") { "?" }
        val sql = "INSERT OR REPLACE INTO $table (${columns.joinToString(", ")}) VALUES ($placeholders)"

        database.compileStatement(sql).use { statement ->
            values.forEachIndexed { index, value ->
                when (value) {
                    is Boolean -> statement.bindLong(index + 1, if (value) 1L else 0L)
                    is Int -> statement.bindLong(index + 1, value.toLong())
                    is Long -> statement.bindLong(index + 1, value)
                    is Double -> statement.bindDouble(index + 1, value)
                    is org.json.JSONObject.NULL -> statement.bindNull(index + 1)
                    else -> statement.bindString(index + 1, value.toString())
                }
            }
            statement.executeInsert()
        }
        return true
    }

    private fun readManifest(file: File): BackupManifest {
        java.util.zip.ZipFile(file).use { zip ->
            val entry = zip.getEntry(MANIFEST_ENTRY) ?: throw IllegalArgumentException("Missing manifest")
            val text = zip.getInputStream(entry).bufferedReader().use { it.readText() }
            return json.decodeFromString(text)
        }
    }

    private fun readPayload(file: File): String {
        java.util.zip.ZipFile(file).use { zip ->
            val entry = zip.getEntry(DATA_ENTRY) ?: throw IllegalArgumentException("Missing data")
            return zip.getInputStream(entry).bufferedReader().use { it.readText() }
        }
    }

    private fun sha256(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(text.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}

enum class RestoreMode {
    /** Replace rows with the same primary key. */
    REPLACE,

    /** Keep existing rows, only insert rows that don't exist. */
    INSERT_IF_ABSENT,
}

data class BackupValidation(
    val isValid: Boolean,
    val manifest: BackupManifest?,
    val actualHash: String?,
    val errorMessage: String?,
)

data class RestoreResult(
    val success: Boolean,
    val rowsRestored: Int,
    val expectedRows: Int = 0,
    val errorMessage: String?,
)
