package uk.co.appoly.droid.ui.screens

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import uk.co.appoly.droid.ui.viewmodels.MockInterceptorDemoViewModel
import uk.co.appoly.droid.ui.viewmodels.RequestResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MockInterceptorDemoScreen(navController: NavController) {
	val viewModel: MockInterceptorDemoViewModel = viewModel()
	val results by viewModel.results.collectAsState()

	Scaffold(
		topBar = {
			TopAppBar(
				title = { Text("Mock Interceptor Demo") },
				navigationIcon = {
					IconButton(onClick = { navController.navigateUp() }) {
						Text("\u2190")
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
			// Card 1: Core DSL
			Card(modifier = Modifier.fillMaxWidth()) {
				Column(modifier = Modifier.padding(16.dp)) {
					Text(
						text = "Core DSL",
						style = MaterialTheme.typography.titleMedium
					)
					Text(
						text = "Route matching, path params, request body, groups, status helpers",
						style = MaterialTheme.typography.bodyMedium
					)
					Spacer(modifier = Modifier.height(8.dp))

					ButtonRow {
						DemoButton("Hello") { viewModel.fetchHello() }
						DemoButton("User 1") { viewModel.fetchUserById(1) }
						DemoButton("User 99") { viewModel.fetchUserById(99) }
					}
					ButtonRow {
						DemoButton("Login \u2713") { viewModel.postLogin(asAdmin = true) }
						DemoButton("Login \u2717") { viewModel.postLogin(asAdmin = false) }
					}
					ButtonRow {
						DemoButton("Grouped Items") { viewModel.fetchGroupedItems() }
						DemoButton("Grouped Item 3") { viewModel.fetchGroupedItem(3) }
					}
					DemoButton("Delete Item (204)") { viewModel.deleteItem(42) }
				}
			}

			// Card 2: Typed Serialization
			Card(modifier = Modifier.fillMaxWidth()) {
				Column(modifier = Modifier.padding(16.dp)) {
					Text(
						text = "Typed Serialization",
						style = MaterialTheme.typography.titleMedium
					)
					Text(
						text = "jsonBody<T>() via kotlinx-serialization",
						style = MaterialTheme.typography.bodyMedium
					)
					Spacer(modifier = Modifier.height(8.dp))

					DemoButton("Typed User List") { viewModel.fetchTypedUsers() }
				}
			}

			// Card 3: Appoly JSON Envelope
			Card(modifier = Modifier.fillMaxWidth()) {
				Column(modifier = Modifier.padding(16.dp)) {
					Text(
						text = "Appoly JSON Envelope",
						style = MaterialTheme.typography.titleMedium
					)
					Text(
						text = "successBody, successMessage, errorBody, pagedBody",
						style = MaterialTheme.typography.bodyMedium
					)
					Spacer(modifier = Modifier.height(8.dp))

					ButtonRow {
						DemoButton("Success List") { viewModel.fetchAppolyUsers() }
						DemoButton("Delete (Message)") { viewModel.deleteAppolyUser(7) }
					}
					DemoButton("Error 404") { viewModel.fetchAppolyError() }
					ButtonRow {
						DemoButton("Page 1") { viewModel.fetchPagedProducts(1) }
						DemoButton("Page 2") { viewModel.fetchPagedProducts(2) }
						DemoButton("Page 5") { viewModel.fetchPagedProducts(5) }
					}
					DemoButton("Empty Page") { viewModel.fetchEmptyPage() }
				}
			}

			// Card 4: Retrofit mockApi()
			Card(modifier = Modifier.fillMaxWidth()) {
				Column(modifier = Modifier.padding(16.dp)) {
					Text(
						text = "Retrofit mockApi()",
						style = MaterialTheme.typography.titleMedium
					)
					Text(
						text = "Auto-routes from @GET/@POST annotations",
						style = MaterialTheme.typography.bodyMedium
					)
					Spacer(modifier = Modifier.height(8.dp))

					ButtonRow {
						DemoButton("List Users") { viewModel.retrofitListUsers() }
						DemoButton("Get User 5") { viewModel.retrofitGetUser(5) }
					}
					DemoButton("Create User") { viewModel.retrofitCreateUser() }
				}
			}

			// Results section
			if (results.isNotEmpty()) {
				Row(
					modifier = Modifier.fillMaxWidth(),
					horizontalArrangement = Arrangement.SpaceBetween
				) {
					Text(
						text = "Results",
						style = MaterialTheme.typography.titleMedium
					)
					OutlinedButton(onClick = { viewModel.clearResults() }) {
						Text("Clear")
					}
				}

				results.forEach { result ->
					ResultCard(result)
				}
			}
		}
	}
}

@Composable
private fun ButtonRow(content: @Composable () -> Unit) {
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.horizontalScroll(rememberScrollState()),
		horizontalArrangement = Arrangement.spacedBy(8.dp)
	) {
		content()
	}
}

@Composable
private fun DemoButton(label: String, onClick: () -> Unit) {
	OutlinedButton(onClick = onClick) {
		Text(label)
	}
}

@Composable
private fun ResultCard(result: RequestResult) {
	Card(
		modifier = Modifier.fillMaxWidth(),
		colors = CardDefaults.cardColors(
			containerColor = MaterialTheme.colorScheme.surfaceVariant
		)
	) {
		Column(modifier = Modifier.padding(12.dp)) {
			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.SpaceBetween
			) {
				Text(
					text = result.label,
					style = MaterialTheme.typography.titleSmall,
					fontWeight = FontWeight.Bold
				)
				Text(
					text = "${result.statusCode}",
					style = MaterialTheme.typography.labelMedium,
					color = if (result.statusCode in 200..299)
						MaterialTheme.colorScheme.primary
					else
						MaterialTheme.colorScheme.error
				)
			}
			Text(
				text = "${result.method} ${result.url}",
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant
			)
			if (result.responseBody.isNotEmpty()) {
				Spacer(modifier = Modifier.height(4.dp))
				Text(
					text = result.responseBody,
					style = MaterialTheme.typography.bodySmall,
					fontFamily = FontFamily.Monospace
				)
			}
		}
	}
}
