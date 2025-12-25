package club.ozgur.gifland.core

import club.ozgur.gifland.capture.strategy.ScreenCaptureStrategy
import club.ozgur.gifland.capture.strategy.FFmpegCaptureStrategy
import club.ozgur.gifland.capture.strategy.RobotApiCaptureStrategy
import club.ozgur.gifland.capture.strategy.ScreenCaptureKitStrategy


import club.ozgur.gifland.encoder.NativeEncoderSimple
import club.ozgur.gifland.encoder.JAVEEncoder
import club.ozgur.gifland.ui.components.CaptureArea
import club.ozgur.gifland.util.debugId
import club.ozgur.gifland.util.Log
import club.ozgur.gifland.platform.showRecordingCompleteNotification
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.awt.GraphicsEnvironment
import java.awt.Point
import java.awt.Rectangle
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class RecordingState(
    val isRecording: Boolean = false,
    val isPaused: Boolean = false,
    val frameCount: Int = 0,
    val duration: Int = 0, // seconds
    val estimatedSize: Long = 0L,
    val isSaving: Boolean = false,
    val saveProgress: Int = 0,
    val captureMethod: String? = null,
    val captureMethodDetails: String? = null
)


/**
 * Records a sequence of frames from a fixed screen rectangle and encodes as WebP or GIF.
 *
 * Multi-monitor & DPI notes:
 * - We pin the recording rectangle to a specific monitor determined by its center.
 */
class Recorder {
	private var tempDir: File? = null
	private val frameFiles = mutableListOf<File>()
    private var captureStrategy: ScreenCaptureStrategy? = null
    private var collectorJob: Job? = null
    private var fallbackStep: Int = 0 // 0: primary, 1: first fallback, 2: second fallback (final)


	private var cumulativeBytes: Long = 0

    private var recordingJob: Job? = null
    private var startTime: Long = 0
    private var pausedDuration: Long = 0  // Toplam pause süresi (ms)
    private var pauseStartTime: Long = 0  // Pause başlangıç zamanı

    // Resume için son capture ayarlarını sakla
    private var lastCaptureArea: CaptureArea? = null
    private var lastCaptureFps: Int = 10
    private var lastJpegQuality: Int = 80

    private val _state = MutableStateFlow(RecordingState())
    val state: StateFlow<RecordingState> = _state

    private val _settings = MutableStateFlow(RecorderSettings())
    val settingsFlow: StateFlow<RecorderSettings> = _settings
    var settings: RecorderSettings
        get() = _settings.value
        set(value) {
            _settings.value = value
        }

    // Store last saved file for UI access
    private val _lastSavedFile = MutableStateFlow<File?>(null)
    val lastSavedFile: StateFlow<File?> = _lastSavedFile

    // Store last error for surfacing issues in UI
    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    init {
        Log.d("Recorder", "========================================")
        Log.d("Recorder", "🚀 NEW RECORDER INSTANCE CREATED")
        Log.d("Recorder", "Default settings: fps=${settings.fps}, quality=${settings.quality}")
        Log.d("Recorder", "========================================")
    }

