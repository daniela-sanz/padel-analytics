package com.tfg.wearableapp.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun PlayerProfileDialog(
    profile: PlayerProfileUiState,
    onDismiss: () -> Unit,
    onUpdateAthleteName: (String) -> Unit,
    onUpdateSex: (String) -> Unit,
    onUpdateDominantHand: (String) -> Unit,
    onUpdateLevel: (String) -> Unit,
    onUpdateNotes: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Perfil del jugador",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(
                modifier = Modifier.padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                OutlinedTextField(
                    value = profile.athleteName,
                    onValueChange = onUpdateAthleteName,
                    label = { Text("Nombre o alias") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                ProfileChoiceRow(
                    title = "Sexo",
                    options = listOf("No definido", "Femenino", "Masculino"),
                    selected = profile.sex,
                    onSelect = onUpdateSex,
                )
                ProfileChoiceRow(
                    title = "Mano dominante",
                    options = listOf("Derecha", "Izquierda", "Ambas"),
                    selected = profile.dominantHand,
                    onSelect = onUpdateDominantHand,
                )
                ProfileChoiceRow(
                    title = "Nivel",
                    options = listOf("Iniciacion", "Intermedio", "Avanzado"),
                    selected = profile.level,
                    onSelect = onUpdateLevel,
                )
                OutlinedTextField(
                    value = profile.notes,
                    onValueChange = onUpdateNotes,
                    label = { Text("Notas de jugador") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Cerrar")
            }
        },
    )
}

@Composable
private fun ProfileChoiceRow(
    title: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(options) { option ->
                if (option == selected) {
                    Button(
                        onClick = { onSelect(option) },
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Text(option)
                    }
                } else {
                    OutlinedButton(
                        onClick = { onSelect(option) },
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Text(option)
                    }
                }
            }
        }
    }
}
