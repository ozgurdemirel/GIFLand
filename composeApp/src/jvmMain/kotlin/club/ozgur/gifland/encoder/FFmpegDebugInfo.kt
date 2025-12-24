package club.ozgur.gifland.encoder

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

data class FFmpegDebugInfo(
    val ffmpegVersion: String? = null,
    val architecture: String? = null,
    val lastError: String? = null
)


object FFmpegDebugManager {
    var debugInfo by mutableStateOf(FFmpegDebugInfo())
        private set


    fun updateFFmpegVersion(version: String) {
        debugInfo = debugInfo.copy(ffmpegVersion = version)
    }

    fun updateArchitecture(arch: String) {
        debugInfo = debugInfo.copy(architecture = arch)
    }

    fun setError(error: String) {
        debugInfo = debugInfo.copy(lastError = error)
    }

    fun clear() {
        debugInfo = FFmpegDebugInfo()
    }
}