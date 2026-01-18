package uk.co.appoly.droid.s3upload.multipart.network

import com.duck.flexilogger.LoggingLevel
import com.duck.flexilogger.okhttp.FlexiLogHttpLoggingInterceptorLogger
import com.skydoves.sandwich.retrofit.adapters.ApiResponseCallAdapterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import uk.co.appoly.droid.s3upload.S3Uploader
import uk.co.appoly.droid.s3upload.multipart.utils.MultipartUploadLog
import java.util.concurrent.TimeUnit

@Suppress("DEPRECATION")

/**
 * Retrofit client configured for multipart upload operations.
 *
 * Uses longer timeouts than the standard S3Uploader client since
 * individual part uploads may take longer for large chunks.
 */
internal object MultipartRetrofitClient {
    private var retrofit: Retrofit? = null

    private val okHttpClient by lazy { OkHttpClient() }

    val json = Json {
        ignoreUnknownKeys = true
        useAlternativeNames = true
        explicitNulls = false
        encodeDefaults = true
		prettyPrint = S3Uploader.loggingLevel == LoggingLevel.V
    }

    private fun getRetrofitClient(): Retrofit {
        if (retrofit == null) {
            synchronized(this) {
                if (retrofit == null) {
                    retrofit = Retrofit.Builder()
                        .baseUrl("https://not_used.com")
                        .addConverterFactory(
                            json.asConverterFactory(
                                "application/json; charset=UTF-8".toMediaType()
                            )
                        )
                        .client(
                            okHttpClient.newBuilder().apply {
                                // Longer timeouts for multipart uploads
                                connectTimeout(30, TimeUnit.SECONDS)
                                writeTimeout(120, TimeUnit.SECONDS) // 2 minutes for large chunks
                                readTimeout(60, TimeUnit.SECONDS)

                                if (S3Uploader.loggingLevel.level >= LoggingLevel.D.level) {
                                    addInterceptor(
                                        HttpLoggingInterceptor(
                                            FlexiLogHttpLoggingInterceptorLogger.with(MultipartUploadLog, "S3Multipart:http")
                                        ).apply {
                                            level = when (S3Uploader.loggingLevel) {
                                                LoggingLevel.V -> HttpLoggingInterceptor.Level.BODY // Full logging for verbose
                                                LoggingLevel.D -> HttpLoggingInterceptor.Level.HEADERS
                                                LoggingLevel.I -> HttpLoggingInterceptor.Level.BASIC
                                                LoggingLevel.W,
                                                LoggingLevel.E,
                                                LoggingLevel.NONE -> HttpLoggingInterceptor.Level.NONE
                                            }
                                        }
                                    )
                                }
                            }.build()
                        )
                        .addCallAdapterFactory(ApiResponseCallAdapterFactory.create())
                        .build()
                }
            }
        }
        return retrofit!!
    }

    fun <T> createService(tClass: Class<T>): T {
        return getRetrofitClient().create(tClass)
    }

    private var _multipartApis: MultipartApis? = null

    val multipartApis: MultipartApis
        get() {
            if (_multipartApis == null) {
                _multipartApis = createService(MultipartApis::class.java)
            }
            return _multipartApis!!
        }

    /**
     * Resets the Retrofit client, forcing it to be recreated on next use.
     * Useful for applying new configuration (like logging level changes).
     */
    fun reset() {
        synchronized(this) {
            retrofit = null
            _multipartApis = null
        }
    }
}
