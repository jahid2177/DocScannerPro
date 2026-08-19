package com.scanner.pro.di

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.scanner.pro.repository.ScannerRepository
import com.scanner.pro.utils.SettingsManager
import com.scanner.pro.viewmodel.ScannerViewModel

/**
 * Manual DI: this app is small enough that Hilt/Dagger would add build
 * complexity (another annotation processor -> another kapt risk on
 * AndroidIDE) without real benefit. A single factory covers every ViewModel.
 */
class ViewModelFactory(private val appContext: Context) : ViewModelProvider.Factory {

    private val repository by lazy { ScannerRepository(appContext) }
    private val settingsManager by lazy { SettingsManager(appContext) }

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when (modelClass) {
        ScannerViewModel::class.java -> ScannerViewModel(repository, settingsManager) as T
        else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }

    companion object {
        @Volatile private var instance: ViewModelFactory? = null
        fun getInstance(context: Context): ViewModelFactory =
            instance ?: synchronized(this) {
                instance ?: ViewModelFactory(context.applicationContext).also { instance = it }
            }
    }
}
