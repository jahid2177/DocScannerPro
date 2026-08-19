package com.scanner.pro.repository

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.scanner.pro.model.ScanDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

data class ScanFolder(
    val id: String = UUID.randomUUID().toString(),
    var name: String,
    val createdAt: Long = System.currentTimeMillis()
)

enum class SortOrder { DATE_NEWEST, DATE_OLDEST, NAME_AZ, NAME_ZA }

/**
 * Local, offline-only persistence for scanned documents and folders.
 *
 * Deliberately avoids Room: this project targets AndroidIDE, where kapt (Room's
 * annotation processor) is unreliable and has previously caused build crashes
 * on other projects in this codebase. A single Gson-serialized JSON index file
 * plus per-document image files on disk is simpler, has zero annotation
 * processing, and is plenty fast for the document counts a scanner app sees.
 */
class FileManager(context: Context) {

    private val gson: Gson = GsonBuilder().create()
    private val mutex = Mutex()

    private val rootDir = File(context.filesDir, "scans").apply { mkdirs() }
    private val indexFile = File(context.filesDir, "documents_index.json")
    private val foldersFile = File(context.filesDir, "folders_index.json")

    fun documentDirectory(documentId: String): File = File(rootDir, documentId).apply { mkdirs() }

    // ---------------------------------------------------------------------
    // Documents
    // ---------------------------------------------------------------------

    suspend fun getAllDocuments(): List<ScanDocument> = withContext(Dispatchers.IO) {
        mutex.withLock { readDocuments() }
    }

    suspend fun getDocument(id: String): ScanDocument? =
        getAllDocuments().firstOrNull { it.id == id }

    suspend fun saveDocument(document: ScanDocument) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val all = readDocuments().toMutableList()
            val index = all.indexOfFirst { it.id == document.id }
            document.updatedAt = System.currentTimeMillis()
            if (index >= 0) all[index] = document else all.add(document)
            writeDocuments(all)
        }
    }

    suspend fun deleteDocument(id: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val all = readDocuments().toMutableList()
            all.removeAll { it.id == id }
            writeDocuments(all)
            documentDirectory(id).deleteRecursively()
        }
    }

    suspend fun duplicateDocument(id: String): ScanDocument? = withContext(Dispatchers.IO) {
        val original = getDocument(id) ?: return@withContext null
        val copy = original.copy(
            id = UUID.randomUUID().toString(),
            name = "${original.name} (copy)",
            createdAt = System.currentTimeMillis()
        )
        val srcDir = documentDirectory(original.id)
        val dstDir = documentDirectory(copy.id)
        srcDir.copyRecursively(dstDir, overwrite = true)
        saveDocument(copy)
        copy
    }

    suspend fun toggleFavorite(id: String) = withContext(Dispatchers.IO) {
        getDocument(id)?.let {
            it.isFavorite = !it.isFavorite
            saveDocument(it)
        }
    }

    suspend fun moveToFolder(documentId: String, folderId: String?) = withContext(Dispatchers.IO) {
        getDocument(documentId)?.let {
            it.folderId = folderId
            saveDocument(it)
        }
    }

    suspend fun renameDocument(documentId: String, newName: String) = withContext(Dispatchers.IO) {
        getDocument(documentId)?.let {
            it.name = newName
            saveDocument(it)
        }
    }

    suspend fun search(query: String): List<ScanDocument> =
        getAllDocuments().filter { it.name.contains(query, ignoreCase = true) }

    suspend fun sorted(documents: List<ScanDocument>, order: SortOrder): List<ScanDocument> = when (order) {
        SortOrder.DATE_NEWEST -> documents.sortedByDescending { it.updatedAt }
        SortOrder.DATE_OLDEST -> documents.sortedBy { it.updatedAt }
        SortOrder.NAME_AZ -> documents.sortedBy { it.name.lowercase() }
        SortOrder.NAME_ZA -> documents.sortedByDescending { it.name.lowercase() }
    }

    suspend fun recentScans(limit: Int = 20): List<ScanDocument> =
        sorted(getAllDocuments(), SortOrder.DATE_NEWEST).take(limit)

    private fun readDocuments(): List<ScanDocument> {
        if (!indexFile.exists()) return emptyList()
        return try {
            val type = object : TypeToken<List<ScanDocument>>() {}.type
            gson.fromJson(indexFile.readText(), type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun writeDocuments(documents: List<ScanDocument>) {
        indexFile.writeText(gson.toJson(documents))
    }

    // ---------------------------------------------------------------------
    // Folders
    // ---------------------------------------------------------------------

    suspend fun getAllFolders(): List<ScanFolder> = withContext(Dispatchers.IO) {
        if (!foldersFile.exists()) return@withContext emptyList()
        try {
            val type = object : TypeToken<List<ScanFolder>>() {}.type
            gson.fromJson(foldersFile.readText(), type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun createFolder(name: String): ScanFolder = withContext(Dispatchers.IO) {
        val folder = ScanFolder(name = name)
        val all = getAllFolders().toMutableList()
        all.add(folder)
        foldersFile.writeText(gson.toJson(all))
        folder
    }

    suspend fun deleteFolder(id: String) = withContext(Dispatchers.IO) {
        val all = getAllFolders().toMutableList()
        all.removeAll { it.id == id }
        foldersFile.writeText(gson.toJson(all))
        // Un-file any documents that were inside it, rather than deleting their scans.
        getAllDocuments().filter { it.folderId == id }.forEach { moveToFolder(it.id, null) }
    }

    suspend fun renameFolder(id: String, newName: String) = withContext(Dispatchers.IO) {
        val all = getAllFolders().toMutableList()
        all.indexOfFirst { it.id == id }.takeIf { it >= 0 }?.let { idx ->
            all[idx] = all[idx].copy(name = newName)
            foldersFile.writeText(gson.toJson(all))
        }
    }

    fun availableStorageBytes(): Long = rootDir.usableSpace
}
