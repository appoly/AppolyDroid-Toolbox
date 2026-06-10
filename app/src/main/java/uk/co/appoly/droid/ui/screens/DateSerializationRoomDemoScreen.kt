package uk.co.appoly.droid.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import uk.co.appoly.droid.ui.viewmodels.DateSerializationRoomDemoViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateSerializationRoomDemoScreen(navController: NavController) {
	val viewModel: DateSerializationRoomDemoViewModel = viewModel()
	val serializedJson by viewModel.serializedJson.collectAsState()
	val decoded by viewModel.decoded.collectAsState()
	val notes by viewModel.notes.collectAsState()

	Scaffold(
		topBar = {
			TopAppBar(
				title = { Text("Date Serialization & Room") },
				navigationIcon = {
					IconButton(onClick = { navController.navigateUp() }) {
						Text("←")
					}
				}
			)
		}
	) { paddingValues ->
		Column(
			modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
			verticalArrangement = Arrangement.spacedBy(16.dp)
		) {
			// --- DateHelperUtil-Serialization ---
			Card(modifier = Modifier.fillMaxWidth()) {
				Column(modifier = Modifier.padding(16.dp)) {
					Text("DateHelperUtil-Serialization", style = MaterialTheme.typography.titleMedium)
					Text(
						text = "@Serializable(with = …) KSerializers encode/decode java.time types as JSON.",
						style = MaterialTheme.typography.bodyMedium
					)
					Spacer(Modifier.height(8.dp))
					Button(onClick = { viewModel.runSerializationRoundTrip() }) {
						Text("Encode & decode an EventDto")
					}
					if (serializedJson.isNotEmpty()) {
						Spacer(Modifier.height(8.dp))
						Text("Encoded JSON:", style = MaterialTheme.typography.labelMedium)
						Text(
							text = serializedJson,
							style = MaterialTheme.typography.bodySmall,
							fontFamily = FontFamily.Monospace
						)
					}
					decoded?.let { event ->
						Spacer(Modifier.height(8.dp))
						Text("Decoded back:", style = MaterialTheme.typography.labelMedium)
						Text("• day = ${event.day}", style = MaterialTheme.typography.bodySmall)
						Text("• startsAt = ${event.startsAt}", style = MaterialTheme.typography.bodySmall)
						Text("• zoned = ${event.zoned}", style = MaterialTheme.typography.bodySmall)
						Text("• createdAt = ${event.createdAt}", style = MaterialTheme.typography.bodySmall)
					}
				}
			}

			// --- DateHelperUtil-Room ---
			Card(modifier = Modifier.fillMaxWidth()) {
				Column(modifier = Modifier.padding(16.dp)) {
					Text("DateHelperUtil-Room", style = MaterialTheme.typography.titleMedium)
					Text(
						text = "DBDateConverters persist LocalDateTime / LocalDate through Room. " +
							"Rows survive across app restarts.",
						style = MaterialTheme.typography.bodyMedium
					)
					Spacer(Modifier.height(8.dp))
					Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
						Button(onClick = { viewModel.addNote() }) { Text("Add note") }
						OutlinedButton(onClick = { viewModel.clearNotes() }) { Text("Clear") }
					}
					Spacer(Modifier.height(8.dp))
					if (notes.isEmpty()) {
						Text("No notes persisted yet.", style = MaterialTheme.typography.bodySmall)
					} else {
						notes.forEach { note ->
							Text(
								text = "#${note.id} · created ${note.createdAt} · due ${note.dueDate}",
								style = MaterialTheme.typography.bodySmall,
								fontFamily = FontFamily.Monospace
							)
						}
					}
				}
			}
		}
	}
}
