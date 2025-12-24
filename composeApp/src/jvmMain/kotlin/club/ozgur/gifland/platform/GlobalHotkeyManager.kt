package club.ozgur.gifland.platform

import club.ozgur.gifland.domain.model.HotkeyAction
import com.github.kwhat.jnativehook.GlobalScreen
import com.github.kwhat.jnativehook.NativeHookException
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.awt.event.KeyEvent
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Manages global hotkeys for the application using JNativeHook.
 * Allows users to trigger actions from anywhere in the system.
 */
class GlobalHotkeyManager {
    private val scope = CoroutineScope(Dispatchers.Main)
    private val registeredHotkeys = ConcurrentHashMap<String, () -> Unit>()
    private var isListening = false

    private val keyListener = object : NativeKeyListener {
        private val pressedKeys = mutableSetOf<Int>()

        override fun nativeKeyPressed(event: NativeKeyEvent) {
            pressedKeys.add(event.keyCode)
            checkHotkey()
        }

        override fun nativeKeyReleased(event: NativeKeyEvent) {
            pressedKeys.remove(event.keyCode)
        }

        override fun nativeKeyTyped(event: NativeKeyEvent) {
            // Not needed
        }

        private fun checkHotkey() {
            val currentCombination = createKeyCombination(pressedKeys)
            registeredHotkeys[currentCombination]?.let { action ->
                scope.launch {
                    action()
                }
            }
        }
    }

    init {
        // Disable JNativeHook logging
        val logger = Logger.getLogger(GlobalScreen::class.java.`package`.name)
        logger.level = Level.WARNING
        logger.useParentHandlers = false
    }

    /**
     * Initialize the global hotkey listener
     */
    fun initialize() {
        if (isListening) return

        try {
            GlobalScreen.registerNativeHook()
            GlobalScreen.addNativeKeyListener(keyListener)
            isListening = true
            println("Global hotkey manager initialized")
        } catch (e: NativeHookException) {
            println("Failed to register native hook: ${e.message}")
        }
    }

    /**
     * Register a hotkey with an action
     */
    fun registerHotkey(
        action: HotkeyAction,
        keyCombination: String,
        callback: () -> Unit
    ) {
        val normalizedKey = normalizeKeyCombination(keyCombination)
        registeredHotkeys[normalizedKey] = callback
        println("Registered hotkey: $normalizedKey for action: $action")
    }

    /**
     * Register multiple hotkeys from a map
     */
    fun registerHotkeys(
        hotkeys: Map<HotkeyAction, String>,
        callbacks: Map<HotkeyAction, () -> Unit>
    ) {
        hotkeys.forEach { (action, keyCombination) ->
            callbacks[action]?.let { callback ->
                registerHotkey(action, keyCombination, callback)
            }
        }
    }

    /**
     * Unregister a hotkey
     */
    fun unregisterHotkey(keyCombination: String) {
        val normalizedKey = normalizeKeyCombination(keyCombination)
        registeredHotkeys.remove(normalizedKey)
        println("Unregistered hotkey: $normalizedKey")
    }

    /**
     * Clear all registered hotkeys
     */
    fun clearHotkeys() {
        registeredHotkeys.clear()
        println("Cleared all hotkeys")
    }

    /**
     * Dispose of the hotkey manager
     */
    fun dispose() {
        if (!isListening) return

        try {
            GlobalScreen.removeNativeKeyListener(keyListener)
            GlobalScreen.unregisterNativeHook()
            isListening = false
            clearHotkeys()
            println("Global hotkey manager disposed")
        } catch (e: NativeHookException) {
            println("Failed to unregister native hook: ${e.message}")
        }
    }

    /**
     * Normalize a key combination string for consistent comparison
     */
    private fun normalizeKeyCombination(keyCombination: String): String {
        val parts = keyCombination.lowercase().split("+", "-")
            .map { it.trim() }
            .sorted()
        return parts.joinToString("+")
    }

    /**
     * Create a key combination string from pressed keys
     */
    private fun createKeyCombination(keys: Set<Int>): String {
        val keyNames = keys.mapNotNull { code ->
            getNativeKeyName(code)
        }.sorted()
        return keyNames.joinToString("+")
    }

