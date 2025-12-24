package club.ozgur.gifland.util

import java.awt.GraphicsDevice

/**
 * Returns a stable identifier string for a GraphicsDevice when available.
 * Falls back to `toString()` if the platform method is not present.
 */
fun GraphicsDevice.debugId(): String = try {
    val m = javaClass.getMethod("getIDstring")
    (m.invoke(this) as? String) ?: toString()
} catch (_: Exception) { toString() }


