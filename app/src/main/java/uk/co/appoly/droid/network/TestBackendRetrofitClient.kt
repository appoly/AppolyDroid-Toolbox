package uk.co.appoly.droid.network

import com.duck.flexilogger.LoggingLevel
import com.duck.flexilogger.okhttp.FlexiLogHttpLoggingInterceptorLogger
import com.skydoves.sandwich.retrofit.adapters.ApiResponseCallAdapterFactory
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import uk.co.appoly.droid.Log
import uk.co.appoly.droid.data.remote.BaseRetrofitClient
import java.util.concurrent.TimeUnit

/**
 * BaseRetrofitClient implementation for the test multipart upload server.
 *
 * ## Server Information
 * - **Host:** `https://multipart-uploader.on-forge.com`
 * - **Purpose:** Test backend for demonstrating multipart upload functionality
 * - **Source:** [https://github.com/appoly/s3-uploader](https://github.com/appoly/s3-uploader)
 *
 * ## Features Demonstrated
 * This client demonstrates proper Retrofit client setup following the BaseRepo pattern:
 *
 * 1. **Token Injection:** Uses an [AuthInterceptor] to automatically attach Bearer tokens
 *    to all authenticated requests. The token is provided via a lambda, allowing the token
 *    to change dynamically without recreating the client.
 *
 * 2. **Kotlinx Serialization:** Uses `kotlinx.serialization` for JSON parsing, which is
 *    the preferred approach for Kotlin projects.
 *
 * 3. **Sandwich Integration:** Includes the [ApiResponseCallAdapterFactory] to wrap all
 *    API responses in [com.skydoves.sandwich.ApiResponse], providing consistent error handling.
 *
 * 4. **FlexiLogger Integration:** Uses [FlexiLogHttpLoggingInterceptorLogger] for HTTP
 *    logging integration with the FlexiLogger library.
 *
 * 5. **Timeout Configuration:** Sets appropriate timeouts for different network operations:
 *    - Connect: 30 seconds
 *    - Read: 30 seconds
 *    - Write: 60 seconds (longer for uploads)
 *
 * ## Usage
 * ```kotlin
 * // Create with a token provider that returns the current auth token
 * val client = TestBackendRetrofitClient(
 *     tokenProvider = { authRepository.currentToken }
 * )
 *
 * // Use in a repository
 * class MyRepository(
 *     getRetrofitClient: () -> BaseRetrofitClient
 * ) : GenericBaseRepo(getRetrofitClient)
 * ```
 *
 * @property tokenProvider Lambda that returns the current auth token, or null if not authenticated.
 *                         This is called on every request, allowing dynamic token updates.
 *
 * @see BaseRetrofitClient
 * @see AuthInterceptor
 */
