package club.ozgur.gifland.encoder

import club.ozgur.gifland.util.Log
import java.io.File
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO

/**
 * Simple native encoder that uses the Rust binary via command line
 */
object NativeEncoderSimple {



    fun findFfmpeg(): String {
        return JAVEEncoder.getFFmpegPath()
            ?: throw RuntimeException("JAVE2 FFmpeg not available - check Gradle dependencies")
    }

    // Build robust input args for an image sequence from actual file names
    private fun buildImageSequenceInputArgs(frameFiles: List<File>, fps: Int): List<String> {
        val first = frameFiles.first()
        val name = first.name
        val dir = first.parentFile.absolutePath
        val m = Regex("^(.*?)(\\d+)(\\.[A-Za-z0-9]+)$").find(name)
        return if (m != null) {
            val prefix = m.groupValues[1]
            val digits = m.groupValues[2]
            val ext = m.groupValues[3]
            val pad = digits.length
            val startNum = runCatching { digits.toInt() }.getOrElse { 1 }
            val pattern = "$dir/${prefix}%0${pad}d${ext}"
            val args = mutableListOf("-framerate", fps.toString(), "-pattern_type", "sequence")
            if (startNum != 1) {
                args += listOf("-start_number", startNum.toString())
            }
            args + listOf("-i", pattern)
        } else {
            // Fallback to glob pattern (e.g., ffcap_*.jpg)
            val base = name.substringBeforeLast('.')
            val prefixOnly = base.substringBeforeLast('_', base)
            val ext = name.substringAfterLast('.', "jpg")
            listOf("-framerate", fps.toString(), "-pattern_type", "glob", "-i", "$dir/${prefixOnly}_*.${ext}")
        }
    }


