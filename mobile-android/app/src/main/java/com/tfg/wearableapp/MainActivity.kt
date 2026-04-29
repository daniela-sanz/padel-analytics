package com.tfg.wearableapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tfg.wearableapp.app.WearableAppRoot
import com.tfg.wearableapp.feature.session.SessionDemoViewModel
import com.tfg.wearableapp.feature.session.SessionViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val sessionViewModel: SessionViewModel = viewModel(
                factory = SessionViewModel.provideFactory(applicationContext)
            )
            val sessionUiState by sessionViewModel.uiState.collectAsStateWithLifecycle()

            val demoViewModel: SessionDemoViewModel = viewModel()
            val demoUiState by demoViewModel.uiState.collectAsStateWithLifecycle()

            WearableAppRoot(
                sessionUiState = sessionUiState,
                onStartSimulatedSession = sessionViewModel::startSimulatedSession,
                onStopSimulatedSession = sessionViewModel::stopSimulatedSession,
                onRefreshSessions = sessionViewModel::refreshSavedSessions,
                demoUiState = demoUiState,
                onStartDemo = demoViewModel::startSimulation,
                onStopDemo = demoViewModel::stopSimulation,
                onPrepareRealBleDemo = demoViewModel::prepareRealBleMode,
            )
        }
    }
}
