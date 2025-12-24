package club.ozgur.gifland.platform

import club.ozgur.gifland.domain.model.Dimensions
import club.ozgur.gifland.domain.model.MediaItem
import club.ozgur.gifland.domain.model.OutputFormat
import club.ozgur.gifland.domain.repository.MediaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import java.io.File
import javax.imageio.ImageIO

/**
 * Platform-specific wrapper for MediaRepository on JVM/Desktop
 * Uses composition instead of inheritance since MediaRepository is not open
 */
class PlatformMediaRepository(
    private val baseRepository: MediaRepository = MediaRepository()
) {
    // Delegate basic operations to the base repository
    val mediaItems = baseRepository.mediaItems
    val recentItems = baseRepository.recentItems

    suspend fun addMediaItem(
        filePath: String,
        format: OutputFormat,
        sizeBytes: Long,
        durationMs: Long,
        width: Int,
        height: Int,
        thumbnailPath: String? = null
    ) = baseRepository.addMediaItem(
        filePath, format, sizeBytes, durationMs, width, height, thumbnailPath
    )

    fun getMediaItem(id: String) = baseRepository.getMediaItem(id)
    suspend fun updateMediaItem(mediaItem: MediaItem) = baseRepository.updateMediaItem(mediaItem)
    fun getRecentItems(limit: Int = 50) = baseRepository.getRecentItems(limit)
    fun searchMediaItems(
        query: String? = null,
        format: OutputFormat? = null,
        startDate: kotlinx.datetime.Instant? = null,
        endDate: kotlinx.datetime.Instant? = null,
        minSize: Long? = null,
        maxSize: Long? = null
    ) = baseRepository.searchMediaItems(query, format, startDate, endDate, minSize, maxSize)
    suspend fun clearAll() = baseRepository.clearAll()
    fun getTotalStorageUsed() = baseRepository.getTotalStorageUsed()
    fun getStatistics() = baseRepository.getStatistics()
    suspend fun importFromDirectory(directoryPath: String) = baseRepository.importFromDirectory(directoryPath)

    /**
     * Scan the recordings directory for existing media files
     */
    suspend fun scanRecordingsDirectory() = withContext(Dispatchers.IO) {
        val recordingsDir = PlatformActions.getRecordingsDirectory()
        val files = PlatformActions.listFiles(
            recordingsDir,
            listOf("gif", "webp")
        )

        files.forEach { file ->
            if (!mediaItemExists(file.absolutePath)) {
                try {
                    val format = when (file.extension.lowercase()) {
                        "gif" -> OutputFormat.GIF
                        "webp" -> OutputFormat.WEBP
                        else -> return@forEach
                    }

                    val dimensions = getMediaDimensions(file)
                    val duration = getMediaDuration(file)

                    addMediaItem(
                        filePath = file.absolutePath,
                        format = format,
                        sizeBytes = file.length(),
                        durationMs = duration,
                        width = dimensions.width,
                        height = dimensions.height
                    )
                } catch (e: Exception) {
                    println("Failed to import file ${file.name}: ${e.message}")
                }
            }
        }
    }

    /**
     * Check if a media item with the given file path already exists
     */
    private fun mediaItemExists(filePath: String): Boolean {
        return mediaItems.value.any { it.filePath == filePath }
    }

    /**
     * Delete the actual file for a media item
     */
    suspend fun deleteMediaItem(id: String): Boolean {
        val item = getMediaItem(id) ?: return false

        // Delete the actual files
        val fileDeleted = PlatformActions.deleteFile(item.filePath)
        item.thumbnailPath?.let { PlatformActions.deleteFile(it) }

        // Remove from repository if file was deleted
        if (fileDeleted) {
            return baseRepository.deleteMediaItem(id)
        }

        return false
    }

    /**
     * Export media items to a specified location
     */
    suspend fun exportTo(items: List<MediaItem>, destinationPath: String) {
        withContext(Dispatchers.IO) {
            items.forEach { item ->
                val sourceFile = File(item.filePath)
                if (sourceFile.exists()) {
                    val destFile = File(destinationPath, sourceFile.name)
                    PlatformActions.copyFile(item.filePath, destFile.absolutePath)
                }
            }
            // Also delegate to base repository
            baseRepository.exportTo(items, destinationPath)
        }
    }

    /**
     * Import media from a selected file
     */
    suspend fun importMediaFile(filePath: String): MediaItem? {
        return withContext(Dispatchers.IO) {
            val file = File(filePath)
            if (!file.exists()) return@withContext null

            val format = when (file.extension.lowercase()) {
                "gif" -> OutputFormat.GIF
                "webp" -> OutputFormat.WEBP
                else -> return@withContext null
            }

            try {
                val dimensions = getMediaDimensions(file)
                val duration = getMediaDuration(file)

                // Copy to recordings directory
                val recordingsDir = PlatformActions.getRecordingsDirectory()
                val destFile = File(recordingsDir, file.name)
                PlatformActions.copyFile(filePath, destFile.absolutePath)

                // Add to repository
                addMediaItem(
                    filePath = destFile.absolutePath,
                    format = format,
                    sizeBytes = file.length(),
                    durationMs = duration,
                    width = dimensions.width,
                    height = dimensions.height
                )
            } catch (e: Exception) {
                println("Failed to import media: ${e.message}")
                null
            }
        }
    }

    /**
     * Get dimensions of a media file
     */
    private fun getMediaDimensions(file: File): Dimensions {
        return try {
            when (file.extension.lowercase()) {
                "gif", "webp" -> {
                    // For image formats, use ImageIO
                    val image = ImageIO.read(file)
                    Dimensions(image.width, image.height)
                }
                else -> Dimensions(0, 0)
            }
        } catch (e: Exception) {
            Dimensions(0, 0)
        }
    }

    /**
     * Get duration of a media file in milliseconds
     */
    private fun getMediaDuration(file: File): Long {
        return when (file.extension.lowercase()) {
            "gif", "webp" -> {
                // For animated images, duration calculation would need specific libraries
                // Return an estimate based on file size
                file.length() / 1000 // Rough estimate
            }
            else -> 0L
        }
    }

    /**
     * Generate a thumbnail for a media item
     */
    suspend fun generateThumbnail(mediaItem: MediaItem): String? {
        return withContext(Dispatchers.IO) {
            try {
                val sourceFile = File(mediaItem.filePath)
                if (!sourceFile.exists()) return@withContext null

                when (mediaItem.format) {
                    OutputFormat.GIF, OutputFormat.WEBP -> {
                        // For image formats, create a small version
                        val image = ImageIO.read(sourceFile)

                        // Calculate thumbnail size (max 200x200)
                        val maxSize = 200
                        val scale = minOf(
                            maxSize.toDouble() / image.width,
                            maxSize.toDouble() / image.height
                        )

                        val thumbWidth = (image.width * scale).toInt()
                        val thumbHeight = (image.height * scale).toInt()

                        // Create thumbnail
                        val thumbnail = image.getScaledInstance(thumbWidth, thumbHeight, java.awt.Image.SCALE_SMOOTH)
                        val bufferedThumbnail = java.awt.image.BufferedImage(
                            thumbWidth,
                            thumbHeight,
                            java.awt.image.BufferedImage.TYPE_INT_ARGB
                        )

                        val g2d = bufferedThumbnail.createGraphics()
                        g2d.drawImage(thumbnail, 0, 0, null)
                        g2d.dispose()

                        // Save thumbnail
                        val thumbFile = File(
                            sourceFile.parentFile,
                            "${sourceFile.nameWithoutExtension}_thumb.png"
                        )
                        ImageIO.write(bufferedThumbnail, "png", thumbFile)

                        thumbFile.absolutePath
                    }
                }
            } catch (e: Exception) {
                println("Failed to generate thumbnail: ${e.message}")
                null
            }
        }
    }

    /**
     * Watch a directory for changes
     */
    fun watchDirectory(path: String, onChange: () -> Unit) {
        // This would use WatchService API for file system monitoring
        // For now, we'll rely on manual scanning
    }

    companion object {
        /**
         * Create and initialize a platform media repository
         */
        suspend fun create(): PlatformMediaRepository {
            val repository = PlatformMediaRepository()
            repository.scanRecordingsDirectory()
            return repository
        }
    }
}