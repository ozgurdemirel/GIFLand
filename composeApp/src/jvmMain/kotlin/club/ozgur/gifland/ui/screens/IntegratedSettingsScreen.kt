package club.ozgur.gifland.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import club.ozgur.gifland.domain.model.SettingsTab
import club.ozgur.gifland.domain.model.AppSettings
import club.ozgur.gifland.domain.repository.StateRepository
import club.ozgur.gifland.presentation.viewmodel.SettingsViewModel
import club.ozgur.gifland.ui.components.DraggableWindowTitleBar
import kotlinx.coroutines.launch
import org.koin.compose.getKoin

/**
 * Integrated settings screen that connects QualitySettingsScreen with the new architecture.
 * Bridges RecorderSettings with AppSettings through the SettingsViewModel.
 */
data object IntegratedSettingsScreen : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val koin = getKoin()
        val settingsViewModel = remember { koin.get<SettingsViewModel>() }
        val stateRepository = remember { koin.get<StateRepository>() }
        val scope = rememberCoroutineScope()

        val settings by settingsViewModel.settings.collectAsState()

        // Tab selection
        var selectedTab by remember { mutableStateOf(SettingsTab.Appearance) }

        Scaffold { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                DraggableWindowTitleBar(
                    title = "Settings",
                    onClose = {
                        scope.launch {
                            settingsViewModel.applySettings()
                            stateRepository.cancelSettings()
                        }
                        navigator.pop()
                    }
                )

                // Tab Row
                TabRow(
                    selectedTabIndex = selectedTab.ordinal,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    SettingsTab.entries.forEach { tab ->
                        Tab(
                            selected = selectedTab == tab,
                            onClick = { selectedTab = tab },
                            text = { Text(tab.name) }
                        )
                    }
                }

                // Content based on selected tab
                when (selectedTab) {
                    SettingsTab.Appearance -> AppearanceSettingsContent(
                        settings = settings,
                        viewModel = settingsViewModel,
                        modifier = Modifier.fillMaxSize()
                    )

                    SettingsTab.About -> AboutSettingsContent(
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Bottom action buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            // Reset to defaults
                            scope.launch {
                                settingsViewModel.resetToDefaults()
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Reset to Defaults")
                    }

                    Button(
                        onClick = {
                            scope.launch {
                                settingsViewModel.applySettings()
                                stateRepository.cancelSettings()
                                navigator.pop()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Save Settings")
                    }
                }
            }
        }
    }
}

@Composable
private fun AppearanceSettingsContent(
    settings: AppSettings,
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Theme / Appearance
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text("Appearance", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(Modifier.height(6.dp))

                    Text("Theme", fontSize = 13.sp)
                    Spacer(Modifier.height(4.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = settings.theme == club.ozgur.gifland.domain.model.AppTheme.Light,
                            onClick = {
                                scope.launch {
                                    viewModel.changeTheme(club.ozgur.gifland.domain.model.AppTheme.Light)
                                }
                            },
                            label = { Text("Light") }
                        )
                        FilterChip(
                            selected = settings.theme == club.ozgur.gifland.domain.model.AppTheme.Dark,
                            onClick = {
                                scope.launch {
                                    viewModel.changeTheme(club.ozgur.gifland.domain.model.AppTheme.Dark)
                                }
                            },
                            label = { Text("Dark") }
                        )
                        FilterChip(
                            selected = settings.theme == club.ozgur.gifland.domain.model.AppTheme.System,
                            onClick = {
                                scope.launch {
                                    viewModel.changeTheme(club.ozgur.gifland.domain.model.AppTheme.System)
                                }
                            },
                            label = { Text("System") }
                        )
                    }
                }
            }
        }

        // Scrollbar
        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(scrollState),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
        )
    }
}

@Composable
private fun AboutSettingsContent(
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Spacer(Modifier.height(12.dp))

            // App name and version
            Text(
                text = "GIF Land",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Text(
                text = "Version 1.0.11",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(8.dp))

            // Tagline
            Text(
                text = "Coded in Ankara, grown with passion",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )

            // LinkedIn link
            TextButton(
                onClick = {
                    try {
                        java.awt.Desktop.getDesktop().browse(
                            java.net.URI("https://www.linkedin.com/in/ozgdemirel/")
                        )
                    } catch (e: Exception) {
                        // Ignore if browser can't be opened
                    }
                }
            ) {
                Text(
                    text = "linkedin.com/in/ozgdemirel",
                    fontSize = 13.sp,
                    color = Color(0xFF0A66C2) // LinkedIn blue
                )
            }

            Spacer(Modifier.weight(1f))

            // Copyright
            Text(
                text = "© 2025 Ozgur Demirel",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun SwitchPreference(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(description, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}