    /**
     * Get a normalized key name from native key code
     */
    private fun getNativeKeyName(keyCode: Int): String? {
        return when (keyCode) {
            // Modifiers
            NativeKeyEvent.VC_CONTROL -> "ctrl"
            NativeKeyEvent.VC_SHIFT -> "shift"
            NativeKeyEvent.VC_ALT -> "alt"
            NativeKeyEvent.VC_META -> if (isMac()) "cmd" else "meta"

            // Letters
            NativeKeyEvent.VC_A -> "a"
            NativeKeyEvent.VC_B -> "b"
            NativeKeyEvent.VC_C -> "c"
            NativeKeyEvent.VC_D -> "d"
            NativeKeyEvent.VC_E -> "e"
            NativeKeyEvent.VC_F -> "f"
            NativeKeyEvent.VC_G -> "g"
            NativeKeyEvent.VC_H -> "h"
            NativeKeyEvent.VC_I -> "i"
            NativeKeyEvent.VC_J -> "j"
            NativeKeyEvent.VC_K -> "k"
            NativeKeyEvent.VC_L -> "l"
            NativeKeyEvent.VC_M -> "m"
            NativeKeyEvent.VC_N -> "n"
            NativeKeyEvent.VC_O -> "o"
            NativeKeyEvent.VC_P -> "p"
            NativeKeyEvent.VC_Q -> "q"
            NativeKeyEvent.VC_R -> "r"
            NativeKeyEvent.VC_S -> "s"
            NativeKeyEvent.VC_T -> "t"
            NativeKeyEvent.VC_U -> "u"
            NativeKeyEvent.VC_V -> "v"
            NativeKeyEvent.VC_W -> "w"
            NativeKeyEvent.VC_X -> "x"
            NativeKeyEvent.VC_Y -> "y"
            NativeKeyEvent.VC_Z -> "z"

            // Numbers
            NativeKeyEvent.VC_0 -> "0"
            NativeKeyEvent.VC_1 -> "1"
            NativeKeyEvent.VC_2 -> "2"
            NativeKeyEvent.VC_3 -> "3"
            NativeKeyEvent.VC_4 -> "4"
            NativeKeyEvent.VC_5 -> "5"
            NativeKeyEvent.VC_6 -> "6"
            NativeKeyEvent.VC_7 -> "7"
            NativeKeyEvent.VC_8 -> "8"
            NativeKeyEvent.VC_9 -> "9"

            // Special keys
            NativeKeyEvent.VC_SPACE -> "space"
            NativeKeyEvent.VC_ENTER -> "enter"
            NativeKeyEvent.VC_ESCAPE -> "escape"
            NativeKeyEvent.VC_TAB -> "tab"
            NativeKeyEvent.VC_BACKSPACE -> "backspace"
            NativeKeyEvent.VC_DELETE -> "delete"

            // Function keys
            NativeKeyEvent.VC_F1 -> "f1"
            NativeKeyEvent.VC_F2 -> "f2"
            NativeKeyEvent.VC_F3 -> "f3"
            NativeKeyEvent.VC_F4 -> "f4"
            NativeKeyEvent.VC_F5 -> "f5"
            NativeKeyEvent.VC_F6 -> "f6"
            NativeKeyEvent.VC_F7 -> "f7"
            NativeKeyEvent.VC_F8 -> "f8"
            NativeKeyEvent.VC_F9 -> "f9"
            NativeKeyEvent.VC_F10 -> "f10"
            NativeKeyEvent.VC_F11 -> "f11"
            NativeKeyEvent.VC_F12 -> "f12"

            else -> null
        }
    }

    /**
     * Check if running on macOS
     */
    private fun isMac(): Boolean {
        val os = System.getProperty("os.name").lowercase()
        return os.contains("mac") || os.contains("darwin")
    }

    companion object {
        /**
         * Convert a user-friendly key combination to normalized format
         */
        fun parseKeyCombination(input: String): String {
            return input.lowercase()
                .replace("cmd", if (isMacStatic()) "cmd" else "ctrl")
                .replace("command", if (isMacStatic()) "cmd" else "ctrl")
                .replace("control", "ctrl")
                .replace("option", "alt")
                .replace("plus", "+")
                .split(Regex("[+\\-]"))
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .sorted()
                .joinToString("+")
        }

        private fun isMacStatic(): Boolean {
            val os = System.getProperty("os.name").lowercase()
            return os.contains("mac") || os.contains("darwin")
        }
    }
}