    fun encodeWebPFromFiles(
        frameFiles: List<File>,
        outputFile: File,
        quality: Int = 80,
        fps: Int = 30,
        onProgress: ((Int) -> Unit)? = null
    ): Result<File> {
        return try {
            // Use bundled or system ffmpeg
            val ffmpegPath = findFfmpeg()

            Log.d("NativeEncoderSimple", "Using FFmpeg at: $ffmpegPath for ${frameFiles.size} frames")

            // Since our minimal FFmpeg doesn't support concat, use image2 pattern matching
            // First, ensure frames are in sequence
            if (frameFiles.isEmpty()) {
                return Result.failure(Exception("No frames to encode"))
            }

            // Get the directory containing the frames
            val frameDir = frameFiles.first().parentFile

            // Log frame files for debugging
            Log.d("NativeEncoderSimple", "Frame directory: ${frameDir.absolutePath}")
            Log.d("NativeEncoderSimple", "First 3 frame files: ${frameFiles.take(3).map { it.name }}")
            Log.d("NativeEncoderSimple", "Frame files exist: ${frameFiles.take(3).map { it.exists() }}")

            // Start progress at 0
            onProgress?.invoke(0)

            // Build FFmpeg command
            val ffmpegCommand = mutableListOf<String>().apply {
                add(ffmpegPath)
                addAll(listOf(
                    "-y", // Overwrite output
                    "-stats", // Enable progress stats output
                    "-threads", "0", // Use all available CPU cores
                ))
                // Input image sequence (support both numeric and glob patterns)
                addAll(buildImageSequenceInputArgs(frameFiles, fps))
                addAll(listOf(
                    "-c:v", "libwebp"
                ))
            }

            ffmpegCommand.addAll(listOf(
                "-lossless", "0",
                "-quality", quality.toString(),
                // Higher compression_level and method for noticeably better quality at acceptable speed
                "-compression_level", "6",
                "-method", "6",
                "-loop", "0",
                outputFile.absolutePath
            ))

            Log.d("NativeEncoderSimple", "FFmpeg command: ${ffmpegCommand.joinToString(" ")}")

            val process = ProcessBuilder(ffmpegCommand)
                .redirectErrorStream(false).start() // Don't mix stderr with stdout

            // Monitor FFmpeg progress in a separate thread
            val outputBuilder = StringBuilder()
            val totalFrames = frameFiles.size

            // Track if process crashed with error 134
            var crashedWith134 = false

            Thread {
                try {
                    val reader = process.errorStream.bufferedReader() // FFmpeg outputs to stderr
                    var line: String?
                    var lastProgress = 0

                    while (reader.readLine().also { line = it } != null) {
                        outputBuilder.appendLine(line)

                        // Check for error 134 symptoms
                        if (line?.contains("signal 6") == true ||
                            line?.contains("SIGABRT") == true ||
                            line?.contains("Abort trap") == true) {
                            Log.e("NativeEncoderSimple", "FFmpeg crashed with SIGABRT (error 134): $line")
                            crashedWith134 = true
                            FFmpegDebugManager.setError("FFmpeg crashed with error 134 (SIGABRT) - code signing issue")
                        }

                        // FFmpeg outputs: "frame=   10 fps=..."
                        val framePattern = Regex("frame=\\s*(\\d+)")
                        val match = framePattern.find(line ?: "")

                        match?.groupValues?.get(1)?.toIntOrNull()?.let { currentFrame ->
                            // Calculate progress from 0% to 95% (leave 5% for finalization)
                            val progress = if (totalFrames > 0) {
                                (currentFrame * 95 / totalFrames).coerceIn(0, 95)
                            } else {
                                50 // Default progress if we can't calculate
                            }
                            if (progress > lastProgress) {
                                Log.d("NativeEncoderSimple", "WebP encoding progress: $progress% (frame $currentFrame/$totalFrames)")
                                onProgress?.invoke(progress)
                                lastProgress = progress
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("NativeEncoderSimple", "Error reading FFmpeg stderr", e)
                }
            }.start()

            // Using only real FFmpeg progress

            // Read stdout for progress info
            Thread {
                try {
                    val reader = process.inputStream.bufferedReader()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        // Parse progress output if using -progress flag
                        if (line?.startsWith("out_time_ms=") == true) {
                            Log.d("NativeEncoderSimple", "Progress output: $line")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("NativeEncoderSimple", "Error reading FFmpeg stdout", e)
                }
            }.start()

            // Wait for process to complete (increased timeout for WebP)
            val success = process.waitFor(540, TimeUnit.SECONDS)
            val output = outputBuilder.toString()

            if (success && process.exitValue() == 0 && outputFile.exists()) {
                Log.d("NativeEncoderSimple", "WebP encoding successful: ${outputFile.absolutePath} (${outputFile.length()} bytes)")
                onProgress?.invoke(100)
                Result.success(outputFile)
            } else {
                val exitCode = if (success) process.exitValue() else -1
                val errorMsg = when {
                    exitCode == 134 || crashedWith134 -> {
                        Log.e("NativeEncoderSimple", "FFmpeg crashed with error 134 (SIGABRT) - This is a code signing issue on macOS")
                        FFmpegDebugManager.setError("Error 134: FFmpeg signature verification failed. This Mac may have stricter security settings.")
                        "FFmpeg exited with error code: 134"
                    }
                    exitCode == 137 -> {
                        Log.e("NativeEncoderSimple", "FFmpeg crashed with error 137 (SIGKILL) - Process was terminated by the system")
                        FFmpegDebugManager.setError("Error 137: FFmpeg was killed by the system. This may be due to memory limits or security restrictions.")
                        "FFmpeg exited with error code: 137"
                    }
                    !success -> "FFmpeg process timed out after 540 seconds"
                    exitCode != 0 -> "FFmpeg exited with error code: $exitCode"
                    !outputFile.exists() -> "Output file was not created"
                    else -> "Unknown error during encoding"
                }
                Log.e("NativeEncoderSimple", "$errorMsg. Output file exists: ${outputFile.exists()}")

                // Extract actual error from FFmpeg output (skip version info)
                val errorLines = output.lines()
                    .dropWhile { it.contains("ffmpeg version") || it.contains("built with") || it.contains("configuration:") || it.contains("lib") }
                    .filter { it.isNotBlank() && !it.startsWith(" ") }
                    .take(5)
                    .joinToString("\n")

                if (errorLines.isNotEmpty()) {
                    Log.e("NativeEncoderSimple", "FFmpeg error output: $errorLines")
                }
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e("NativeEncoderSimple", "WebP encoding error", e)
            Result.failure(e)
        }
    }

    fun encodeGIFFromFiles(
        frameFiles: List<File>,
        outputFile: File,
        fps: Int = 10,
        quality: Int = 50,
        fastMode: Boolean = false,
        onProgress: ((Int) -> Unit)? = null
    ): Result<File> {
        return try {
            val ffmpegPath = findFfmpeg()
            val osName = System.getProperty("os.name").lowercase()
            val isMac = osName.contains("mac") || osName.contains("darwin")

            // Create dynamic shell script on macOS to run bundled FFmpeg
            val executableAndArgs: Pair<String, List<String>> = if (isMac && ffmpegPath.contains("gifland_ffmpeg")) {
                try {
                    // Create a temporary shell script that will run FFmpeg
                    val scriptFile = File(System.getProperty("java.io.tmpdir"), "run_ffmpeg_${System.currentTimeMillis()}.sh")
                    scriptFile.deleteOnExit()

                    // Write script content
                    scriptFile.writeText("""
                        #!/bin/sh
                        # Remove quarantine attribute
                        xattr -cr "$ffmpegPath" 2>/dev/null || true
                        # Run FFmpeg with all arguments
                        exec "$ffmpegPath" "${"$"}@"
                    """.trimIndent())

                    // Make script executable
                    ProcessBuilder("chmod", "+x", scriptFile.absolutePath).start().waitFor()

                    Log.d("NativeEncoderSimple", "Using dynamic shell script to bypass signature issues")

                    Pair(scriptFile.absolutePath, emptyList<String>())
                } catch (e: Exception) {
                    Log.e("NativeEncoderSimple", "Failed to create wrapper script, using FFmpeg directly", e)
                    Pair(ffmpegPath, emptyList<String>())
                }
            } else {
                Pair(ffmpegPath, emptyList<String>())
            }


            Log.d("NativeEncoderSimple", "Using FFmpeg at: $ffmpegPath for ${frameFiles.size} frames (GIF)")

            if (frameFiles.isEmpty()) {
                return Result.failure(Exception("No frames to encode"))
            }

            // Get the directory containing the frames
            val frameDir = frameFiles.first().parentFile

            // Get dimensions from first frame
            val firstImage = ImageIO.read(frameFiles[0])
            val srcW = firstImage.width
            val srcH = firstImage.height

            // Quality-first params: higher fps cap, minimal/no downscaling, richer palette, high-quality dithering
            val low = quality <= 30
            val mid = quality in 31..60
            val targetFpsCap = when {
                fastMode -> 12
                low -> 12
                mid -> 16
                else -> 20
            }
            val scaleFactor = when {
                fastMode -> 0.90
                low -> 0.95
                mid -> 1.0
                else -> 1.0
            }
            val maxColors = when {
                fastMode -> 128
                low -> 128
                mid -> 192
                else -> 256
            }
            // Use high-quality dithering to reduce banding across all modes
            val dither = "sierra2_4a"

            val targetFps = minOf(fps, targetFpsCap)
            var width = (srcW * scaleFactor).toInt().coerceAtLeast(1)
            var height = (srcH * scaleFactor).toInt().coerceAtLeast(1)

            Log.d("NativeEncoderSimple", "GIF params: fastMode=$fastMode, targetFps=$targetFps, scaleFactor=$scaleFactor, size=${width}x${height}, maxColors=$maxColors, dither=$dither")
            Log.d("NativeEncoderSimple", "GIF encoding: ${frameFiles.size} frames, ${width}x${height}, fps=$targetFps")

            // Start progress at 0
            onProgress?.invoke(0)

            // Try to generate palette first for better quality
            val paletteFile = File(frameDir, "palette.png")
            var usePalette = false

            try {
                val paletteCmd = mutableListOf<String>().apply {
                    add(ffmpegPath)
                    addAll(listOf(
                        "-y",
                        // Input image sequence
                        *buildImageSequenceInputArgs(frameFiles, targetFps).toTypedArray(),
                        "-vf", "fps=$targetFps,scale=$width:$height:flags=lanczos,palettegen=max_colors=$maxColors:stats_mode=full",
                        paletteFile.absolutePath
                    ))
                }

                Log.d("NativeEncoderSimple", "Attempting to generate palette for better quality...")
                val paletteProcess = ProcessBuilder(paletteCmd).start()
                val paletteSuccess = paletteProcess.waitFor(240, TimeUnit.SECONDS)

                if (paletteSuccess && paletteProcess.exitValue() == 0 && paletteFile.exists()) {
                    Log.d("NativeEncoderSimple", "Palette generated successfully")
                    usePalette = true
                    onProgress?.invoke(30) // Palette done
                } else {
                    Log.d("NativeEncoderSimple", "Palette generation failed, will use direct GIF encoding")
                    paletteFile.delete() // Clean up any partial file
                }
            } catch (e: Exception) {
                Log.d("NativeEncoderSimple", "Palette generation error: ${e.message}, falling back to direct encoding")
                usePalette = false
            }

            // Generate GIF with or without palette
            val gifCmd = if (usePalette) {
                // Use palette for better quality
                // Dither already derived above
                mutableListOf<String>().apply {
                    add(ffmpegPath)
                    addAll(listOf(
                        "-y",
                        // Input image sequence
                        *buildImageSequenceInputArgs(frameFiles, targetFps).toTypedArray(),
                        "-i", paletteFile.absolutePath,
                        "-lavfi", "fps=$targetFps,scale=$width:$height:flags=lanczos [x]; [x][1:v] paletteuse=dither=$dither",
                        outputFile.absolutePath
                    ))
                }
            } else {
                // Direct GIF encoding without palette (works with any FFmpeg build)
                mutableListOf<String>().apply {
                    add(ffmpegPath)
                    addAll(listOf(
                        "-y",
                        // Input image sequence
                        *buildImageSequenceInputArgs(frameFiles, targetFps).toTypedArray(),
                        "-vf", "fps=$targetFps,scale=$width:$height:flags=lanczos",
                        outputFile.absolutePath
                    ))
                }
            }

            Log.d("NativeEncoderSimple", "Encoding GIF ${if (usePalette) "with palette" else "directly"}...")

            val process = ProcessBuilder(gifCmd)
                .redirectErrorStream(false).start()

            // Monitor progress
            val outputBuilder = StringBuilder()
            val totalFrames = frameFiles.size

            Thread {
                try {
                    val reader = process.errorStream.bufferedReader()
                    var line: String?
                    var lastProgress = 30

                    while (reader.readLine().also { line = it } != null) {
                        outputBuilder.appendLine(line)

                        val framePattern = Regex("frame=\\s*(\\d+)")
                        val match = framePattern.find(line ?: "")

                        match?.groupValues?.get(1)?.toIntOrNull()?.let { currentFrame ->
                            val progress = if (totalFrames > 0) {
                                30 + (currentFrame * 65 / totalFrames).coerceIn(0, 65)
                            } else {
                                50
                            }
                            if (progress > lastProgress) {
                                Log.d("NativeEncoderSimple", "GIF encoding progress: $progress% (frame $currentFrame/$totalFrames)")
                                onProgress?.invoke(progress)
                                lastProgress = progress
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("NativeEncoderSimple", "Error reading FFmpeg stderr", e)
                }
            }.start()

            // Wait for process to complete
            val success = process.waitFor(600, TimeUnit.SECONDS)
            val output = outputBuilder.toString()

            // Clean up palette file
            paletteFile.delete()

            if (success && process.exitValue() == 0 && outputFile.exists()) {
                Log.d("NativeEncoderSimple", "GIF encoding successful: ${outputFile.absolutePath} (${outputFile.length()} bytes)")
                onProgress?.invoke(100)
                Result.success(outputFile)
            } else {
                val exitCode = if (success) process.exitValue() else -1
                val errorMsg = when {
                    !success -> "FFmpeg process timed out after 120 seconds"
                    exitCode != 0 -> "FFmpeg exited with error code: $exitCode"
                    !outputFile.exists() -> "Output file was not created"
                    else -> "Unknown error during encoding"
                }
                Log.e("NativeEncoderSimple", "$errorMsg. Output file exists: ${outputFile.exists()}")

                val errorLines = output.lines()
                    .dropWhile { it.contains("ffmpeg version") || it.contains("built with") || it.contains("configuration:") || it.contains("lib") }
                    .filter { it.isNotBlank() && !it.startsWith(" ") }
                    .take(5)
                    .joinToString("\n")

                if (errorLines.isNotEmpty()) {
                    Log.e("NativeEncoderSimple", "FFmpeg error output: $errorLines")
                }
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e("NativeEncoderSimple", "GIF encoding error", e)
            Result.failure(e)
        }
    }
}