    fun startRecording(
        area: CaptureArea? = null,
        onUpdate: (RecordingState) -> Unit = {},
        onComplete: (Result<File>) -> Unit = {}
    ) {
        if (_state.value.isRecording) return

        // Debug: Print actual settings being used
        Log.d("Recorder", "========================================")
        Log.d("Recorder", "📹 RECORDER STARTING WITH SETTINGS:")
        Log.d("Recorder", "Format: ${settings.format}")
        Log.d("Recorder", "FPS: ${settings.fps}")
        Log.d("Recorder", "Quality: ${settings.quality}")
        Log.d("Recorder", "Max Duration: ${settings.maxDuration}s")
        Log.d("Recorder", "========================================")

        // Clear previous error before starting a new session
        _lastError.value = null

        // Proactively cleanup any stale temp folders from previous abnormal exits
        cleanupStaleTempDirs()

        frameFiles.clear()
        cumulativeBytes = 0
        startTime = System.currentTimeMillis()
        pausedDuration = 0
        pauseStartTime = 0
        _state.update { RecordingState(isRecording = true) }
        Log.d("Recorder", "startRecording area=$area settings=$settings")

        // Apply GIF-specific FPS caps earlier to avoid over-capturing
        val clampedFps = run {
            val base = settings.fps.coerceIn(1, 60)
            if (settings.format == OutputFormat.GIF) {
                val cap = when {
                    settings.fastGifPreview -> 10
                    settings.quality < 20 -> 10
                    settings.quality <= 40 -> 12
                    else -> 15
                }
                minOf(base, cap)
            } else base
        }  // Support up to 60 FPS
        val captureRect = area?.let { Rectangle(it.x, it.y, it.width, it.height) } ?: getFullScreenBounds()

        // Choose monitor by rectangle center (for logs/debug)
        val screens = GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices
        val center = Point(captureRect.x + captureRect.width / 2, captureRect.y + captureRect.height / 2)
        val targetScreen = screens.find { it.defaultConfiguration.bounds.contains(center) } ?: screens[0]
        val monitorIndex = screens.indexOf(targetScreen)
        Log.d("Recorder", "========================================")
        Log.d("Recorder", "🖥️ MULTI-MONITOR INFO:")
        Log.d("Recorder", "Total monitors: ${screens.size}")
        Log.d("Recorder", "Selected monitor: #$monitorIndex (${targetScreen.debugId()})")
        Log.d("Recorder", "Monitor bounds: ${targetScreen.defaultConfiguration.bounds}")
        Log.d("Recorder", "Capture rect: $captureRect")
        Log.d("Recorder", "========================================")

		// Prepare temp directory for disk-backed frames
		val sessionStamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
		tempDir = File(System.getProperty("java.io.tmpdir"), "gifland_${sessionStamp}").also { it.mkdirs() }
		Log.d("Recorder", "Temp dir created: ${tempDir?.absolutePath}")

        // Start using Strategy pattern: prefer ScreenCaptureKit (macOS), then Robot, then FFmpeg
        val jpegQualityPercent = when {
            settings.quality >= 45 -> 92
            settings.quality >= 35 -> 88
            settings.quality >= 25 -> 85
            settings.quality >= 15 -> 80
            else -> 75
        }

        fallbackStep = 0
        val isMac = System.getProperty("os.name").lowercase().contains("mac") || System.getProperty("os.name").lowercase().contains("darwin")
        val osVersion = System.getProperty("os.version")
        var sckInitFailureMsg: String? = null
        // Cap SCK FPS to 10 to reduce load on JPEG encoding (increased from 5 for smoother recordings)
        val sckFps = minOf(clampedFps, 10)

        // Convert captureRect to CaptureArea for strategy (ensures correct monitor bounds even when area param is null)
        val captureAreaForStrategy = CaptureArea(captureRect.x, captureRect.y, captureRect.width, captureRect.height)

        // Resume için capture ayarlarını sakla
        lastCaptureArea = captureAreaForStrategy
        lastCaptureFps = if (isMac) minOf(clampedFps, 10) else clampedFps
        lastJpegQuality = jpegQualityPercent

        captureStrategy = if (isMac) {
            runCatching {
                Log.d("Recorder", "Attempting to initialize ScreenCaptureKit (macOS $osVersion)")
                ScreenCaptureKitStrategy().also {
                    it.start(captureAreaForStrategy, sckFps, settings.scale, jpegQualityPercent, tempDir!!)
                    Log.d("Recorder", "ScreenCaptureKit initialized successfully with ${sckFps}fps")
                }
            }.onFailure { err ->
                val msg = err.message?.takeIf { it.isNotBlank() }
                val detail = "${err.javaClass.simpleName}${msg?.let { ": $it" } ?: ""}"
                sckInitFailureMsg = "ScreenCaptureKit başlatılamadı ($detail)"
                Log.e("Recorder", "ScreenCaptureKit initialization failed: $detail", err)
                Log.d("Recorder", "Possible causes: macOS version < 12.3, missing screen recording permissions, or native library loading issues")
                Log.d("Recorder", "Falling back to Robot API due to SCK failure")
            }
             .getOrElse {
                 Log.d("Recorder", "Using Robot API fallback strategy (${clampedFps}fps)")
                 RobotApiCaptureStrategy().also { it.start(captureAreaForStrategy, clampedFps, settings.scale, jpegQualityPercent, tempDir!!) }
             }
        } else {
            Log.d("Recorder", "Non-macOS platform detected (${System.getProperty("os.name")}), using Robot API")
            RobotApiCaptureStrategy().also { it.start(captureAreaForStrategy, clampedFps, settings.scale, jpegQualityPercent, tempDir!!) }
        }
        // Update capture method label in state (and any initial failure details)
        runCatching {
            val currentMethod = when (captureStrategy) {
                is ScreenCaptureKitStrategy -> "ScreenCaptureKit"
                is RobotApiCaptureStrategy -> "Robot API"
                is FFmpegCaptureStrategy -> "FFmpeg"
                else -> null
            }
            val details = when (captureStrategy) {
                is RobotApiCaptureStrategy -> sckInitFailureMsg
                else -> null
            }
            _state.update { it.copy(captureMethod = currentMethod, captureMethodDetails = details) }
        }

        // Early diagnostics & fallback: if no frames after a short while, advance through fallback chain
        ApplicationScope.launchIO {
            delay(3000)
            if (_state.value.isRecording && frameFiles.isEmpty()) {
                val alive = captureStrategy?.isRunning() ?: false
                val logSnippet = runCatching {
                    tempDir?.let { File(it, "ffmpeg_capture.log") }?.takeIf { it.exists() }?.readText()?.take(1000)
                }.getOrNull()

                if (fallbackStep == 0) {
                    // First fallback step
                    when (captureStrategy) {
                        is ScreenCaptureKitStrategy -> {
                            Log.d("Recorder", "No frames; switching from ScreenCaptureKit to Robot fallback. prevAlive=$alive")
                            runCatching { captureStrategy?.stop() }
                            captureStrategy = RobotApiCaptureStrategy().also { it.start(area, clampedFps, settings.scale, jpegQualityPercent, tempDir!!) }
                            val method = "Robot API"
                            val details = "ScreenCaptureKit kare üretemedi"
                            _state.update { it.copy(captureMethod = method, captureMethodDetails = details) }
                            withContext(Dispatchers.Main) { onUpdate(_state.value) }
                        }
                        is RobotApiCaptureStrategy -> {
                            Log.d("Recorder", "No frames; switching from Robot to FFmpeg fallback. prevAlive=$alive")
                            runCatching { captureStrategy?.stop() }
                            captureStrategy = FFmpegCaptureStrategy().also { it.start(area, clampedFps, settings.scale, jpegQualityPercent, tempDir!!) }
                            val method = "FFmpeg"
                            val details = "Robot API kare uretemedi"
                            _state.update { it.copy(captureMethod = method, captureMethodDetails = details) }
                            withContext(Dispatchers.Main) { onUpdate(_state.value) }
                        }
                        else -> {
                            Log.d("Recorder", "No frames; switching to FFmpeg fallback as default. prevAlive=$alive")
                            runCatching { captureStrategy?.stop() }
                            captureStrategy = FFmpegCaptureStrategy().also { it.start(area, clampedFps, settings.scale, jpegQualityPercent, tempDir!!) }
                            val method = "FFmpeg"
                            val details = "Onceki yontem kare uretemedi"
                            _state.update { it.copy(captureMethod = method, captureMethodDetails = details) }
                            withContext(Dispatchers.Main) { onUpdate(_state.value) }
                        }
                    }
                    fallbackStep = 1
                    return@launchIO
                } else if (fallbackStep == 1) {
                    // Second fallback step (ensure FFmpeg is used)
                    if (captureStrategy !is FFmpegCaptureStrategy) {
                        Log.d("Recorder", "No frames; enforcing FFmpeg as final fallback. prevAlive=$alive")
                        runCatching { captureStrategy?.stop() }
                        captureStrategy = FFmpegCaptureStrategy().also { it.start(area, clampedFps, settings.scale, jpegQualityPercent, tempDir!!) }
                        val method = "FFmpeg"
                        val details = "Son care FFmpeg'e gecildi"
                        _state.update { it.copy(captureMethod = method, captureMethodDetails = details) }
                        withContext(Dispatchers.Main) { onUpdate(_state.value) }
                    }
                    fallbackStep = 2
                    return@launchIO
                }

                val baseMsg = if (!alive) {
                    "Ekran yakalama başlatılamadı (yakalama stratejisi erken sona erdi)."
                } else {
                    "Ekran yakalama beklenen sürede kare üretemedi."
                }
                val hint = buildString {
                    append(" Olası nedenler: ")
                    append("• macOS ekran kaydı izni verilmemiş olabilir (Sistem Ayarları > Gizlilik ve Güvenlik > Ekran Kaydı). ")
                    append("• Güvenlik/İmza (code signing) veya FFmpeg/Robot erişim izinleri. ")
                    append("• Harici monitör/sıralama farkları.")
                }
                _lastError.value = baseMsg + hint + (logSnippet?.let { "\nFFmpeg günlük özeti:\n" + it } ?: "")
                Log.e("Recorder", "Early no-frames condition. alive=$alive (after fallback)")
            }
        }

        // Start collector to watch output directory and update state
        collectorJob = ApplicationScope.launchIO {
            var lastCount = 0
            var lastCountChangeAt = System.currentTimeMillis()
            var lastDurationEmitted = -1
            while (isActive && _state.value.isRecording) {
                try {
                    val out = tempDir
                    val files = out?.listFiles { f -> f.isFile && f.name.endsWith(".jpg") }?.sortedBy { it.name } ?: emptyList()
                    if (files.size > frameFiles.size) {
                        val newFiles = files.drop(frameFiles.size)
                        frameFiles.addAll(newFiles)
                        newFiles.forEach { cumulativeBytes += it.length() }
                    }

                    val now = System.currentTimeMillis()
                    // Pause sırasında geçen süreyi çıkar
                    val effectivePausedTime = if (_state.value.isPaused) {
                        pausedDuration + (now - pauseStartTime)
                    } else {
                        pausedDuration
                    }
                    val duration = ((now - startTime - effectivePausedTime) / 1000).toInt()

                    // Emit updates when frames changed
                    if (frameFiles.size != lastCount) {
                        lastCount = frameFiles.size
                        lastCountChangeAt = now
                        _state.update { it.copy(
                            frameCount = frameFiles.size,
                            duration = duration,
                            estimatedSize = cumulativeBytes
                        ) }
                        withContext(Dispatchers.Main) { onUpdate(_state.value) }
                    } else if (duration != lastDurationEmitted) {
                        // Also keep the timer UI progressing even if frames stall
                        lastDurationEmitted = duration
                        _state.update { it.copy(duration = duration, estimatedSize = cumulativeBytes) }
                        withContext(Dispatchers.Main) { onUpdate(_state.value) }
                    }

                    // Detect true capture stall for SCK using its native frame heartbeat; otherwise use file growth
                    // Note: SCK stall detection disabled because JPEG encoding is slow
                    val sckHeartbeatStalled = false  // Disabled for ScreenCaptureKit
                    val genericStalled = frameFiles.isNotEmpty() && now - lastCountChangeAt > 2000

                    if (sckHeartbeatStalled || (captureStrategy !is ScreenCaptureKitStrategy && genericStalled)) {
                        Log.d("Recorder", "Frame stream stalled for >2s; attempting fallback. currentStrategy=${captureStrategy?.javaClass?.simpleName}")
                        when (captureStrategy) {
                            is ScreenCaptureKitStrategy -> {
                                // Only fallback if SCK truly stopped producing frames (not just slow encoding)
                                runCatching { captureStrategy?.stop() }
                                captureStrategy = RobotApiCaptureStrategy().also { it.start(area, clampedFps, settings.scale, jpegQualityPercent, tempDir!!) }
                                _state.update { it.copy(captureMethod = "Robot API", captureMethodDetails = "ScreenCaptureKit kare akışı durdu") }
                                withContext(Dispatchers.Main) { onUpdate(_state.value) }
                                lastCountChangeAt = now // reset after switching
                            }
                            is RobotApiCaptureStrategy -> {
                                runCatching { captureStrategy?.stop() }
                                captureStrategy = FFmpegCaptureStrategy().also { it.start(area, clampedFps, settings.scale, jpegQualityPercent, tempDir!!) }
                                _state.update { it.copy(captureMethod = "FFmpeg", captureMethodDetails = "Robot API kare akışı durdu") }
                                withContext(Dispatchers.Main) { onUpdate(_state.value) }
                                lastCountChangeAt = now
                            }
                        }
                    }

                    if (duration >= settings.maxDuration) {
                        Log.d("Recorder", "Max duration reached, stopping and saving...")
                        ApplicationScope.launchIO {
                            val result = stopRecordingInternal()
                            withContext(Dispatchers.Main) { onComplete(result) }
                        }
                        break
                    }
                } catch (e: CancellationException) {
                    break
                } catch (e: Exception) {
                    Log.e("Recorder", "Collector error", e)
                    _state.update { it.copy(isRecording = false) }
                    _lastError.value = e.message ?: "Bilinmeyen ekran yakalama hatas\u0131"
                    withContext(Dispatchers.Main) { onComplete(Result.failure(e)) }
                    cancel("Collector failed", e)
                }
                delay(100)
            }
        }

        recordingJob = collectorJob
    }

