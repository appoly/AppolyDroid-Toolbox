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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import uk.co.appoly.droid.util.DateHelper
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateHelperDemoScreen(navController: NavController) {
	var inputServerTimestamp by remember { mutableStateOf("2025-09-16T14:30:00.000000Z") }
	var inputDate by remember { mutableStateOf("2025-09-16") }
	var parsedInstant by remember { mutableStateOf<Instant?>(null) }
	var formattedInstant by remember { mutableStateOf<String?>(null) }
	var parsedDate by remember { mutableStateOf<LocalDate?>(null) }
	var formattedDate by remember { mutableStateOf<String?>(null) }

	Scaffold(
		topBar = {
			TopAppBar(
				title = { Text("DateHelperUtil Demo") },
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
			verticalArrangement = Arrangement.spacedBy(16.dp)
		) {
			Text(
				text = "DateHelperUtil Demo",
				style = MaterialTheme.typography.headlineMedium
			)

			Text(
				text = "Demonstrates date/time formatting, parsing, and utility functions",
				style = MaterialTheme.typography.bodyLarge
			)

			// Current time utilities
			Card(modifier = Modifier.fillMaxWidth()) {
				Column(modifier = Modifier.padding(16.dp)) {
					Text(
						text = "Current Time Utilities",
						style = MaterialTheme.typography.titleMedium
					)
					Spacer(modifier = Modifier.height(8.dp))

					val currentInstant = Instant.now()
					val currentZoned = DateHelper.nowAsUTC()
					val currentLocal = LocalDateTime.now()

					Text("Current Instant: $currentInstant")
					Text("Current UTC: $currentZoned")
					Text("Current Local: $currentLocal")
					Text("Server timestamp (Instant): ${DateHelper.formatServerTimestamp(currentInstant)}")
					Text("Server timestamp (Zoned): ${DateHelper.formatServerTimestamp(currentZoned)}")
					Text("Local as file: ${currentLocal.format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss.SSS"))}")
				}
			}

			// Server timestamps — the recommended path
			Card(modifier = Modifier.fillMaxWidth()) {
				Column(modifier = Modifier.padding(16.dp)) {
					Text(
						text = "Server Timestamps (Recommended)",
						style = MaterialTheme.typography.titleMedium
					)
					Spacer(modifier = Modifier.height(4.dp))
					Text(
						text = "Routes through Instant — UTC enforced at the type level. Use this for any wire I/O.",
						style = MaterialTheme.typography.bodySmall
					)
					Spacer(modifier = Modifier.height(8.dp))

					OutlinedTextField(
						value = inputServerTimestamp,
						onValueChange = { inputServerTimestamp = it },
						label = { Text("Server Timestamp String") },
						modifier = Modifier.fillMaxWidth()
					)

					Row(
						modifier = Modifier.fillMaxWidth(),
						horizontalArrangement = Arrangement.spacedBy(8.dp)
					) {
						Button(
							onClick = {
								parsedInstant = DateHelper.parseServerInstant(inputServerTimestamp)
								formattedInstant = DateHelper.formatServerTimestamp(parsedInstant)
							},
							modifier = Modifier.weight(1f)
						) {
							Text("Parse & Format")
						}

						Button(
							onClick = {
								parsedInstant = null
								formattedInstant = null
							},
							modifier = Modifier.weight(1f)
						) {
							Text("Clear")
						}
					}

					if (parsedInstant != null) {
						Text("Parsed Instant: $parsedInstant")
					}
					if (formattedInstant != null) {
						Text("Formatted: $formattedInstant")
					}
				}
			}

			// Date parsing and formatting
			Card(modifier = Modifier.fillMaxWidth()) {
				Column(modifier = Modifier.padding(16.dp)) {
					Text(
						text = "Date Parsing & Formatting",
						style = MaterialTheme.typography.titleMedium
					)
					Spacer(modifier = Modifier.height(8.dp))

					OutlinedTextField(
						value = inputDate,
						onValueChange = { inputDate = it },
						label = { Text("Date String") },
						modifier = Modifier.fillMaxWidth()
					)

					Row(
						modifier = Modifier.fillMaxWidth(),
						horizontalArrangement = Arrangement.spacedBy(8.dp)
					) {
						Button(
							onClick = {
								parsedDate = DateHelper.parseLocalDate(inputDate)
								formattedDate = DateHelper.formatLocalDate(parsedDate)
							},
							modifier = Modifier.weight(1f)
						) {
							Text("Parse & Format")
						}

						Button(
							onClick = {
								parsedDate = null
								formattedDate = null
							},
							modifier = Modifier.weight(1f)
						) {
							Text("Clear")
						}
					}

					if (parsedDate != null) {
						Text("Parsed: $parsedDate")
					}
					if (formattedDate != null) {
						Text("Formatted: $formattedDate")
					}
				}
			}

			// Naive helpers — for genuinely zone-naive use cases
			Card(modifier = Modifier.fillMaxWidth()) {
				Column(modifier = Modifier.padding(16.dp)) {
					Text(
						text = "Naive Helpers (Zone-Naive)",
						style = MaterialTheme.typography.titleMedium
					)
					Spacer(modifier = Modifier.height(4.dp))
					Text(
						text = "Use only for genuinely naive values (date pickers, display labels). " +
							"No UTC enforcement — caller is responsible for zone semantics.",
						style = MaterialTheme.typography.bodySmall
					)
					Spacer(modifier = Modifier.height(8.dp))

					val demoDateTime = LocalDateTime.now()
					val demoDate = LocalDate.now()

					Text("formatNaiveDateTime(): ${DateHelper.formatNaiveDateTime(demoDateTime)}")
					Text(
						"parseNaiveDateTime(\"2025-09-16T14:30:00.000000Z\") = " +
							"${DateHelper.parseNaiveDateTime("2025-09-16T14:30:00.000000Z")}"
					)
					Text("formatLocalDate(): ${DateHelper.formatLocalDate(demoDate)}")
					Text("parseLocalDate(\"2025-09-16\") = ${DateHelper.parseLocalDate("2025-09-16")}")
					Text(
						"File-safe format: " +
							demoDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss.SSS"))
					)
				}
			}

			// Usage examples
			Card(modifier = Modifier.fillMaxWidth()) {
				Column(modifier = Modifier.padding(16.dp)) {
					Text(
						text = "Usage Examples",
						style = MaterialTheme.typography.titleMedium
					)
					Spacer(modifier = Modifier.height(8.dp))
					Text(
						text = """
                        // Recommended: server timestamps via Instant (UTC at the type level)
                        val payload = DateHelper.formatServerTimestamp(Instant.now())
                        val instant = DateHelper.parseServerInstant("2025-09-16T14:30:00.000000Z")

                        // Naive helpers for genuinely zone-naive values
                        val text = DateHelper.formatNaiveDateTime(localDateTime)
                        val ldt = DateHelper.parseNaiveDateTime("2025-09-16T14:30:00.000000Z")

                        // Dates (no zone ambiguity to worry about)
                        val date = DateHelper.parseLocalDate("2025-09-16")
                        val formatted = DateHelper.formatLocalDate(date)

                        // Current time in UTC
                        val utcNow = DateHelper.nowAsUTC()

                        // File-safe formatting
                        val fileName = LocalDateTime.now()
                            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss.SSS"))
                        """.trimIndent(),
						style = MaterialTheme.typography.bodySmall,
						modifier = Modifier.fillMaxWidth()
					)
				}
			}
		}
	}
}
