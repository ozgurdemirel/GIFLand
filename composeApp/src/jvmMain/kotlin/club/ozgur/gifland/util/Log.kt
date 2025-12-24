package club.ozgur.gifland.util

import java.time.LocalTime
import java.time.format.DateTimeFormatter

object Log {
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
    var enabled = true

    fun d(tag: String, msg: String) {
        if (!enabled) return
        println("[${time()}][DEBUG][$tag] $msg")
    }

    fun e(tag: String, msg: String, t: Throwable? = null) {
        if (!enabled) return
        println("[${time()}][ERROR][$tag] $msg")
        t?.printStackTrace()
    }

    private fun time(): String = LocalTime.now().format(timeFmt)
}


