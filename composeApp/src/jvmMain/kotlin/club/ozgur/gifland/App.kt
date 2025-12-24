package club.ozgur.gifland

// ===== IMPORT'LAR - GEREKLI KÜTÜPHANELER =====
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.WindowState
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.transitions.FadeTransition
import club.ozgur.gifland.core.Recorder
import club.ozgur.gifland.domain.model.AppState
import club.ozgur.gifland.domain.model.AppSettings
import club.ozgur.gifland.domain.repository.StateRepository
import club.ozgur.gifland.domain.service.RecordingService
import club.ozgur.gifland.presentation.components.QuickAccessPanel
import club.ozgur.gifland.presentation.components.ContextualActionMenu
import club.ozgur.gifland.ui.screens.MainScreenCompact
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

// ===== COMPOSITION LOCAL'LER - GLOBAL STATE PAYLAŞIMI =====
/**
 * CompositionLocal nedir?
 * - Compose'da veriyi tüm UI ağacında paylaşmanın bir yolu
 * - Props drilling'den kaçınmamızı sağlar (her seviyede veriyi geçirmek zorunda kalmayız)
 * - Context API'ye benzer (React'tan bilenler için)
 *
 * Örnek kullanım:
 * Herhangi bir Composable içinde şöyle erişebilirsiniz:
 * val recorder = LocalRecorder.current
 */
val LocalRecorder = compositionLocalOf<Recorder> {
    // Eğer provider tanımlanmazsa bu hata fırlatılır
    error("No Recorder provided")
}

/**
 * WindowState'i tüm uygulama boyunca paylaşmak için
 * Böylece herhangi bir ekrandan pencere boyutuna/konumuna erişebiliriz
 */
val LocalWindowState = compositionLocalOf<WindowState> {
    error("No WindowState provided")
}

val LocalComposeWindow = compositionLocalOf<java.awt.Window?> { null }

/**
 * Window control callbacks
 */
data class WindowControl(
    val onMinimizeToTray: () -> Unit = {},
    val hideMainWindow: () -> Unit = {},
    val showMainWindow: () -> Unit = {}
)

val LocalWindowControl = compositionLocalOf { WindowControl() }

val LocalRecordingService = compositionLocalOf<RecordingService> {
    error("No RecordingService provided")
}

/**
 * Ana uygulama composable fonksiyonu
 * @param windowState: Pencere durumu (boyut, konum vb.) bilgilerini tutar
 * @param onMinimizeToTray: Callback to minimize window to system tray
 *
 * @Composable anotasyonu: Bu fonksiyonun UI oluşturduğunu belirtir
 */
@Composable
fun App(
    windowState: WindowState,
    onMinimizeToTray: () -> Unit = {},
    onHideMainWindow: () -> Unit = {},
    onShowMainWindow: () -> Unit = {}
) {
    // ===== 1. DI ile StateRepository'yi al =====
    val stateRepository = koinInject<StateRepository>()
    val scope = rememberCoroutineScope()

    // ===== 2. App state'i dinle =====
    val appState by stateRepository.state.collectAsState()

    // ===== 3. İlk yüklemede state'i initialize et =====
    LaunchedEffect(Unit) {
        if (appState is AppState.Initializing) {
            stateRepository.initialize(
                settings = AppSettings(),
                recentRecordings = emptyList()
            )
        }
    }

    // ===== 4. RECORDER ve SERVICE SAĞLAMA (DI üzerinden tekil instance) =====
    // UI ile RecordingService aynı Recorder'ı paylaşsın diye Koin'den alınır
    val recorder = koinInject<Recorder>()
    val recordingService = koinInject<RecordingService>()

    // Get the current window reference
    val currentWindow = remember {
        java.awt.Window.getWindows().firstOrNull()
    }

    // ===== 5. UI AĞACI OLUŞTURMA =====
    /**
     * MaterialTheme: Material Design 3 tema sistemini uygular
     * Tüm alt componentler otomatik olarak tema renklerini/stillerini alır
     */
    club.ozgur.gifland.ui.theme.ThemeProvider {
        Box(modifier = Modifier.fillMaxSize()) {
            // CompositionLocal providers
            CompositionLocalProvider(
                LocalRecorder provides recorder,
                LocalRecordingService provides recordingService,
                LocalWindowState provides windowState,
                LocalComposeWindow provides currentWindow,
                LocalWindowControl provides WindowControl(
                    onMinimizeToTray = onMinimizeToTray,
                    hideMainWindow = onHideMainWindow,
                    showMainWindow = onShowMainWindow
                )
            ) {
                Navigator(MainScreenCompact) { navigator ->
                    FadeTransition(navigator)
                }
            }

            // ===== 6. OVERLAY COMPONENTS =====
            val currentState = appState
            val showQuickPanel = currentState is AppState.Idle && currentState.isQuickPanelVisible
            QuickAccessPanel(
                visible = showQuickPanel,
                onDismiss = {
                    scope.launch { stateRepository.toggleQuickPanel() }
                }
            )

            // Recording overlay disabled to unify UI with RecordingScreen
            // ContextualActionMenu removed; main window RecordingScreen will be used
        }
    }
}

/**
 * ÖZET VE İPUÇLARI:
 *
 * 1. Bu dosya uygulamanın kalbi - tüm state yönetimi ve navigasyon burada başlıyor
 *
 * 2. CompositionLocal kullanım örneği (herhangi bir Composable'da):
 *    @Composable
 *    fun MyScreen() {
 *        val recorder = LocalRecorder.current
 *        Button(onClick = { recorder.startRecording() }) {
 *            Text("Kayıt Başlat")
 *        }
 *    }
 *
 * 3. State Flow pattern:
 *    Recorder -> StateFlow yayar -> collectAsState() ile dinle -> UI güncelle
 *
 * 4. Navigation örneği:
 *    navigator.push(NewScreen())      // İleri git
 *    navigator.pop()                  // Geri dön
 *    navigator.replace(AnotherScreen()) // Mevcut ekranı değiştir
 *
 * 5. Window yönetimi:
 *    WindowResizeEffect kayıt sırasında pencereyi otomatik küçültür/büyütür
 *    Bu UX için önemli - kullanıcı kayıt yaparken pencere engel olmasın!
 */

