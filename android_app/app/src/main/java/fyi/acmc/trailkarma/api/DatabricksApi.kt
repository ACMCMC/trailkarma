package fyi.acmc.trailkarma.api

import com.squareup.moshi.JsonClass
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

@JsonClass(generateAdapter = true)
data class DatabricksSyncRequest(
    val warehouse_id: String,
    val statement: String,
    val wait_timeout: String = "30s"
)

interface DatabricksApi {
    @Headers("Content-Type: application/json")
    @POST("api/2.0/sql/statements")
    suspend fun executeSql(@Body request: DatabricksSyncRequest): DatabricksSqlResponse
}

@JsonClass(generateAdapter = true)
data class DatabricksSqlResponse(
    val statement_id: String,
    val status: SqlStatus
)

@JsonClass(generateAdapter = true)
data class SqlStatus(
    val state: String,
    val error: SqlError? = null
)

@JsonClass(generateAdapter = true)
data class SqlError(
    val error_code: String,
    val message: String
)

object DatabricksApiClient {
    fun create(databricksUrl: String, token: String): DatabricksApi {
        val okHttpClient = okhttp3.OkHttpClient.Builder()
            .addInterceptor { chain ->
                val originalRequest = chain.request()
                val newRequest = originalRequest.newBuilder()
                    .header("Authorization", "Bearer $token")
                    .build()
                chain.proceed(newRequest)
            }
            .addInterceptor(okhttp3.logging.HttpLoggingInterceptor().apply {
                level = okhttp3.logging.HttpLoggingInterceptor.Level.BASIC
            })
            .build()

        return retrofit2.Retrofit.Builder()
            .baseUrl(databricksUrl.trimEnd('/') + "/")
            .addConverterFactory(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory().let {
                com.squareup.moshi.Moshi.Builder()
                    .add(it)
                    .build()
            }.let { retrofit2.converter.moshi.MoshiConverterFactory.create(it) })
            .client(okHttpClient)
            .build()
            .create(DatabricksApi::class.java)
    }
}
