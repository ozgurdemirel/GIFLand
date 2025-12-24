package club.ozgur.gifland.platform

import club.ozgur.gifland.domain.model.CaptureRegion

/**
 * Platform UI services exposed to common code via expect/actual.
 * JVM actual is provided under jvmMain.
 */
expect object PlatformUi {
    /** Primary screen bounds in absolute screen coordinates. */
    fun getPrimaryScreenBounds(): CaptureRegion

    /**
     * Open a file picker dialog.
     * @param title optional dialog title
     * @param extensions list of allowed file extensions (without dot). Empty means all files
     * @return absolute path to the selected file, or null if cancelled
     */
    fun pickFile(title: String? = null, extensions: List<String> = emptyList()): String?

    /** Copy given text to the system clipboard. */
    fun copyToClipboard(text: String)

    /** Reveal the given file in the platform's file manager (e.g., Finder). */
    fun revealInFileManager(path: String)
}

