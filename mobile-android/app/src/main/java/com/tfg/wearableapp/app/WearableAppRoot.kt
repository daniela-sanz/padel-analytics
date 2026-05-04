package com.tfg.wearableapp.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tfg.wearableapp.data.session.StoredSessionSummary
import com.tfg.wearableapp.feature.dashboard.DashboardScreen
import com.tfg.wearableapp.feature.connection.ConnectionScreen
import com.tfg.wearableapp.feature.connection.ConnectionUiState
import com.tfg.wearableapp.feature.profile.PlayerProfileDialog
import com.tfg.wearableapp.feature.session.SessionScreen
import com.tfg.wearableapp.feature.session.SessionUiState

private enum class AppSection(
    val label: String,
) {
    Connection("Conexion"),
    Session("Sesion"),
    Dashboard("Dashboard"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WearableAppRoot(
    connectionUiState: ConnectionUiState,
    onStartBleScan: () -> Unit,
    onStopBleScan: () -> Unit,
    onBlePermissionsDenied: () -> Unit,
    onUpdateBlePermissionSnapshot: (Boolean) -> Unit,
    onConnectToBleDevice: (String) -> Unit,
    onDisconnectBle: () -> Unit,
    sessionUiState: SessionUiState,
    onStartRealBleSession: () -> Unit,
    onStopRealBleSession: () -> Unit,
    onRefreshSessions: () -> Unit,
    onSelectSession: (StoredSessionSummary) -> Unit,
    onCloseSessionDetail: () -> Unit,
    onUpdateSessionNameDraft: (String) -> Unit,
    onUpdateAthleteName: (String) -> Unit,
    onUpdateSex: (String) -> Unit,
    onUpdateDominantHand: (String) -> Unit,
    onUpdateLevel: (String) -> Unit,
    onUpdateNotes: (String) -> Unit,
    onOpenLiveDashboard: () -> Unit,
    onOpenSelectedDashboard: () -> Unit,
) {
    var currentSection by rememberSaveable { mutableStateOf(AppSection.Connection) }
    var showProfileDialog by rememberSaveable { mutableStateOf(false) }

    MaterialTheme {
        Scaffold(
            containerColor = TechStyle.bgTop,
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = TechStyle.bgTop,
                        titleContentColor = TechStyle.title,
                        actionIconContentColor = TechStyle.title,
                    ),
                    title = {
                        Text(
                            text = "Padel Analytics",
                            fontWeight = FontWeight.Bold,
                        )
                    },
                    actions = {
                        ProfileBadgeButton(
                            label = sessionUiState.playerProfile.athleteName,
                            onClick = { showProfileDialog = true },
                        )
                    }
                )
            },
            bottomBar = {
                NavigationBar(
                    containerColor = TechStyle.bgTop,
                ) {
                    AppSection.entries.forEach { section ->
                        NavigationBarItem(
                            selected = currentSection == section,
                            onClick = { currentSection = section },
                            icon = { Text(section.label.take(1)) },
                            label = { Text(section.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = TechStyle.title,
                                selectedTextColor = TechStyle.title,
                                indicatorColor = TechStyle.accent.copy(alpha = 0.18f),
                                unselectedIconColor = TechStyle.body,
                                unselectedTextColor = TechStyle.body,
                            ),
                        )
                    }
                }
            }
        ) { innerPadding ->
            when (currentSection) {
                AppSection.Connection -> ConnectionScreen(
                    paddingValues = innerPadding,
                    uiState = connectionUiState,
                    onStartScan = onStartBleScan,
                    onStopScan = onStopBleScan,
                    onPermissionsDenied = onBlePermissionsDenied,
                    onPermissionsSnapshot = onUpdateBlePermissionSnapshot,
                    onConnectToDevice = onConnectToBleDevice,
                    onDisconnect = onDisconnectBle,
                    onGoToSession = { currentSection = AppSection.Session },
                )

                AppSection.Session -> SessionScreen(
                    paddingValues = innerPadding,
                    uiState = sessionUiState,
                    onStartRealBleSession = onStartRealBleSession,
                    onStopRealBleSession = onStopRealBleSession,
                    onRefreshSessions = onRefreshSessions,
                    onSelectSession = onSelectSession,
                    onCloseSessionDetail = onCloseSessionDetail,
                    onUpdateSessionNameDraft = onUpdateSessionNameDraft,
                    onOpenLiveDashboard = {
                        onOpenLiveDashboard()
                        currentSection = AppSection.Dashboard
                    },
                    onOpenSelectedDashboard = {
                        onOpenSelectedDashboard()
                        currentSection = AppSection.Dashboard
                    },
                )

                AppSection.Dashboard -> DashboardScreen(
                    paddingValues = innerPadding,
                    dashboardMode = sessionUiState.dashboardMode,
                    liveSessionDetail = sessionUiState.liveDashboardDetail,
                    selectedSessionDetail = sessionUiState.selectedSessionDetail,
                    sessionSetup = sessionUiState.playerProfile,
                    onGoToSession = { currentSection = AppSection.Session },
                )
            }
        }

        if (showProfileDialog) {
            PlayerProfileDialog(
                profile = sessionUiState.playerProfile,
                onDismiss = { showProfileDialog = false },
                onUpdateAthleteName = onUpdateAthleteName,
                onUpdateSex = onUpdateSex,
                onUpdateDominantHand = onUpdateDominantHand,
                onUpdateLevel = onUpdateLevel,
                onUpdateNotes = onUpdateNotes,
            )
        }
    }
}

@Composable
private fun ProfileBadgeButton(
    label: String,
    onClick: () -> Unit,
) {
    val badgeText = label.trim().take(1).ifBlank { "P" }.uppercase()

    IconButton(onClick = onClick) {
        Surface(
            modifier = Modifier.size(36.dp),
            shape = CircleShape,
            color = TechStyle.accentSecondary,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = badgeText,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
