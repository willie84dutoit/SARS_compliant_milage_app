package com.mileagetracker.app.ui.classification

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mileagetracker.app.domain.model.TripClassification

/** Trip Classification prompt shell (brief §7 #3). */
@Composable
fun TripClassificationScreen(
    tripId: String,
    onClassificationSaved: () -> Unit,
    viewModel: TripClassificationViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold { contentPadding ->
        Column(modifier = Modifier.padding(contentPadding).fillMaxSize().padding(24.dp)) {
            Text("Was this trip Work or Private?")

            Button(onClick = { viewModel.onClassificationSelected(TripClassification.WORK) }) {
                Text("Work")
            }
            Button(onClick = { viewModel.onClassificationSelected(TripClassification.PRIVATE) }) {
                Text("Private")
            }

            if (uiState.selectedClassification == TripClassification.WORK) {
                OutlinedTextField(
                    value = uiState.businessReasonText,
                    onValueChange = viewModel::onBusinessReasonChanged,
                    label = { Text("Business reason") },
                )
            }

            uiState.validationErrorMessage?.let { errorMessage ->
                Text(errorMessage)
            }

            Button(onClick = { viewModel.onSaveClassification(onClassificationSaved) }) {
                Text("Save")
            }
        }
    }
}