class TestBackendRetrofitClient(
    private val tokenProvider: () -> String?
) : BaseRetrofitClient {

    companion object {
        /**
         * Base URL for the test backend server.
         *
         * This server provides:
         * - `/api/login` - Authentication endpoint
         * - `/api/s3/multipart/x` - Multipart upload endpoints
         */
        const val BASE_URL = "https://multipart-uploader.on-forge.com/"

        /**
         * Base URL for multipart upload API endpoints.
         *
         * Use with [uk.co.appoly.droid.s3upload.multipart.network.model.MultipartApiUrls.fromBaseUrl]
         * to generate all required multipart upload URLs.
         *
         * ## Endpoints
         * - `POST /initiate` - Start a new multipart upload
         * - `GET /presigned-url` - Get presigned URL for part upload
         * - `POST /complete` - Complete the multipart upload
         * - `POST /abort` - Abort an in-progress upload
         */
        const val MULTIPART_BASE_URL = "${BASE_URL}api/s3/multipart"
    }

    /**
     * JSON configuration for serialization/deserialization.
     *
     * Configuration:
     * - `ignoreUnknownKeys`: Allows the server to add new fields without breaking the client
     * - `useAlternativeNames`: Supports alternative field names for deserialization
     * - `explicitNulls`: Omits null values from serialized output
     * - `encodeDefaults`: Includes properties with default values in serialized output
     * - `prettyPrint`: Enabled for debug builds to improve readability in logs
     */
    override val json: Json = Json {
        ignoreUnknownKeys = true
        useAlternativeNames = true
        explicitNulls = false
        encodeDefaults = true
        prettyPrint = true // For demo purposes - disable in production
    }

    /**
     * Base OkHttpClient instance used as a template for building configured clients.
     */
    private val okHttpClient by lazy { OkHttpClient() }

    /**
     * Cached Retrofit instance. Created lazily on first use.
     */
    private var retrofit: Retrofit? = null

    /**
     * Authentication interceptor that adds Bearer tokens and standard headers to requests.
     *
     * Uses the [tokenProvider] to get the current token for each request.
     * If no token is available, the Authorization header is not added.
     *
     * Also adds standard headers:
     * - Accept: application/json
     * - Content-Type: application/json
     */
    private val headerInterceptor: Interceptor
        get() = Interceptor { chain ->
            chain.proceed(
                chain.request().newBuilder().apply {
                    val authToken = tokenProvider()
                    if (!authToken.isNullOrBlank()) {
                        addHeader("Authorization", "Bearer $authToken")
                    }
                    addStandardHeaders()
                }.build()
            )
        }

    /**
     * Adds standard HTTP headers to the request.
     *
     * @return The builder for method chaining
     */
    private fun Request.Builder.addStandardHeaders(): Request.Builder =
        addHeader("Accept", "application/json")
            .addHeader("Content-Type", "application/json")

    /**
     * Gets or creates the Retrofit instance.
     *
     * Uses double-checked locking to ensure thread-safe lazy initialization.
     *
     * @return The configured Retrofit instance
     */
    private fun getRetrofitClient(): Retrofit {
        return retrofit ?: synchronized(this) {
            retrofit ?: buildRetrofitClient().also { retrofit = it }
        }
    }

    /**
     * Builds the Retrofit instance with all required configuration.
     *
     * Uses:
     * - Kotlinx Serialization converter for JSON handling
     * - Sandwich's [ApiResponseCallAdapterFactory] for response wrapping
     * - Custom OkHttpClient with interceptors
     *
     * @return The configured Retrofit instance
     */
    private fun buildRetrofitClient(): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(
                json.asConverterFactory("application/json; charset=UTF-8".toMediaType())
            )
            .client(buildOkHttpClient())
            .addCallAdapterFactory(ApiResponseCallAdapterFactory.create())
            .build()
    }

    /**
     * Builds the OkHttpClient with authentication, logging, and appropriate timeouts.
     *
     * ## Interceptor Order
     * Interceptors are executed in order:
     * 1. [headerInterceptor] - Adds Authorization header and standard headers
     * 2. [HttpLoggingInterceptor] - Logs the complete request/response (including auth header)
     *
     * ## Timeouts
     * - **Connect:** 30 seconds - Time to establish a connection
     * - **Read:** 30 seconds - Time to receive response data
     * - **Write:** 60 seconds - Time to send request data (longer for uploads)
     *
     * @return The configured OkHttpClient instance
     */
    private fun buildOkHttpClient(): OkHttpClient {
        return okHttpClient.newBuilder().apply {
            // Add auth and standard headers
            addInterceptor(headerInterceptor)

            // Add logging interceptor using FlexiLogger
            addInterceptor(
                HttpLoggingInterceptor(
                    FlexiLogHttpLoggingInterceptorLogger.with(
                        logger = Log,
                        loggingLevel = LoggingLevel.V
                    )
                ).apply {
                    level = HttpLoggingInterceptor.Level.BODY
                }
            )

            // Configure timeouts
            connectTimeout(30, TimeUnit.SECONDS)
            readTimeout(30, TimeUnit.SECONDS)
            writeTimeout(60, TimeUnit.SECONDS)
        }.build()
    }

    /**
     * Creates a service instance for the specified API interface.
     *
     * This method is called by [uk.co.appoly.droid.data.remote.BaseService] to create
     * API client instances. Services are typically cached by the [uk.co.appoly.droid.data.remote.ServiceManager].
     *
     * @param serviceClass The class of the API interface to create
     * @return A new instance of the requested API interface
     */
    override fun <T> createService(serviceClass: Class<T>): T {
        return getRetrofitClient().create(serviceClass)
    }
}
