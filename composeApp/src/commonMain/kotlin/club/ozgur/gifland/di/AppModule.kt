package club.ozgur.gifland.di

import club.ozgur.gifland.domain.repository.StateRepository
import club.ozgur.gifland.domain.repository.SettingsRepository
import club.ozgur.gifland.domain.repository.MediaRepository
import club.ozgur.gifland.domain.repository.WindowStateRepository
import club.ozgur.gifland.presentation.viewmodel.*
import org.koin.dsl.module

/**
 * Main dependency injection module for the application.
 * Contains all common dependencies that are shared across platforms.
 */
val appModule = module {

    // ===== Repositories =====

    // State repository - single source of truth
    single { StateRepository() }

    // Settings repository - manages app configuration
    single { SettingsRepository(get()) }

    // Media repository - manages recorded media items
    single { MediaRepository() }

    // Window state repository - manages window visibility and position for multi-monitor support
    single { WindowStateRepository() }

    // ===== ViewModels =====

    // Main view model for the primary interface
    single { MainViewModel(get(), get(), get()) }

    // Recording view model for capture operations
    factory { RecordingViewModel(get()) }

    // Editor view model for media editing
    factory { EditorViewModel(get(), get()) }

    // Settings view model for configuration
    factory { SettingsViewModel(get(), get()) }

    // Quick panel view model for the lightweight interface
    single { QuickPanelViewModel(get(), get(), get()) }

    // ===== Services =====

    // Recording service - handles capture logic (provided by platform modules)

    // Processing service - handles encoding and optimization (placeholder for now)
    single { ProcessingService(get()) }

    // Thumbnail service - generates media previews (placeholder for now)
    single { ThumbnailService() }

    // Export service - handles various export formats (placeholder for now)
    single { ExportService(get()) }

    // Hotkey service - manages global shortcuts (placeholder for now)
    single { HotkeyService(get()) }

    // ===== Settings =====

    // Multiplatform settings - will be provided by platform modules
}

// Placeholder service classes - will be implemented as needed

class ProcessingService(
    private val stateRepository: StateRepository
)

class ThumbnailService

class ExportService(
    private val settingsRepository: SettingsRepository
)

class HotkeyService(
    private val settingsRepository: SettingsRepository
)