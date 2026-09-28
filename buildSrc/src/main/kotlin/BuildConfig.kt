/**
 * Build configuration constants for the AppolyDroid Toolbox library.
 *
 * These values are centralized here and used across all module build.gradle.kts files
 * and the UpdateReadmeVersions task.
 */
object BuildConfig {
    /**
     * The current version of the AppolyDroid Toolbox library.
     * This is used for maven publishing and README version updates.
     */
	const val TOOLBOX_VERSION = "1.10.0"

    /**
     * SDK version configuration for Android modules.
     */
    object Sdk {
        const val COMPILE = 37
        const val TARGET = 37
    }

    /**
     * Minimum SDK versions for different module categories.
     * Grouped by functional area to make it clear which modules share the same minSdk.
     *
     * Each value must be at least the highest minSdk of any AAR on that module's release runtime
     * classpath. Nothing enforces this: a library module builds fine below its dependencies' floor,
     * and the demo app uses [max], so a dependency bump that raises a floor goes unnoticed. The
     * shared floor of 23 comes from androidx itself (core 1.19, appcompat 1.8, Compose 1.12,
     * Paging 3.5). Re-check the AAR manifests whenever a published dependency is bumped.
     */
    object MinSdk {
        /** BaseRepo and its extensions (BaseRepo, BaseRepo-S3Uploader, BaseRepo-Paging, etc.) */
        const val BASE_REPO = 23

        /** UiState module */
        const val UI_STATE = 23

        /** AppSnackBar and AppSnackBar-UiState modules */
        const val APP_SNACK_BAR = 23

        /** ComposeExtensions module */
        const val COMPOSE_EXTENSIONS = 23

        /** SegmentedControl module */
        const val SEGMENTED_CONTROL = 23

        /** DateHelperUtil and its extensions (requires Java 8 time APIs) */
        const val DATE_HELPER = 26

        /** Paging extensions (PagingExtensions, LazyListPaging, LazyGridPaging) */
        const val LAZY_PAGING = 23

        /** S3Uploader module */
        const val S3_UPLOADER = 23

        /** S3Uploader-Multipart and BaseRepo-S3Uploader-Multipart (androidx.work 2.12 requires minSdk 24) */
        const val S3_UPLOADER_MULTIPART = 24

        /** ConnectivityMonitor module (requires newer network APIs) */
        const val CONNECTIVITY_MONITOR = 24

        /** Nav3Navigation module (androidx.navigation3 requires minSdk 24) */
        const val NAV3_NAVIGATION = 24

        /** BarcodeScanner and BarcodeScanner-Camera modules */
        const val BARCODE_SCANNER = 24

		/**
		 * Returns the highest minSdk version among all modules.
		 *
		 * This is used for the overall application minSdk setting.
		 */
		fun max(): Int {
			return listOf(
				BASE_REPO,
				UI_STATE,
				APP_SNACK_BAR,
				COMPOSE_EXTENSIONS,
				SEGMENTED_CONTROL,
				DATE_HELPER,
				LAZY_PAGING,
				S3_UPLOADER,
				S3_UPLOADER_MULTIPART,
				CONNECTIVITY_MONITOR,
				NAV3_NAVIGATION,
				BARCODE_SCANNER
			).max()
		}
    }
}
