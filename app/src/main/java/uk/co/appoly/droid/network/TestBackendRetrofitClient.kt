package uk.co.appoly.droid.network

import com.skydoves.sandwich.retrofit.adapters.ApiResponseCallAdapterFactory
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import uk.co.appoly.droid.data.remote.BaseRetrofitClient
import java.util.concurrent.TimeUnit

/**
 * BaseRetrofitClient implementation for the test multipart upload server.
 *
 * ## Server Information
 * - **Host:** `https://multipart-uploader.on-forge.com`
 * - **Purpose:** Test backend for demonstrating multipart upload functionality
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
 * 4. **Timeout Configuration:** Sets appropriate timeouts for different network operations:
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
         * - `/api/s3/multipart/*` - Multipart upload endpoints
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
     * - `isLenient`: Handles minor JSON formatting issues
     * - `encodeDefaults`: Includes properties with default values in serialized output
     */
    override val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    /**
     * HTTP logging interceptor for debugging network calls.
     *
     * Set to [HttpLoggingInterceptor.Level.BODY] for full request/response logging.
     * In production, this should be [HttpLoggingInterceptor.Level.NONE] or controlled
     * by a build configuration flag.
     */
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    /**
     * Authentication interceptor that adds Bearer tokens to requests.
     *
     * Uses the [tokenProvider] to get the current token for each request.
     * If no token is available, the Authorization header is not added.
     */
    private val authInterceptor = AuthInterceptor(tokenProvider)

    /**
     * OkHttp client configured with authentication, logging, and appropriate timeouts.
     *
     * ## Interceptor Order
     * Interceptors are executed in order:
     * 1. [authInterceptor] - Adds Authorization header
     * 2. [loggingInterceptor] - Logs the complete request/response (including auth header)
     *
     * ## Timeouts
     * - **Connect:** 30 seconds - Time to establish a connection
     * - **Read:** 30 seconds - Time to receive response data
     * - **Write:** 60 seconds - Time to send request data (longer for uploads)
     */
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * Retrofit instance configured for the test backend.
     *
     * Uses:
     * - Kotlinx Serialization converter for JSON handling
     * - Sandwich's [ApiResponseCallAdapterFactory] for response wrapping
     */
    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .addCallAdapterFactory(ApiResponseCallAdapterFactory.create())
        .build()

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
        return retrofit.create(serviceClass)
    }
}

/**
 * OkHttp Interceptor that adds Bearer token authentication to requests.
 *
 * ## How It Works
 * For each request, this interceptor:
 * 1. Calls the [tokenProvider] to get the current token
 * 2. If a token is available, adds an `Authorization: Bearer {token}` header
 * 3. If no token is available, the request proceeds without authentication
 *
 * ## Dynamic Token Updates
 * Because the token is retrieved via a lambda on each request, the token can be
 * updated (e.g., after login) without needing to recreate the Retrofit client.
 *
 * ## Usage
 * ```kotlin
 * val interceptor = AuthInterceptor { authState.currentToken }
 * val client = OkHttpClient.Builder()
 *     .addInterceptor(interceptor)
 *     .build()
 * ```
 *
 * @property tokenProvider Lambda that returns the current Bearer token, or null if not authenticated
 */
class AuthInterceptor(
    private val tokenProvider: () -> String?
) : Interceptor {

    /**
     * Intercepts the request and adds the Authorization header if a token is available.
     *
     * @param chain The interceptor chain
     * @return The response from the next interceptor or the network
     */
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        // Get the current token
        val token = tokenProvider()

        // If no token, proceed with the original request
        if (token.isNullOrBlank()) {
            return chain.proceed(originalRequest)
        }

        // Add Authorization header with Bearer token
        val authenticatedRequest = originalRequest.newBuilder()
            .header("Authorization", "Bearer $token")
            .build()

        return chain.proceed(authenticatedRequest)
    }
}
