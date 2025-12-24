package club.ozgur.gifland.domain.repository

import club.ozgur.gifland.domain.model.MediaItem
import club.ozgur.gifland.domain.model.Dimensions
import club.ozgur.gifland.domain.model.OutputFormat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * Repository for managing recorded media items.
 * Handles storage, retrieval, and organization of recordings.
 */
class MediaRepository {

    private val _mediaItems = MutableStateFlow<List<MediaItem>>(emptyList())
    val mediaItems: StateFlow<List<MediaItem>> = _mediaItems.asStateFlow()

    private val _recentItems = MutableStateFlow<List<MediaItem>>(emptyList())
    val recentItems: StateFlow<List<MediaItem>> = _recentItems.asStateFlow()

    /**
     * Add a new media item to the repository
     */
    suspend fun addMediaItem(
        filePath: String,
        format: OutputFormat,
        sizeBytes: Long,
        durationMs: Long,
        width: Int,
        height: Int,
        thumbnailPath: String? = null
    ): MediaItem {
        val mediaItem = MediaItem(
            id = generateMediaId(),
            filePath = filePath,
            thumbnailPath = thumbnailPath,
            format = format,
            sizeBytes = sizeBytes,
            durationMs = durationMs,
            dimensions = Dimensions(width, height),
            createdAt = Clock.System.now(),
            metadata = buildMetadata(format, sizeBytes, durationMs)
        )

        _mediaItems.value = _mediaItems.value + mediaItem
        updateRecentItems()

        return mediaItem
    }

    /**
     * Get a media item by ID
     */
    fun getMediaItem(id: String): MediaItem? {
        return _mediaItems.value.find { it.id == id }
    }

    /**
     * Update an existing media item
     */
    suspend fun updateMediaItem(mediaItem: MediaItem) {
        _mediaItems.value = _mediaItems.value.map {
            if (it.id == mediaItem.id) mediaItem else it
        }
        updateRecentItems()
    }

    /**
     * Delete a media item
     */
    suspend fun deleteMediaItem(id: String): Boolean {
        val item = getMediaItem(id) ?: return false

        // Delete the actual file
        deleteFile(item.filePath)

        // Delete thumbnail if exists
        item.thumbnailPath?.let { deleteFile(it) }

        // Remove from repository
        _mediaItems.value = _mediaItems.value.filter { it.id != id }
        updateRecentItems()

        return true
    }

    /**
     * Get recent media items
     */
    fun getRecentItems(limit: Int = 50): List<MediaItem> {
        return _mediaItems.value
            .sortedByDescending { it.createdAt }
            .take(limit)
    }

    /**
     * Search media items by various criteria
     */
    fun searchMediaItems(
        query: String? = null,
        format: OutputFormat? = null,
        startDate: Instant? = null,
        endDate: Instant? = null,
        minSize: Long? = null,
        maxSize: Long? = null
    ): List<MediaItem> {
        return _mediaItems.value.filter { item ->
            val matchesQuery = query?.let { q ->
                item.filePath.contains(q, ignoreCase = true) ||
                item.metadata.values.any { it.contains(q, ignoreCase = true) }
            } ?: true

            val matchesFormat = format?.let { f ->
                item.format == f
            } ?: true

            val matchesDateRange = when {
                startDate != null && endDate != null -> {
                    item.createdAt >= startDate && item.createdAt <= endDate
                }
                startDate != null -> item.createdAt >= startDate
                endDate != null -> item.createdAt <= endDate
                else -> true
            }

            val matchesSizeRange = when {
                minSize != null && maxSize != null -> {
                    item.sizeBytes >= minSize && item.sizeBytes <= maxSize
                }
                minSize != null -> item.sizeBytes >= minSize
                maxSize != null -> item.sizeBytes <= maxSize
                else -> true
            }

            matchesQuery && matchesFormat && matchesDateRange && matchesSizeRange
        }
    }

    /**
     * Clear all media items
     */
    suspend fun clearAll() {
        // Delete all files
        _mediaItems.value.forEach { item ->
            deleteFile(item.filePath)
            item.thumbnailPath?.let { deleteFile(it) }
        }

        _mediaItems.value = emptyList()
        _recentItems.value = emptyList()
    }

    /**
     * Get total storage used by all media items
     */
    fun getTotalStorageUsed(): Long {
        return _mediaItems.value.sumOf { it.sizeBytes }
    }

    /**
     * Get media statistics
     */
    fun getStatistics(): MediaStatistics {
        val items = _mediaItems.value
        return MediaStatistics(
            totalCount = items.size,
            totalSizeBytes = items.sumOf { it.sizeBytes },
            formatDistribution = items.groupBy { it.format }.mapValues { it.value.size },
            averageDurationMs = if (items.isNotEmpty()) {
                items.sumOf { it.durationMs } / items.size
            } else 0,
            oldestItem = items.minByOrNull { it.createdAt },
            newestItem = items.maxByOrNull { it.createdAt }
        )
    }

    /**
     * Import media items from a directory scan
     */
    suspend fun importFromDirectory(directoryPath: String) {
        // This would scan a directory and import existing recordings
        // Implementation depends on platform-specific file APIs
    }

    /**
     * Export media items to a specified location
     */
    suspend fun exportTo(items: List<MediaItem>, destinationPath: String) {
        // This would copy selected items to a new location
        // Implementation depends on platform-specific file APIs
    }

    // Private helper functions

    private fun updateRecentItems() {
        _recentItems.value = getRecentItems()
    }

    private fun generateMediaId(): String {
        return "media_${Clock.System.now().toEpochMilliseconds()}_${(0..9999).random()}"
    }

    private fun buildMetadata(
        format: OutputFormat,
        sizeBytes: Long,
        durationMs: Long
    ): Map<String, String> {
        return mapOf(
            "format" to format.name,
            "size" to formatFileSize(sizeBytes),
            "duration" to formatDuration(durationMs),
            "created" to Clock.System.now().toString()
        )
    }

    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
            else -> "${bytes / (1024 * 1024 * 1024)} GB"
        }
    }

    private fun formatDuration(ms: Long): String {
        val seconds = ms / 1000
        val minutes = seconds / 60
        val hours = minutes / 60

        return when {
            hours > 0 -> "${hours}h ${minutes % 60}m"
            minutes > 0 -> "${minutes}m ${seconds % 60}s"
            else -> "${seconds}s"
        }
    }

    private fun deleteFile(path: String) {
        // Platform-specific file deletion
        // Will be implemented with expect/actual
    }
}

/**
 * Media statistics data class
 */
data class MediaStatistics(
    val totalCount: Int,
    val totalSizeBytes: Long,
    val formatDistribution: Map<OutputFormat, Int>,
    val averageDurationMs: Long,
    val oldestItem: MediaItem?,
    val newestItem: MediaItem?
)