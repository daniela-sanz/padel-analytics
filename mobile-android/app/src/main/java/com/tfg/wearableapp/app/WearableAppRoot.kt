package com.tfg.wearableapp.app

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tfg.wearableapp.feature.connection.ConnectionScreen
import com.tfg.wearableapp.feature.session.SessionDemoScreen
import com.tfg.wearableapp.feature.session.SessionDemoUiState
import com.tfg.wearableapp.feature.session.SessionScreen
import com.tfg.wearableapp.feature.session.SessionUiState

private enum class AppSection(
    val label: String,
) {
    Connection("Conexion"),
    Session("Sesion"),
    Debug("Demo"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WearableAppRoot(
    sessionUiState: SessionUiState,
    onStartSimulatedSession: () -> Unit,
    onStopSimulatedSession: () -> Unit,
    onRefreshSessions: () -> Unit,
    demoUiState: SessionDemoUiState,
    onStartDemo: () -> Unit,
    onStopDemo: () -> Unit,
    onPrepareRealBleDemo: () -> Unit,
) {
    var currentSection by rememberSaveable { mutableStateOf(AppSection.Connection) }
    var useSimulatedDemo by rememberSaveable { mutableStateOf(true) }

    MaterialTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "Wearable App MVP",
                            fontWeight = FontWeight.Bold,
                        )
                    }
                )
            },
            bottomBar = {
                NavigationBar {
                    AppSection.entries.forEach { section ->
                        NavigationBarItem(
                            selected = currentSection == section,
                            onClick = { currentSection = section },
                            icon = { Text(section.label.take(1)) },
                            label = { Text(section.label) },
                        )
                    }
                }
            }
        ) { innerPadding ->
            when (currentSection) {
                AppSection.Connection -> ConnectionScreen(
                    paddingValues = innerPadding,
                    onGoToSession = { currentSection = AppSection.Session },
                )

                AppSection.Session -> SessionScreen(
                    paddingValues = innerPadding,
                    uiState = sessionUiState,
                    onStartSimulatedSession = onStartSimulatedSession,
                    onStopSimulatedSession = onStopSimulatedSession,
                    onRefreshSessions = onRefreshSessions,
                    onOpenInternalDemo = { currentSection = AppSection.Debug },
                )

                AppSection.Debug -> SessionDemoScreen(
                    paddingValues = innerPadding,
                    uiState = demoUiState,
                    useSimulatedBle = useSimulatedDemo,
                    onToggleSimulation = { useSimulatedDemo = it },
                    onStartDemo = {
                        if (useSimulatedDemo) {
                            onStartDemo()
                        } else {
                            onPrepareRealBleDemo()
                        }
                    },
                    onStopDemo = onStopDemo,
                )
            }
        }
    }
}
