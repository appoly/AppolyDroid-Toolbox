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
import kotlinx.serialization.Serializable
import uk.co.appoly.droid.nav3.LocalNav3Navigator
import uk.co.appoly.droid.nav3.Nav3Screen

@Serializable
data object HomeScreen : Nav3Screen {
	@OptIn(ExperimentalMaterial3Api::class)
	@Composable
	override fun Content() {
		val navigator = LocalNav3Navigator.current

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

				FeatureButton(
					title = "Nav3 Navigation",
					description = "Voyager-style screens on Navigation 3: push, replace, popUntilRoot, results",
					onClick = { navigator?.push(Nav3NavigationDemoScreen) }
				)

				FeatureButton(
					title = "Nav3 Tabs + transitions",
					description = "TabsNav3Navigator, directional tab slides, spring-slide + parallax for in-tab nav",
					onClick = { navigator?.push(TabsDemoScreen) }
				)

				FeatureButton(
					title = "UI State Management",
					description = "Demonstrate UiState with loading, success, and error states",
					onClick = { navigator?.push(UiStateDemoScreen) }
				)

				FeatureButton(
					title = "App SnackBar",
					description = "Show different snackbar types with custom styling",
					onClick = { navigator?.push(SnackBarDemoScreen) }
				)

				FeatureButton(
					title = "Segmented Control",
					description = "iOS-style segmented control with smooth animations",
					onClick = { navigator?.push(SegmentedControlDemoScreen) }
				)

				FeatureButton(
					title = "Date Helper Utilities",
					description = "Date formatting, parsing, and time zone operations",
					onClick = { navigator?.push(DateHelperDemoScreen) }
				)

				FeatureButton(
					title = "Base Repository",
					description = "API calls with standardized error handling",
					onClick = { navigator?.push(BaseRepoDemoScreen) }
				)

				FeatureButton(
					title = "Appoly JSON Responses",
					description = "Parse GenericResponse / nested-paged envelopes via AppolyBaseRepo",
					onClick = { navigator?.push(AppolyJsonDemoScreen) }
				)

				FeatureButton(
					title = "Date Serialization & Room",
					description = "kotlinx date serializers and Room TypeConverters for java.time",
					onClick = { navigator?.push(DateSerializationRoomDemoScreen) }
				)

				FeatureButton(
					title = "Paging Extensions",
					description = "LazyList and LazyGrid with paging support",
					onClick = { navigator?.push(PagingDemoScreen) }
				)

				FeatureButton(
					title = "S3 Uploader",
					description = "File upload to AWS S3 with progress tracking",
					onClick = { navigator?.push(S3UploaderDemoScreen) }
				)

				FeatureButton(
					title = "Multipart Upload Test",
					description = "Test S3 multipart uploads with pause/resume/recover",
					onClick = { navigator?.push(MultipartUploadDemoScreen) }
				)

				FeatureButton(
					title = "Mock Interceptor",
					description = "OkHttp interceptor DSL for mocking API responses with typed bodies and pagination",
					onClick = { navigator?.push(MockInterceptorDemoScreen) }
				)

				FeatureButton(
					title = "Compose Extensions",
					description = "Serialization-safe MutableState holders and the clipboard copier",
					onClick = { navigator?.push(ComposeExtensionsDemoScreen) }
				)
			}
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
