package uk.co.appoly.droid.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavController) {
	Scaffold(
		topBar = {
			TopAppBar(
				title = { Text("AppolyDroid Showcase") }
			)
		}
	) { paddingValues ->
		Column(
			modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
			verticalArrangement = Arrangement.spacedBy(12.dp),
			horizontalAlignment = Alignment.CenterHorizontally
		) {
			Text(
				text = "Welcome to AppolyDroid Toolbox Showcase",
				style = MaterialTheme.typography.headlineMedium,
				textAlign = TextAlign.Center,
				modifier = Modifier.padding(bottom = 16.dp)
			)

			Text(
				text = "Explore the various library modules and their features",
				style = MaterialTheme.typography.bodyLarge,
				textAlign = TextAlign.Center,
				modifier = Modifier.padding(bottom = 24.dp)
			)

			// Feature buttons
			FeatureButton(
				title = "UI State Management",
				description = "Demonstrate UiState with loading, success, and error states",
				onClick = { navController.navigate("ui_state") }
			)

			FeatureButton(
				title = "App SnackBar",
				description = "Show different snackbar types with custom styling",
				onClick = { navController.navigate("snackbar") }
			)

			FeatureButton(
				title = "Date Helper Utilities",
				description = "Date formatting, parsing, and time zone operations",
				onClick = { navController.navigate("date_helper") }
			)

			FeatureButton(
				title = "Base Repository",
				description = "API calls with standardized error handling",
				onClick = { navController.navigate("base_repo") }
			)

			FeatureButton(
				title = "Paging Extensions",
				description = "LazyList and LazyGrid with paging support",
				onClick = { navController.navigate("paging") }
			)

			FeatureButton(
				title = "S3 Uploader",
				description = "File upload to AWS S3 with progress tracking",
				onClick = { navController.navigate("s3_uploader") }
			)

			FeatureButton(
				title = "Multipart Upload Test",
				description = "Test S3 multipart uploads with pause/resume/recover",
				onClick = { navController.navigate("multipart_upload") }
			)
		}
	}
}

@Composable
private fun FeatureButton(
	title: String,
	description: String,
	onClick: () -> Unit
) {
	OutlinedButton(
		onClick = onClick,
		modifier = Modifier.fillMaxWidth(),
		contentPadding = PaddingValues(16.dp)
	) {
		Column(
			horizontalAlignment = Alignment.Start,
			modifier = Modifier.fillMaxWidth()
		) {
			Text(
				text = title,
				style = MaterialTheme.typography.titleMedium
			)
			Spacer(modifier = Modifier.height(4.dp))
			Text(
				text = description,
				style = MaterialTheme.typography.bodyMedium,
				color = MaterialTheme.colorScheme.onSurfaceVariant
			)
		}
	}
}