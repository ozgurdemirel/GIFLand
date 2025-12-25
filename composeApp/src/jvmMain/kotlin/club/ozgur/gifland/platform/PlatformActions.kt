package club.ozgur.gifland.platform

import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

/**
 * Platform-specific implementations for desktop actions
 */
object PlatformActions {

    /**
     * Open file location in system file explorer
     */
    fun openFileLocation(filePath: String) {
        try {
            val file = File(filePath)
            if (file.exists()) {
                val desktop = Desktop.getDesktop()
                if (Desktop.isDesktopSupported()) {
                    // Try to open parent directory and select the file
                    val parent = file.parentFile
                    if (parent != null && parent.exists()) {
                        if (System.getProperty("os.name").lowercase().contains("windows")) {
                            // Windows: Use explorer with /select flag
                            Runtime.getRuntime().exec(arrayOf("explorer.exe", "/select,", file.absolutePath))
                        } else if (System.getProperty("os.name").lowercase().contains("mac")) {
                            // macOS: Use open with -R flag to reveal in Finder
                            Runtime.getRuntime().exec(arrayOf("open", "-R", file.absolutePath))
                        } else {
                            // Linux/Other: Just open the parent directory
                            desktop.open(parent)
                        }
                    } else {
                        // Fallback: just open the file
                        desktop.open(file)
                    }
                }
            } else {
                println("File does not exist: $filePath")
            }
        } catch (e: Exception) {
            println("Failed to open file location: ${e.message}")
        }
    }

    /**
     * Share media file using system share functionality
     */
    fun shareMedia(filePath: String) {
        try {
            val file = File(filePath)
            if (file.exists()) {
                if (Desktop.isDesktopSupported()) {
                    val desktop = Desktop.getDesktop()

                    // Try to open with default application (usually opens share options)
                    if (desktop.isSupported(Desktop.Action.OPEN)) {
                        desktop.open(file)
                    } else {
                        println("Share action not supported on this platform")
                    }
                } else {
                    println("Desktop not supported")
                }
            } else {
                println("File does not exist: $filePath")
            }
        } catch (e: Exception) {
            println("Failed to share media: ${e.message}")
        }
    }

    /**
     * Copy file to clipboard (can be pasted into Finder, Word, etc.)
     */
    fun copyToClipboard(filePath: String) {
        try {
            val file = File(filePath)
            if (file.exists()) {
                if (isMac()) {
                    // macOS: Use AppleScript for proper file clipboard support
                    val process = Runtime.getRuntime().exec(arrayOf(
                        "osascript", "-e",
                        "set the clipboard to POSIX file \"${file.absolutePath}\""
                    ))
                    process.waitFor()
                    println("Copied file to clipboard (macOS): $filePath")
                } else {
                    // Windows/Linux: Use Java clipboard with file list flavor
                    val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                    val transferable = object : Transferable {
                        override fun getTransferDataFlavors(): Array<DataFlavor> {
                            return arrayOf(DataFlavor.javaFileListFlavor, DataFlavor.stringFlavor)
                        }

                        override fun isDataFlavorSupported(flavor: DataFlavor): Boolean {
                            return flavor == DataFlavor.javaFileListFlavor || flavor == DataFlavor.stringFlavor
                        }

                        override fun getTransferData(flavor: DataFlavor): Any {
                            return when (flavor) {
                                DataFlavor.javaFileListFlavor -> listOf(file)
                                DataFlavor.stringFlavor -> file.absolutePath
                                else -> throw UnsupportedFlavorException(flavor)
                            }
                        }
                    }
                    clipboard.setContents(transferable, null)
                    println("Copied file to clipboard: $filePath")
                }
            } else {
                println("File does not exist: $filePath")
            }
        } catch (e: Exception) {
            println("Failed to copy to clipboard: ${e.message}")
        }
    }

