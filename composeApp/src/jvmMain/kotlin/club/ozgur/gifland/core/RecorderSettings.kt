package club.ozgur.gifland.core

data class RecorderSettings(
    val fps: Int = 23,
    val quality: Int = 15,
    val format: OutputFormat = OutputFormat.GIF,
    val maxDuration: Int = 30,
    val scale: Float = 1.0f,  // Keep it but hidden from UI
    val fastGifPreview: Boolean = false // Fast/preview GIF mode toggle
)