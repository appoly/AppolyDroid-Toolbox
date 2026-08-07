package uk.co.appoly.droid.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.serialization.Serializable
import uk.co.appoly.droid.nav3.LocalNav3Navigator
import uk.co.appoly.droid.nav3.Nav3Screen
import uk.co.appoly.droid.data.repo.APIFlowState
import uk.co.appoly.droid.ui.viewmodels.BaseRepoDemoViewModel

@Serializable
data object BaseRepoDemoScreen : Nav3Screen {
	@OptIn(ExperimentalMaterial3Api::class)
	@Composable
	override fun Content() {
		val navigator = LocalNav3Navigator.current

	val viewModel: BaseRepoDemoViewModel = viewModel()
	val userState by viewModel.userState.collectAsState()
	val postsState by viewModel.postsState.collectAsState()
	val lastApiResult by viewModel.lastApiResult.collectAsState()

	Scaffold(
		topBar = {
			TopAppBar(
				title = { Text("BaseRepo Demo") },
				navigationIcon = {
					IconButton(onClick = { navigator?.pop() }) {
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
				text = "BaseRepo Demo",
				style = MaterialTheme.typography.headlineMedium
			)

			Text(
				text = "Demonstrates standardized API call handling with different response scenarios",
				style = MaterialTheme.typography.bodyLarge
			)

			// Last API result
			if (lastApiResult.isNotEmpty()) {
				Card(
					modifier = Modifier.fillMaxWidth(),
					colors = CardDefaults.cardColors(
						containerColor = MaterialTheme.colorScheme.surfaceVariant
					)
				) {
					Text(
						text = lastApiResult,
						modifier = Modifier.padding(16.dp),
						style = MaterialTheme.typography.bodyMedium
					)
				}
			}

			// User API calls demo
			Card(modifier = Modifier.fillMaxWidth()) {
				Column(modifier = Modifier.padding(16.dp)) {
					Text(
						text = "User API Calls",
						style = MaterialTheme.typography.titleMedium
					)
					Text(
						text = "Simulates fetching user data with different scenarios",
						style = MaterialTheme.typography.bodyMedium
					)
					Spacer(modifier = Modifier.height(8.dp))

					// State display
					when (val state = userState) {
						is APIFlowState.Loading -> {
							Row(
								verticalAlignment = Alignment.CenterVertically,
								horizontalArrangement = Arrangement.spacedBy(8.dp)
							) {
								CircularProgressIndicator(modifier = Modifier.size(20.dp))
								Text("Loading user data...")
							}
						}

						is APIFlowState.Success -> {
							Column {
								Text("✅ User loaded successfully:")
								Text("ID: ${state.data.id}")
								Text("Name: ${state.data.name}")
								Text("Email: ${state.data.email}")
							}
						}

						is APIFlowState.Error -> {
							Column {
								Text("❌ Error: ${state.message}")
								Text("Response Code: ${state.responseCode}")
							}
						}
					}

					Spacer(modifier = Modifier.height(16.dp))

					// Action buttons
					Row(
						modifier = Modifier.fillMaxWidth(),
						horizontalArrangement = Arrangement.spacedBy(8.dp)
					) {
						OutlinedButton(
							onClick = { viewModel.fetchUserSuccess() },
							modifier = Modifier.weight(1f)
						) {
							Text("Success")
						}
						OutlinedButton(
							onClick = { viewModel.fetchUserError() },
							modifier = Modifier.weight(1f)
						) {
							Text("API Error")
						}
						OutlinedButton(
							onClick = { viewModel.fetchUserNetworkError() },
							modifier = Modifier.weight(1f)
						) {
							Text("Network Error")
						}
					}

					Button(
						onClick = { viewModel.resetUserState() },
						modifier = Modifier.fillMaxWidth()
					) {
						Text("Reset User State")
					}
				}
			}

			// Posts API calls demo
			Card(modifier = Modifier.fillMaxWidth()) {
				Column(modifier = Modifier.padding(16.dp)) {
					Text(
						text = "Posts API Calls",
						style = MaterialTheme.typography.titleMedium
					)
					Text(
						text = "Simulates fetching a list of posts",
						style = MaterialTheme.typography.bodyMedium
					)
					Spacer(modifier = Modifier.height(8.dp))

					// State display
					when (val state = postsState) {
						is APIFlowState.Loading -> {
							Row(
								verticalAlignment = Alignment.CenterVertically,
								horizontalArrangement = Arrangement.spacedBy(8.dp)
							) {
								CircularProgressIndicator(modifier = Modifier.size(20.dp))
								Text("Loading posts...")
							}
						}

						is APIFlowState.Success -> {
							Column {
								Text("✅ Posts loaded successfully:")
								state.data.forEach { post ->
									Text("• ${post.title}", modifier = Modifier.padding(start = 8.dp))
								}
							}
						}

						is APIFlowState.Error -> {
							Column {
								Text("❌ Error: ${state.message}")
								Text("Response Code: ${state.responseCode}")
							}
						}
					}

					Spacer(modifier = Modifier.height(16.dp))

					// Action buttons
					Row(
						modifier = Modifier.fillMaxWidth(),
						horizontalArrangement = Arrangement.spacedBy(8.dp)
					) {
						Button(
							onClick = { viewModel.fetchPostsSuccess() },
							modifier = Modifier.weight(1f)
						) {
							Text("Fetch Posts")
						}
						Button(
							onClick = { viewModel.fetchPostsError() },
							modifier = Modifier.weight(1f)
						) {
							Text("Error Response")
						}
					}

					OutlinedButton(
						onClick = { viewModel.resetPostsState() },
						modifier = Modifier.fillMaxWidth()
					) {
						Text("Reset Posts State")
					}
				}
			}

			// API Result types explanation
			Card(modifier = Modifier.fillMaxWidth()) {
				Column(modifier = Modifier.padding(16.dp)) {
					Text(
						text = "API Result Types",
						style = MaterialTheme.typography.titleMedium
					)
					Spacer(modifier = Modifier.height(8.dp))
					Text(
						text = """
                        • APIResult.Success<T> - Contains successful response data
                        • APIResult.Error - Contains error code and message
                        • APIFlowState.Loading - Loading state for UI
                        • APIFlowState.Success<T> - Success state with data
                        • APIFlowState.Error - Error state with details
                        """.trimIndent(),
						style = MaterialTheme.typography.bodySmall
					)
				}
			}

			// Usage example
			Card(modifier = Modifier.fillMaxWidth()) {
				Column(modifier = Modifier.padding(16.dp)) {
					Text(
						text = "Usage Example",
						style = MaterialTheme.typography.titleMedium
					)
					Spacer(modifier = Modifier.height(8.dp))
					Text(
						text = """
                        // In your repository
                        suspend fun fetchUser(userId: Int): APIResult<UserData> = 
                            doAPICall("fetchUser") {
                                userService.getUser(userId)
                            }
                        
                        // In your ViewModel
                        fun getUserFlow(userId: Int): Flow<APIFlowState<UserData>> = 
                            flow {
                                emit(APIFlowState.Loading)
                                emit(fetchUser(userId).asApiFlowState())
                            }
                        
                        // In your UI
                        when (val state = userState) {
                            is APIFlowState.Loading -> showLoading()
                            is APIFlowState.Success -> showData(state.data)
                            is APIFlowState.Error -> showError(state.message)
                        }
                        """.trimIndent(),
						style = MaterialTheme.typography.bodySmall,
						modifier = Modifier.fillMaxWidth()
					)
				}
			}
		}
	}
}
}
