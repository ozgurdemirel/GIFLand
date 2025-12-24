package club.ozgur.gifland.capture.sck

import club.ozgur.gifland.util.Log
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermissions

object NativeLoader {
    private var loaded = false
    var lastLoadedPath: String? = null
        private set


    fun isMac(): Boolean = System.getProperty("os.name").lowercase().contains("mac")

    fun loadIfMac(): Boolean {
        if (!isMac()) return false
        if (loaded) return true

        return runCatching {
            val arch = System.getProperty("os.arch").lowercase()
            val sub = when {
                arch.contains("aarch64") || arch.contains("arm64") -> "arm64"
                arch.contains("x86_64") || arch.contains("amd64") -> "x64"
                else -> {
                    Log.e("NativeLoader", "Unsupported macOS architecture: $arch")
                    return false
                }
            }
            val res = "/natives/darwin/$sub/libsck_bridge_swift.dylib"
            val ins = NativeLoader::class.java.getResourceAsStream(res)
            if (ins == null) {
                Log.e("NativeLoader", "Missing Swift native library: $res")
                return false
            }

            val tmp = Files.createTempFile("sck_bridge_", ".dylib")
            Files.copy(ins, tmp, StandardCopyOption.REPLACE_EXISTING)
            try {
                Files.setPosixFilePermissions(tmp, PosixFilePermissions.fromString("rwxr-xr-x"))
            } catch (_: Throwable) {}

            val abs = tmp.toAbsolutePath().toString()
            System.load(abs)
            lastLoadedPath = abs
            tmp.toFile().deleteOnExit()
            loaded = true
            Log.d("NativeLoader", "Successfully loaded ScreenCaptureKit bridge for $arch")
            true
        }.getOrElse { e ->
            Log.e("NativeLoader", "Failed to load ScreenCaptureKit bridge", e)
            false
        }
    }
}

