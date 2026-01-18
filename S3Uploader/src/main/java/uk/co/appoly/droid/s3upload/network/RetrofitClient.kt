package uk.co.appoly.droid.s3upload.network

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
import uk.co.appoly.droid.s3upload.utils.S3UploadLog
import java.util.concurrent.TimeUnit

internal object RetrofitClient {
	private var retrofit: Retrofit? = null

	private val okHttpClient by lazy { OkHttpClient() }

	val json = Json {
		ignoreUnknownKeys = true
		useAlternativeNames = true
		explicitNulls = false
		encodeDefaults = true
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
								connectTimeout(20, TimeUnit.SECONDS)
								writeTimeout(20, TimeUnit.SECONDS)
								readTimeout(20, TimeUnit.SECONDS)
								if (S3Uploader.loggingLevel.level >= LoggingLevel.D.level) {
									addInterceptor(
										HttpLoggingInterceptor(
											FlexiLogHttpLoggingInterceptorLogger.with(S3UploadLog, "S3Uploader:http")
										).apply {
											level = when(S3Uploader.loggingLevel) {
												LoggingLevel.V,
												LoggingLevel.D -> HttpLoggingInterceptor.Level.BODY
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
						.addCallAdapterFactory(ApiResponseCallAdapterFactory.create())//for Skydoves Sandwich library (APIResponse)
						.build()
				}
			}
		}
		return retrofit!!
	}

	fun <T> createService(tClass: Class<T>): T {
		return getRetrofitClient().create(tClass)
	}

	val apiService by lazy { APIService() }
}