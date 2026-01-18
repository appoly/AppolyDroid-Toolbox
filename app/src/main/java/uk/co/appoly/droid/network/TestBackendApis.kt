package uk.co.appoly.droid.network

import com.skydoves.sandwich.ApiResponse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.POST
import uk.co.appoly.droid.data.remote.BaseService

/**
 * API interfaces for the test multipart upload backend server.
 *
 * ## Server Information
 * - **Host:** `https://multipart-uploader.on-forge.com`
 * - **Source:** [https://github.com/appoly/s3-uploader](https://github.com/appoly/s3-uploader)
 * - **Purpose:** Test server for demonstrating S3 multipart upload functionality
 *
 * ## Available APIs
 * This file contains all API interface definitions for the test backend:
 *
 * 1. [AuthApi] - User authentication (login)
 *
 * The multipart upload endpoints are handled directly by the [uk.co.appoly.droid.s3upload.multipart.MultipartUploadManager]
 * using URLs generated from [uk.co.appoly.droid.s3upload.multipart.network.model.MultipartApiUrls].
 *
 * ## BaseService.API Pattern
 * All API interfaces extend [BaseService.API]. This marker interface:
 * - Enables type-safe service caching via [uk.co.appoly.droid.data.remote.ServiceManager]
 * - Allows use of the [uk.co.appoly.droid.data.repo.GenericBaseRepo.lazyService] delegate
 * - Provides consistent service lifecycle management
 *
 * ## Example Usage
 * ```kotlin
 * // In a GenericBaseRepo subclass
 * private val authService by lazyService<AuthApi>()
 *
 * suspend fun login(email: String, password: String): APIResult<LoginResponse> {
 *     val response = authService.api.login(LoginRequest(email, password))
 *     // Handle response...
 * }
 * ```
 */

// ==================== Authentication API ====================

/**
 * Authentication API for the test backend.
 *
 * Provides user authentication via email/password credentials.
 *
 * ## Endpoint Documentation
 *
 * ### POST /api/login
 * Authenticates a user and returns a Bearer token.
 *
 * **Request Body:**
 * ```json
 * {
 *   "email": "user@example.com",
 *   "password": "secret123"
 * }
 * ```
 *
 * **Success Response (200):**
 * ```json
 * {
 *   "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
 *   "user": {
 *     "id": 1,
 *     "name": "Test User",
 *     "email": "user@example.com",
 *     "created_at": "2024-01-01T00:00:00.000000Z",
 *     "updated_at": "2024-01-01T00:00:00.000000Z"
 *   }
 * }
 * ```
 *
 * **Error Response (401):**
 * ```json
 * {
 *   "message": "Invalid credentials"
 * }
 * ```
 *
 * ## Test Credentials
 * The test server accepts these credentials:
 * - Email: `bradley@appoly.co.uk`
 * - Password: `secret123`
 *
 * @see LoginRequest
 * @see LoginResponse
 */
interface AuthApi : BaseService.API {

    /**
     * Authenticates a user with email and password.
     *
     * Returns an [ApiResponse] wrapping the [LoginResponse]. The response is wrapped
     * by Sandwich's [com.skydoves.sandwich.retrofit.adapters.ApiResponseCallAdapterFactory],
     * providing consistent success/error handling.
     *
     * @param request The login credentials
     * @return [ApiResponse.Success] with [LoginResponse] on successful authentication,
     *         [ApiResponse.Failure.Error] for HTTP errors (4xx, 5xx),
     *         [ApiResponse.Failure.Exception] for network/parsing errors
     */
    @POST("api/login")
    suspend fun login(@Body request: LoginRequest): ApiResponse<LoginResponse>
}

// ==================== Request/Response Models ====================

/**
 * Request body for the login endpoint.
 *
 * Serialized to JSON:
 * ```json
 * {"email": "user@example.com", "password": "secret"}
 * ```
 *
 * @property email User's email address
 * @property password User's password (sent in plain text over HTTPS)
 */
@Serializable
data class LoginRequest(
    val email: String,
    val password: String
)

/**
 * Response from the login endpoint on successful authentication.
 *
 * ## Token Usage
 * The [token] should be:
 * 1. Stored securely (e.g., in encrypted SharedPreferences)
 * 2. Included in subsequent requests via `Authorization: Bearer {token}` header
 * 3. Used to authenticate with the multipart upload endpoints
 *
 * ## Example Response
 * ```json
 * {
 *   "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
 *   "user": {
 *     "id": 1,
 *     "name": "Test User",
 *     "email": "user@example.com"
 *   }
 * }
 * ```
 *
 * @property token JWT Bearer token for authentication (null if login failed)
 * @property user User information (null if login failed)
 */
@Serializable
data class LoginResponse(
    val token: String? = null,
    val user: UserData? = null
)

/**
 * User information returned from the login endpoint.
 *
 * Contains basic user profile information from the test backend.
 * All fields are nullable to handle partial responses gracefully.
 *
 * @property id Unique user identifier
 * @property name User's display name
 * @property email User's email address
 * @property createdAt ISO 8601 timestamp of account creation
 * @property updatedAt ISO 8601 timestamp of last account update
 */
@Serializable
data class UserData(
    val id: Int? = null,
    val name: String? = null,
    val email: String? = null,
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("updated_at")
    val updatedAt: String? = null
)
