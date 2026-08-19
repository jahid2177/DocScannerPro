package com.scanner.pro.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scanner.pro.model.*
import com.scanner.pro.pdf.PdfOptions
import com.scanner.pro.repository.ScanFolder
import com.scanner.pro.repository.ScannerRepository
import com.scanner.pro.repository.SortOrder
import com.scanner.pro.utils.SettingsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class ScannerViewModel(
    private val repository: ScannerRepository,
    val settings: SettingsManager
) : ViewModel() {

    // ---- Home / file manager state ----
    private val _documents = MutableStateFlow<List<ScanDocument>>(emptyList())
    val documents: StateFlow<List<ScanDocument>> = _documents.asStateFlow()

    private val _folders = MutableStateFlow<List<ScanFolder>>(emptyList())
    val folders: StateFlow<List<ScanFolder>> = _folders.asStateFlow()

    // ---- Active scan session state ----
    private val _activeDocument = MutableStateFlow<ScanDocument?>(null)
    val activeDocument: StateFlow<ScanDocument?> = _activeDocument.asStateFlow()

    private val _uiState = MutableStateFlow<Resource<Unit>>(Resource.Success(Unit))
    val uiState: StateFlow<Resource<Unit>> = _uiState.asStateFlow()

    init {
        refreshDocuments()
    }

    fun refreshDocuments() {
        viewModelScope.launch {
            _documents.value = repository.fileManager.getAllDocuments()
            _folders.value = repository.fileManager.getAllFolders()
        }
    }

    fun startNewDocument(name: String = defaultDocumentName()) {
        viewModelScope.launch {
            _activeDocument.value = repository.createDocument(name)
        }
    }

    fun resumeDocument(documentId: String) {
        // If the active document already IS this one (e.g. we just navigated
        // here straight from Scanner with pages freshly added in memory),
        // don't clobber it with a re-fetch from disk.
        if (_activeDocument.value?.id == documentId) return
        viewModelScope.launch {
            _activeDocument.value = repository.fileManager.getDocument(documentId)
        }
    }

    fun clearActiveDocument() {
        _activeDocument.value = null
    }

    private fun defaultDocumentName(prefix: String = "Scan"): String {
        val fmt = java.text.SimpleDateFormat("MMM d, yyyy - h:mm a", java.util.Locale.getDefault())
        return "$prefix ${fmt.format(java.util.Date())}"
    }

    // ---------------------------------------------------------------------
    // Capture / page editing
    // ---------------------------------------------------------------------

    fun addPage(capturedFile: File, corners: DocumentCorners) {
        val doc = _activeDocument.value ?: return
        _uiState.value = Resource.Loading
        viewModelScope.launch {
            val result = repository.addPageFromCapture(doc, capturedFile, corners, settings.defaultFilter)
            _uiState.value = result.fold(
                onSuccess = { _activeDocument.value = doc; Resource.Success(Unit) },
                onFailure = { Resource.Error(mapError(it)) }
            )
        }
    }

    /**
     * Imported PDF: pageFiles are already-rendered page images (one file per
     * PDF page, in order). Added sequentially -- not via separate launch{}
     * calls per page -- so they can't race each other mutating the same
     * document's page list.
     */
    fun importPdfPages(pageFiles: List<File>) {
        val doc = _activeDocument.value ?: return
        if (pageFiles.isEmpty()) return
        _uiState.value = Resource.Loading
        viewModelScope.launch {
            for (file in pageFiles) {
                val bmp = com.scanner.pro.utils.BitmapUtils.decodeSampledBitmap(
                    file.absolutePath, com.scanner.pro.utils.BitmapUtils.SCAN_MAX_DIMENSION
                )
                if (bmp == null) { file.delete(); continue }
                val corners = DocumentCorners.defaultForSize(bmp.width, bmp.height)
                bmp.recycle()
                val result = repository.addPageFromCapture(doc, file, corners, settings.defaultFilter)
                if (result.isFailure) {
                    _uiState.value = Resource.Error(mapError(result.exceptionOrNull() ?: Exception("Import failed")))
                    return@launch
                }
            }
            _activeDocument.value = doc
            _uiState.value = Resource.Success(Unit)
        }
    }

    fun addIdCardPage(frontFile: File, frontCorners: DocumentCorners, backFile: File, backCorners: DocumentCorners) {
        val doc = _activeDocument.value ?: return
        // Only rename on the very first card of this document, so scanning
        // several ID cards into one document doesn't keep resetting the name.
        if (doc.pages.isEmpty()) {
            doc.name = defaultDocumentName("ID Card")
        }
        _uiState.value = Resource.Loading
        viewModelScope.launch {
            val result = repository.addIdCardPage(doc, frontFile, frontCorners, backFile, backCorners, settings.defaultFilter)
            _uiState.value = result.fold(
                onSuccess = { _activeDocument.value = doc; Resource.Success(Unit) },
                onFailure = { Resource.Error(mapError(it)) }
            )
        }
    }

    fun reapplyFilter(page: ScanPage, filter: ScanFilterType) {
        val doc = _activeDocument.value ?: return
        viewModelScope.launch {
            repository.reapplyFilter(doc, page, filter)
            _activeDocument.value = doc
        }
    }

    fun recropPage(page: ScanPage, newCorners: DocumentCorners) {
        val doc = _activeDocument.value ?: return
        viewModelScope.launch {
            repository.recropPage(doc, page, newCorners)
            _activeDocument.value = doc
        }
    }

    fun rotatePage(page: ScanPage, clockwise: Boolean = true) {
        val doc = _activeDocument.value ?: return
        viewModelScope.launch {
            repository.rotatePage(doc, page, clockwise)
            _activeDocument.value = doc
        }
    }

    fun addSignatureToPage(page: ScanPage, signature: android.graphics.Bitmap) {
        val doc = _activeDocument.value ?: return
        viewModelScope.launch {
            repository.applySignature(doc, page, signature)
            _activeDocument.value = doc
        }
    }

    fun deletePage(pageId: String) {
        val doc = _activeDocument.value ?: return
        repository.deletePage(doc, pageId)
        _activeDocument.value = doc
        viewModelScope.launch { repository.fileManager.saveDocument(doc) }
    }

    /** Multi-select delete: removes several pages from the active document in one save. */
    fun deletePages(pageIds: Set<String>) {
        val doc = _activeDocument.value ?: return
        if (pageIds.isEmpty()) return
        pageIds.forEach { repository.deletePage(doc, it) }
        _activeDocument.value = doc
        viewModelScope.launch { repository.fileManager.saveDocument(doc) }
    }

    /** "Delete All" from the export sheet: clears every page of the active document. */
    fun deleteAllPages() {
        val doc = _activeDocument.value ?: return
        doc.pages.clear()
        _activeDocument.value = doc
        viewModelScope.launch { repository.fileManager.saveDocument(doc) }
    }

    fun duplicatePage(page: ScanPage) {
        val doc = _activeDocument.value ?: return
        viewModelScope.launch {
            repository.duplicatePage(doc, page)
            repository.fileManager.saveDocument(doc)
            _activeDocument.value = doc
        }
    }

    fun reorderPages(fromIndex: Int, toIndex: Int) {
        val doc = _activeDocument.value ?: return
        repository.reorderPages(doc, fromIndex, toIndex)
        _activeDocument.value = doc
        viewModelScope.launch { repository.fileManager.saveDocument(doc) }
    }

    // ---------------------------------------------------------------------
    // OCR
    // ---------------------------------------------------------------------

    private val _ocrState = MutableStateFlow<Resource<OcrResult>?>(null)
    val ocrState: StateFlow<Resource<OcrResult>?> = _ocrState.asStateFlow()

    fun runOcr(page: ScanPage, language: OcrLanguage = settings.ocrLanguage) {
        _ocrState.value = Resource.Loading
        viewModelScope.launch {
            val result = repository.runOcr(page, language)
            _ocrState.value = result.fold(
                onSuccess = { Resource.Success(it) },
                onFailure = { Resource.Error(ScanError.OcrFailed(it.message ?: "Unknown error")) }
            )
        }
    }

    // ---------------------------------------------------------------------
    // Export
    // ---------------------------------------------------------------------

    private val _exportState = MutableStateFlow<Resource<File>?>(null)
    val exportState: StateFlow<Resource<File>?> = _exportState.asStateFlow()

    fun exportPdf(outputFile: File, options: PdfOptions = defaultPdfOptions()) {
        val doc = _activeDocument.value ?: return
        _exportState.value = Resource.Loading
        viewModelScope.launch {
            val result = repository.exportPdf(doc, options, outputFile)
            _exportState.value = result.fold(
                onSuccess = { Resource.Success(it) },
                onFailure = { Resource.Error(ScanError.PdfFailed(it.message ?: "Unknown error")) }
            )
        }
    }

    fun exportImage(page: ScanPage, outputFile: File, asPng: Boolean) {
        _exportState.value = Resource.Loading
        viewModelScope.launch {
            val result = repository.exportImage(page, outputFile, asPng)
            _exportState.value = result.fold(
                onSuccess = { Resource.Success(it) },
                onFailure = { Resource.Error(ScanError.Unknown(it.message ?: "Unknown error")) }
            )
        }
    }

    private fun defaultPdfOptions() = PdfOptions(
        quality = settings.imageQuality,
        losslessMode = !settings.pdfCompressionEnabled
    )

    // ---------------------------------------------------------------------
    // File manager operations (home screen)
    // ---------------------------------------------------------------------

    fun deleteDocument(id: String) = viewModelScope.launch {
        repository.fileManager.deleteDocument(id)
        refreshDocuments()
    }

    /** Multi-select delete: removes several documents in one pass, then refreshes the list once. */
    fun deleteDocuments(ids: Set<String>) = viewModelScope.launch {
        ids.forEach { repository.fileManager.deleteDocument(it) }
        refreshDocuments()
    }

    fun duplicateDocument(id: String) = viewModelScope.launch {
        repository.fileManager.duplicateDocument(id)
        refreshDocuments()
    }

    fun toggleFavorite(id: String) = viewModelScope.launch {
        repository.fileManager.toggleFavorite(id)
        refreshDocuments()
    }

    fun renameDocument(id: String, newName: String) = viewModelScope.launch {
        repository.fileManager.renameDocument(id, newName)
        refreshDocuments()
    }

    fun moveToFolder(documentId: String, folderId: String?) = viewModelScope.launch {
        repository.fileManager.moveToFolder(documentId, folderId)
        refreshDocuments()
    }

    fun createFolder(name: String) = viewModelScope.launch {
        repository.fileManager.createFolder(name)
        refreshDocuments()
    }

    fun search(query: String) = viewModelScope.launch {
        _documents.value = if (query.isBlank()) repository.fileManager.getAllDocuments()
        else repository.fileManager.search(query)
    }

    fun applySort(order: SortOrder) = viewModelScope.launch {
        _documents.value = repository.fileManager.sorted(_documents.value, order)
    }

    private fun mapError(t: Throwable): ScanError = when (t) {
        is com.scanner.pro.repository.StorageFullException -> ScanError.StorageFull
        else -> ScanError.Unknown(t.message ?: "Something went wrong")
    }
}
