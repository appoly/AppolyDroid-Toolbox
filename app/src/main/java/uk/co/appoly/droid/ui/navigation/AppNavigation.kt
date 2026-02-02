package uk.co.appoly.droid.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import uk.co.appoly.droid.ui.screens.BaseRepoDemoScreen
import uk.co.appoly.droid.ui.screens.DateHelperDemoScreen
import uk.co.appoly.droid.ui.screens.HomeScreen
import uk.co.appoly.droid.ui.screens.MultipartUploadDemoScreen
import uk.co.appoly.droid.ui.screens.PagingDemoScreen
import uk.co.appoly.droid.ui.screens.S3UploaderDemoScreen
import uk.co.appoly.droid.ui.screens.SegmentedControlDemoScreen
import uk.co.appoly.droid.ui.screens.SnackBarDemoScreen
import uk.co.appoly.droid.ui.screens.UiStateDemoScreen

@Composable
fun AppNavigation() {
	val navController = rememberNavController()

	NavHost(navController = navController, startDestination = "home") {
		composable("home") {
			HomeScreen(navController = navController)
		}
		composable("ui_state") {
			UiStateDemoScreen(navController = navController)
		}
		composable("snackbar") {
			SnackBarDemoScreen(navController = navController)
		}
		composable("segmented_control") {
			SegmentedControlDemoScreen(navController = navController)
		}
		composable("date_helper") {
			DateHelperDemoScreen(navController = navController)
		}
		composable("base_repo") {
			BaseRepoDemoScreen(navController = navController)
		}
		composable("paging") {
			PagingDemoScreen(navController = navController)
		}
		composable("s3_uploader") {
			S3UploaderDemoScreen(navController = navController)
		}
		composable("multipart_upload") {
			MultipartUploadDemoScreen(navController = navController)
		}
	}
}