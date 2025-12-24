package club.ozgur.gifland.presentation.viewmodel

import club.ozgur.gifland.domain.model.MediaItem
import club.ozgur.gifland.platform.PlatformActions

/**
 * Platform-specific extensions for QuickPanelViewModel on JVM/Desktop
 */

// Extension functions to provide actual implementations

fun QuickPanelViewModel.shareMediaPlatformImpl(mediaItem: MediaItem) {
    PlatformActions.shareMedia(mediaItem.filePath)
}

fun QuickPanelViewModel.copyToClipboardPlatformImpl(mediaItem: MediaItem) {
    PlatformActions.copyToClipboard(mediaItem.filePath)
}

fun QuickPanelViewModel.openFileLocationPlatformImpl(path: String) {
    PlatformActions.openFileLocation(path)
}

fun QuickPanelViewModel.pickFilePlatformImpl(): String? {
    return PlatformActions.pickFile(
        title = "Import Media",
        filters = mapOf(
            "Media Files" to listOf("gif", "webp"),
            "GIF Files" to listOf("gif"),
            "WebP Files" to listOf("webp")
        )
    )
}