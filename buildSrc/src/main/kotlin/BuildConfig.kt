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
	const val TOOLBOX_VERSION = "1.2.0-beta05"

    /**
     * SDK version configuration for Android modules.
     */
    object Sdk {
        const val COMPILE = 36
        const val TARGET = 36
    }

    /**
     * Minimum SDK versions for different module categories.
     * Grouped by functional area to make it clear which modules share the same minSdk.
     */
    object MinSdk {
        /** BaseRepo and its extensions (BaseRepo, BaseRepo-S3Uploader, BaseRepo-Paging, etc.) */
        const val BASE_REPO = 21

        /** UiState module */
        const val UI_STATE = 21

        /** AppSnackBar and AppSnackBar-UiState modules */
        const val APP_SNACK_BAR = 21

        /** ComposeExtensions module */
        const val COMPOSE_EXTENSIONS = 21

        /** DateHelperUtil and its extensions (requires Java 8 time APIs) */
        const val DATE_HELPER = 26

        /** Paging extensions (PagingExtensions, LazyListPaging, LazyGridPaging) */
        const val LAZY_PAGING = 21

        /** S3Uploader and S3Uploader-Multipart modules */
        const val S3_UPLOADER = 21

        /** ConnectivityMonitor module (requires newer network APIs) */
        const val CONNECTIVITY_MONITOR = 24

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
				DATE_HELPER,
				LAZY_PAGING,
				S3_UPLOADER,
				CONNECTIVITY_MONITOR
			).max()
		}
    }
}
