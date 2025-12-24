package club.ozgur.gifland.platform

import club.ozgur.gifland.domain.model.CaptureRegion
import java.awt.Desktop
import java.awt.Dimension
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

/** JVM actual implementation using AWT/Swing. */
actual object PlatformUi {
    actual fun getPrimaryScreenBounds(): CaptureRegion {
        val size: Dimension = Toolkit.getDefaultToolkit().screenSize
        return CaptureRegion(0, 0, size.width, size.height)
    }

    actual fun pickFile(title: String?, extensions: List<String>): String? {
        val chooser = JFileChooser()
        if (!title.isNullOrBlank()) chooser.dialogTitle = title
        if (extensions.isNotEmpty()) {
            val filter = FileNameExtensionFilter(
                "Supported files",
                *extensions.map { it.trim().removePrefix(".") }.toTypedArray()
            )
            chooser.fileFilter = filter
        }
        val result = chooser.showOpenDialog(null)
        return if (result == JFileChooser.APPROVE_OPTION) chooser.selectedFile?.absolutePath else null
    }

    actual fun copyToClipboard(text: String) {
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        clipboard.setContents(StringSelection(text), null)
    }

    actual fun revealInFileManager(path: String) {
        try {
            // macOS: use 'open -R' to reveal in Finder
            if (System.getProperty("os.name").lowercase().contains("mac")) {
                ProcessBuilder("open", "-R", path).start()
                return
            }
        } catch (_: Exception) {
            // Fallback below
        }
        val file = File(path)
        val target = if (file.isDirectory) file else file.parentFile
        runCatching { Desktop.getDesktop().open(target) }
    }
}