    /**
     * Delete a file from the filesystem
     */
    fun deleteFile(filePath: String): Boolean {
        return try {
            val file = File(filePath)
            if (file.exists()) {
                // Try to move to trash first (platform-specific)
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.MOVE_TO_TRASH)) {
                    Desktop.getDesktop().moveToTrash(file)
                    true
                } else {
                    // Fallback to direct deletion
                    file.delete()
                }
            } else {
                false
            }
        } catch (e: Exception) {
            println("Failed to delete file: ${e.message}")
            false
        }
    }

    /**
     * Pick a file using system file picker
     */
    fun pickFile(
        title: String = "Select File",
        filters: Map<String, List<String>> = emptyMap()
    ): String? {
        val chooser = JFileChooser().apply {
            dialogTitle = title
            fileSelectionMode = JFileChooser.FILES_ONLY
            isAcceptAllFileFilterUsed = true

            // Add file filters
            filters.forEach { (description, extensions) ->
                val filter = FileNameExtensionFilter(description, *extensions.toTypedArray())
                addChoosableFileFilter(filter)
            }
        }

        return when (chooser.showOpenDialog(null)) {
            JFileChooser.APPROVE_OPTION -> chooser.selectedFile?.absolutePath
            else -> null
        }
    }

    /**
     * Pick a directory using system directory picker
     */
    fun pickDirectory(title: String = "Select Directory"): String? {
        val chooser = JFileChooser().apply {
            dialogTitle = title
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            isAcceptAllFileFilterUsed = false
        }

        return when (chooser.showOpenDialog(null)) {
            JFileChooser.APPROVE_OPTION -> chooser.selectedFile?.absolutePath
            else -> null
        }
    }

    /**
     * Copy a file to a new location
     */
    fun copyFile(sourcePath: String, destinationPath: String): Boolean {
        return try {
            val source = Paths.get(sourcePath)
            val destination = Paths.get(destinationPath)
            Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING)
            true
        } catch (e: IOException) {
            println("Failed to copy file: ${e.message}")
            false
        }
    }

    /**
     * Move a file to a new location
     */
    fun moveFile(sourcePath: String, destinationPath: String): Boolean {
        return try {
            val source = Paths.get(sourcePath)
            val destination = Paths.get(destinationPath)
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING)
            true
        } catch (e: IOException) {
            println("Failed to move file: ${e.message}")
            false
        }
    }

    /**
     * Check if a file exists
     */
    fun fileExists(filePath: String): Boolean {
        return File(filePath).exists()
    }

    /**
     * Get file size in bytes
     */
    fun getFileSize(filePath: String): Long {
        val file = File(filePath)
        return if (file.exists()) file.length() else 0L
    }

    /**
     * List files in a directory with optional filter
     */
    fun listFiles(
        directoryPath: String,
        extensions: List<String> = emptyList()
    ): List<File> {
        val directory = File(directoryPath)
        return if (directory.exists() && directory.isDirectory) {
            directory.listFiles { file ->
                if (extensions.isEmpty()) {
                    true
                } else {
                    extensions.any { ext ->
                        file.name.lowercase().endsWith(".$ext")
                    }
                }
            }?.toList() ?: emptyList()
        } else {
            emptyList()
        }
    }

    /**
     * Create a directory if it doesn't exist
     */
    fun createDirectory(path: String): Boolean {
        return try {
            val dir = File(path)
            if (!dir.exists()) {
                dir.mkdirs()
            } else {
                true
            }
        } catch (e: Exception) {
            println("Failed to create directory: ${e.message}")
            false
        }
    }

    /**
     * Get the user's documents directory
     */
    fun getDocumentsDirectory(): String {
        return System.getProperty("user.home") + File.separator + "Documents"
    }

    /**
     * Get the user's downloads directory
     */
    fun getDownloadsDirectory(): String {
        return System.getProperty("user.home") + File.separator + "Downloads"
    }

    /**
     * Get the default recordings directory
     */
    fun getRecordingsDirectory(): String {
        val recordingsPath = getDocumentsDirectory() + File.separator + "Recordings"
        createDirectory(recordingsPath)
        return recordingsPath
    }

    /**
     * Open macOS Screen Recording privacy settings
     */
    fun openScreenRecordingSettings() {
        if (System.getProperty("os.name").lowercase().contains("mac")) {
            try {
                Runtime.getRuntime().exec(arrayOf(
                    "open",
                    "x-apple.systempreferences:com.apple.preference.security?Privacy_ScreenCapture"
                ))
            } catch (e: Exception) {
                println("Failed to open Screen Recording settings: ${e.message}")
            }
        }
    }

    /**
     * Check if running on macOS
     */
    fun isMac(): Boolean {
        return System.getProperty("os.name").lowercase().contains("mac")
    }
}