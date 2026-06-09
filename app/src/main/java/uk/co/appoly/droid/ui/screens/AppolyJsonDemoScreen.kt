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
import uk.co.appoly.droid.data.repo.APIFlowState
import uk.co.appoly.droid.ui.viewmodels.AppolyJsonDemoViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppolyJsonDemoScreen(navController: NavController) {
	val viewModel: AppolyJsonDemoViewModel = viewModel()
	val usersState by viewModel.usersState.collectAsState()
	val productsState by viewModel.productsState.collectAsState()
	val productsPage by viewModel.productsPage.collectAsState()
	val log by viewModel.log.collectAsState()

	Scaffold(
		topBar = {
			TopAppBar(
				title = { Text("Appoly JSON Demo") },
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
			Card(modifier = Modifier.fillMaxWidth()) {
				Column(modifier = Modifier.padding(16.dp)) {
					Text(
						text = "BaseRepo-AppolyJson + BaseRepo-Paging-AppolyJson",
						style = MaterialTheme.typography.titleMedium
					)
					Text(
						text = "A real AppolyBaseRepo parses Appoly's standardized response envelopes into " +
							"APIResults. Responses are served offline by the MockInterceptor module.",
						style = MaterialTheme.typography.bodyMedium
					)
				}
			}

			// --- GenericResponse<List<T>> ---
			Card(modifier = Modifier.fillMaxWidth()) {
				Column(modifier = Modifier.padding(16.dp)) {
					Text("GenericResponse<List<User>>", style = MaterialTheme.typography.titleSmall)
					Spacer(Modifier.height(8.dp))
					Button(onClick = { viewModel.fetchUsers() }) { Text("Fetch users") }
					Spacer(Modifier.height(8.dp))
					when (val state = usersState) {
						null -> Text("Idle", style = MaterialTheme.typography.bodySmall)
						is APIFlowState.Loading -> Text("Loading…")
						is APIFlowState.Success -> Column {
							state.data.forEach { user ->
								Text("• ${user.name} <${user.email}>", style = MaterialTheme.typography.bodyMedium)
							}
						}

						is APIFlowState.Error -> Text(
							"❌ ${state.message} (${state.responseCode})",
							color = MaterialTheme.colorScheme.error
						)
					}
				}
			}

			// --- GenericNestedPagedResponse<T> ---
			Card(modifier = Modifier.fillMaxWidth()) {
				Column(modifier = Modifier.padding(16.dp)) {
					Text("GenericNestedPagedResponse<Product>", style = MaterialTheme.typography.titleSmall)
					Spacer(Modifier.height(8.dp))
					Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
						OutlinedButton(onClick = { viewModel.previousProductsPage() }) { Text("◀ Prev") }
						Button(onClick = { viewModel.fetchProducts(productsPage) }) { Text("Load page $productsPage") }
						OutlinedButton(onClick = { viewModel.nextProductsPage() }) { Text("Next ▶") }
					}
					Spacer(Modifier.height(8.dp))
					when (val state = productsState) {
						null -> Text("Idle", style = MaterialTheme.typography.bodySmall)
						is APIFlowState.Loading -> Text("Loading…")
						is APIFlowState.Success -> Column {
							Text(
								"Page ${state.data.currentPage}/${state.data.lastPage} · ${state.data.total} total",
								style = MaterialTheme.typography.labelMedium,
								color = MaterialTheme.colorScheme.primary
							)
							state.data.data.forEach { product ->
								Text(
									"• ${product.name} — $${String.format("%.2f", product.price)}",
									style = MaterialTheme.typography.bodyMedium
								)
							}
						}

						is APIFlowState.Error -> Text(
							"❌ ${state.message} (${state.responseCode})",
							color = MaterialTheme.colorScheme.error
						)
					}
				}
			}

			// --- Error envelope + BaseResponse ---
			Card(modifier = Modifier.fillMaxWidth()) {
				Column(modifier = Modifier.padding(16.dp)) {
					Text("Error envelope & BaseResponse", style = MaterialTheme.typography.titleSmall)
					Spacer(Modifier.height(8.dp))
					Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
						OutlinedButton(onClick = { viewModel.triggerErrorEnvelope() }) { Text("Trigger 404") }
						OutlinedButton(onClick = { viewModel.deleteUser(2) }) { Text("Delete user (BaseResponse)") }
					}
				}
			}

			// --- Outcome log ---
			Card(modifier = Modifier.fillMaxWidth()) {
				Column(modifier = Modifier.padding(16.dp)) {
					Text("Last parsed outcome", style = MaterialTheme.typography.titleSmall)
					Spacer(Modifier.height(8.dp))
					Text(
						text = log,
						style = MaterialTheme.typography.bodyMedium,
						fontFamily = FontFamily.Monospace
					)
				}
			}
		}
	}
}