    fun pauseRecording() {
        val wasPaused = _state.value.isPaused
        _state.update { it.copy(isPaused = !wasPaused) }
        Log.d("Recorder", "pauseRecording called: wasPaused=$wasPaused -> isPaused=${!wasPaused}")

        if (!wasPaused) {
            // PAUSING - stop capture strategy
            pauseStartTime = System.currentTimeMillis()
            captureStrategy?.stop()
            Log.d("Recorder", "Recording PAUSED - capture stopped")
        } else {
            // RESUMING - restart capture strategy
            val pauseTime = System.currentTimeMillis() - pauseStartTime
            pausedDuration += pauseTime

            // Restart capture with same settings
            tempDir?.let { dir ->
                lastCaptureArea?.let { area ->
                    captureStrategy?.start(area, lastCaptureFps, settings.scale, lastJpegQuality, dir)
                }
            }
            Log.d("Recorder", "Recording RESUMED - capture restarted (pausedDuration=$pausedDuration)")
        }
    }

    suspend fun stopRecording(): Result<File> {
        return stopRecordingInternal()
    }

    private suspend fun stopRecordingInternal(): Result<File> {
        _state.update { it.copy(isRecording = false, isSaving = true, saveProgress = 0) }
        // Cancel capture loop immediately to stop producing frames
        recordingJob?.cancel()
        recordingJob = null
        collectorJob?.cancel()
        collectorJob = null
        // Stop current capture strategy gracefully
        runCatching { captureStrategy?.stop() }
        captureStrategy = null

        // Calculate actual recording duration
        val actualDurationMs = System.currentTimeMillis() - startTime

        if (frameFiles.isEmpty()) {
            Log.e("Recorder", "No frames captured at stop")
            val logPath = runCatching { File(tempDir, "ffmpeg_capture.log").takeIf { it.exists() }?.absolutePath }.getOrNull()
            val guidance = buildString {
                append("Kayıt başarısız: Hiç kare yakalanamadı. \n")
                append("• macOS'te Sistem Ayarları > Gizlilik ve Güvenlik > Ekran Kaydı bölümünden GIF Land için izin verin.\n")
                append("• Eğer istenirse FFmpeg yardımcı aracına da izin vermeniz gerekebilir.\n")
                append("• Farklı monitör/sıralama ve ölçek (HiDPI) ayarlarını kontrol edin.")
                if (!logPath.isNullOrBlank()) append("\nFFmpeg günlük dosyası: $logPath")
            }
            _lastError.value = guidance
            return Result.failure(Exception("No frames captured"))
        }

        val saveResult = withContext(Dispatchers.IO) {
            try {
                val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
                when (settings.format) {
                OutputFormat.WEBP -> {
                        val outputFile = File(System.getProperty("user.home") + "/Documents", "recording_${timestamp}.webp")
                        Log.d("Recorder", "Encoding ${frameFiles.size} JPEG frames to WebP ${outputFile}")
                        // Calculate actual FPS based on frame count and duration
                        val actualFps = if (actualDurationMs > 0) {
                            (frameFiles.size * 1000.0 / actualDurationMs).toInt().coerceIn(1, 60)
                        } else {
                            settings.fps
                        }
                        Log.d("Recorder", "WebP encoding: frames=${frameFiles.size}, duration=${actualDurationMs}ms, actualFps=$actualFps (target was ${settings.fps})")
                        // Use JAVE2 on macOS to avoid signature issues
                        val webpQuality = when {
                            settings.quality >= 45 -> 90
                            settings.quality >= 35 -> 80
                            settings.quality >= 25 -> 70
                            settings.quality >= 15 -> 55
                            settings.quality >= 10 -> 40
                            else -> 30
                        }

                        val result = NativeEncoderSimple.encodeWebPFromFiles(
                            frameFiles = frameFiles,
                            outputFile = outputFile,
                            quality = webpQuality,
                            fps = actualFps,
                            onProgress = { p ->
                                Log.d("Recorder", "WebP encoding progress: $p%")
                                _state.update { it.copy(saveProgress = p) }
                            }
                        )
                        cleanupTemp()
                        result
                }
				OutputFormat.GIF -> {
				val outputFile = File(System.getProperty("user.home") + "/Documents", "recording_${timestamp}.gif")

				// Apply GIF fps cap based on quality and fast preview
				val rawFps = if (actualDurationMs > 0) {
					(frameFiles.size * 1000.0 / actualDurationMs).toInt().coerceIn(1, 60)
				} else {
					settings.fps.coerceIn(1, 60)
				}
				val gifCap = when {
					settings.fastGifPreview -> 12
					settings.quality < 20 -> 12
					settings.quality <= 40 -> 16
					else -> 20
				}
				val actualFps = minOf(rawFps, gifCap)
				Log.d("Recorder", "GIF encoding: frames=${frameFiles.size}, duration=${actualDurationMs}ms, rawFps=$rawFps, gifCap=$gifCap, actualFps=$actualFps")

				// GIF quality settings
				val gifQuality = when {
					settings.quality >= 40 -> 80  // Çok yüksek kalite
					settings.quality >= 25 -> 60  // Yüksek kalite
					settings.quality >= 15 -> 40  // İyi kalite
					else -> 25  // Minimum kabul edilebilir kalite
				}

				// Use JAVE2 on macOS to avoid signature issues
				val result =
					NativeEncoderSimple.encodeGIFFromFiles(
						frameFiles = frameFiles,
						outputFile = outputFile,
						fps = actualFps,
						quality = gifQuality,
						fastMode = settings.fastGifPreview,
						onProgress = { p ->
							Log.d("Recorder", "GIF encoding progress: $p%")
							_state.update { it.copy(saveProgress = p) }
						}
					)
				Log.d("Recorder", "GIF encoding completed, cleaning up temp files...")
				cleanupTemp()
				Log.d("Recorder", "Temp files cleaned up, returning result")
				result
			}
                }
            } catch (e: Exception) {
                Log.e("Recorder", "Error during save operation", e)
                _lastError.value = e.message ?: "Kayıt kaydedilemedi"
                cleanupTemp() // Try to cleanup even on error
                Result.failure(e)
            }
        }
        // Mark saving done - clear the progress to 0 when done
        _state.update { it.copy(isSaving = false, saveProgress = 0) }
        Log.d("Recorder", "Saving complete, state updated: isSaving=false, saveProgress=0")

        // Store last saved file on success; set error on failure
        saveResult.onSuccess { file ->
            _lastSavedFile.value = file
            Log.d("Recorder", "Last saved file updated: ${file.absolutePath}")
            _lastError.value = null
            // Show system notification
            showRecordingCompleteNotification(file.absolutePath, file.length())
        }.onFailure { e ->
            _lastError.value = e.message ?: "Kayıt kaydedilemedi"

        }

        return saveResult
    }

