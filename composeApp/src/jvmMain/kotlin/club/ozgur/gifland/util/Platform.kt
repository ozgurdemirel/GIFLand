package club.ozgur.gifland.util

import java.awt.Desktop
import java.io.File
import java.net.URI

class JVMPlatform {
    val name: String = "Java ${System.getProperty("java.version")}"
}

fun getPlatform() = JVMPlatform()

fun openFileLocation(filePath: String) {
    runCatching {
        val file = File(filePath)
        if (!file.exists()) {
            println("File does not exist: $filePath")
            return
        }

        val os = System.getProperty("os.name").lowercase()
        when {
            os.contains("mac") -> {
                // macOS için open komutu kullan - daha güvenilir
                ProcessBuilder("open", "-R", file.absolutePath).start()
            }
            os.contains("windows") -> {
                // Windows için explorer komutu
                ProcessBuilder("explorer.exe", "/select,", file.absolutePath).start()
            }
            os.contains("linux") -> {
                // Linux için xdg-open kullan
                val parent = file.parentFile
                ProcessBuilder("xdg-open", parent.absolutePath).start()
            }
            else -> {
                // Fallback to Desktop API
                val desktop = Desktop.getDesktop()
                if (desktop.isSupported(Desktop.Action.OPEN)) {
                    desktop.open(file.parentFile)
                }
            }
        }
    }.onFailure {
        println("Could not open file location: ${it.message}")
    }
}

fun openUrl(url: String) {
    runCatching {
        Desktop.getDesktop().browse(URI(url))
    }.onFailure {
        println("Could not open url: ${it.message}")
    }
}