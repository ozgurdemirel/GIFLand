package club.ozgur.gifland.di

import club.ozgur.gifland.domain.repository.MediaRepository
import club.ozgur.gifland.domain.repository.WindowStateRepository
import club.ozgur.gifland.domain.service.RecordingService
import club.ozgur.gifland.platform.PlatformMediaRepository
import club.ozgur.gifland.core.Recorder
import com.russhwolf.settings.PreferencesSettings
import com.russhwolf.settings.Settings
import kotlinx.coroutines.runBlocking
import org.koin.dsl.module
import java.util.prefs.Preferences

/**
 * Platform-specific dependency injection module for JVM/Desktop.
 * Provides implementations that are specific to desktop platforms.
 */
val platformModule = module {

    // Settings implementation using Java Preferences
    single<Settings> {
        PreferencesSettings(
            Preferences.userNodeForPackage(Settings::class.java)
        )
    }

    // Shared Recorder singleton so UI and service observe the same instance
    single { Recorder() }

    // Recording service - uses shared Recorder and repositories
    // Now includes WindowStateRepository for multi-monitor aware full-screen recording
    single { RecordingService(get(), get(), get(), get<WindowStateRepository>()) }
    // Bind the same singleton instance to the RecordingController interface
    single<club.ozgur.gifland.domain.service.RecordingController> { get<RecordingService>() }


    // Provide a single PlatformMediaRepository that wraps the shared base MediaRepository
    single { PlatformMediaRepository(get()) }
}