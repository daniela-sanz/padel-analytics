package com.tfg.wearableapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tfg.wearableapp.app.WearableAppRoot
import com.tfg.wearableapp.feature.connection.ConnectionViewModel
import com.tfg.wearableapp.feature.session.SessionDemoViewModel
import com.tfg.wearableapp.feature.session.SessionViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val connectionViewModel: ConnectionViewModel = viewModel(
                factory = ConnectionViewModel.provideFactory(applicationContext)
            )
            val connectionUiState by connectionViewModel.uiState.collectAsStateWithLifecycle()

            val sessionViewModel: SessionViewModel = viewModel(
                factory = SessionViewModel.provideFactory(applicationContext)
            )
            val sessionUiState by sessionViewModel.uiState.collectAsStateWithLifecycle()

            val demoViewModel: SessionDemoViewModel = viewModel()
            val demoUiState by demoViewModel.uiState.collectAsStateWithLifecycle()

            WearableAppRoot(
                connectionUiState = connectionUiState,
                onStartBleScan = connectionViewModel::startScan,
                onStopBleScan = connectionViewModel::stopScan,
                onBlePermissionsDenied = connectionViewModel::onBlePermissionsDenied,
                onUpdateBlePermissionSnapshot = connectionViewModel::updatePermissionSnapshot,
                onConnectToBleDevice = connectionViewModel::connectToDevice,
                onDisconnectBle = connectionViewModel::disconnect,
                sessionUiState = sessionUiState,
                onStartSimulatedSession = sessionViewModel::startSimulatedSession,
                onStopSimulatedSession = sessionViewModel::stopSimulatedSession,
                onRefreshSessions = sessionViewModel::refreshSavedSessions,
                onSelectSession = sessionViewModel::selectSession,
                onCloseSessionDetail = sessionViewModel::clearSelectedSession,
                onUpdateSessionNameDraft = sessionViewModel::updateSessionNameDraft,
                onUpdateAthleteName = sessionViewModel::updateAthleteName,
                onUpdateSex = sessionViewModel::updateSex,
                onUpdateDominantHand = sessionViewModel::updateDominantHand,
                onUpdateLevel = sessionViewModel::updateLevel,
                onUpdateNotes = sessionViewModel::updateNotes,
                onOpenLiveDashboard = sessionViewModel::openLiveDashboard,
                onOpenSelectedDashboard = sessionViewModel::openSelectedDashboard,
                demoUiState = demoUiState,
                onStartDemo = demoViewModel::startSimulation,
                onStopDemo = demoViewModel::stopSimulation,
                onPrepareRealBleDemo = demoViewModel::prepareRealBleMode,
            )
        }
    }
}
