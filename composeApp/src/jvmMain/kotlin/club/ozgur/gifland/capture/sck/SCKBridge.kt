package club.ozgur.gifland.capture.sck

import com.sun.jna.*

interface SCKBridge : Library {
    companion object {
        fun loadOrNull(): SCKBridge? {
            if (!NativeLoader.loadIfMac()) return null
            val abs = NativeLoader.lastLoadedPath
            return runCatching {
                if (abs != null) {
                    Native.load(abs, SCKBridge::class.java) as SCKBridge
                } else {
                    Native.load("sck_bridge_swift", SCKBridge::class.java) as SCKBridge
                }
            }.getOrNull()
        }
    }

    interface FrameCb : Callback {
        fun invoke(
            frameIndex: Int,
            user: Pointer?
        )
    }

    fun sck_start_display_capture(
        displayId: Int,
        fps: Int,
        x: Int,
        y: Int,
        w: Int,
        h: Int,
        outputDir: String,
        jpegQuality: Int,
        scale: Float,
        cb: FrameCb,
        user: Pointer?
    ): Int
    fun sck_stop_capture()
    fun sck_list_displays_json(): Pointer?

    // Permission management
    /** Check if screen recording permission is granted. Returns 0 = granted, 1 = not granted */
    fun sck_check_permission(): Int
    /** Request screen recording permission. Returns 0 = granted, 1 = denied */
    fun sck_request_permission(): Int
    /** Trigger permission registration by attempting SCShareableContent access.
     *  This registers the app in Privacy settings. Returns 0 = success */
    fun sck_trigger_permission_registration(): Int
}