	private fun cleanupTemp() {
		runCatching {
			frameFiles.forEach { it.delete() }
            tempDir?.delete()
            tempDir = null
			frameFiles.clear()
			cumulativeBytes = 0
		}
	}

	private fun cleanupStaleTempDirs() {
		val tmpRoot = File(System.getProperty("java.io.tmpdir"))
		val stale = tmpRoot.listFiles { f -> f.isDirectory && f.name.startsWith("gifland_") } ?: return
		stale.forEach { dir ->
			runCatching {
				val ok = dir.deleteRecursively()
				Log.d("Recorder", "Cleaned stale temp dir: ${dir.absolutePath} ok=${ok}")
			}
		}
	}

    private fun getFullScreenBounds(): Rectangle {
        val ge = GraphicsEnvironment.getLocalGraphicsEnvironment()
        val mouse = java.awt.MouseInfo.getPointerInfo().location
        val device = ge.screenDevices.find { it.defaultConfiguration.bounds.contains(mouse) } ?: ge.defaultScreenDevice
        return device.defaultConfiguration.bounds
    }

    fun reset() {
        Log.d("Recorder", "Reset called - clearing recording state (preserving lastSavedFile)")
        _state.update { RecordingState() }
        frameFiles.clear()
        cumulativeBytes = 0
        // NOTE: _lastSavedFile is intentionally NOT reset here
        // This preserves the "Open Folder" button functionality after recording
        // But clear any previous error to avoid confusing the user for next session
        _lastError.value = null
    }

    fun clearError() {
        _lastError.value = null
    }
}

enum class OutputFormat { WEBP, GIF }


