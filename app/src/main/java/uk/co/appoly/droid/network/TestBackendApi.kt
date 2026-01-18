package uk.co.appoly.droid.network

import com.skydoves.sandwich.ApiResponse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Test backend API for multipart upload testing.
 * Host: multipart-uploader.on-forge.com
 */
interface TestBackendApi {

    @POST("api/login")
    suspend fun login(@Body request: LoginRequest): ApiResponse<LoginResponse>
}

@Serializable
data class LoginRequest(
    val email: String,
    val password: String
)

@Serializable
data class LoginResponse(
    val success: Boolean = false,
    val message: String? = null,
    val data: LoginData? = null
)

@Serializable
data class LoginData(
    val token: String,
    val user: UserData? = null
)

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
