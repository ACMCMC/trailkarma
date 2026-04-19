package fyi.acmc.trailkarma.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.*

@JsonClass(generateAdapter = true)
data class ReportSyncRequest(
    @Json(name = "report_id") val reportId: String,
    val type: String,
    val title: String,
    val description: String,
    val lat: Double,
    val lng: Double,
    val timestamp: String,
    val source: String,
    @Json(name = "species_name") val speciesName: String? = null
)

@JsonClass(generateAdapter = true)
data class LocationSyncRequest(
    @Json(name = "user_id") val userId: String,
    val lat: Double,
    val lng: Double,
    val timestamp: String
)

interface TrailKarmaApi {
    @POST("/sync/report")
    suspend fun syncReport(@Body body: ReportSyncRequest): Response<Unit>

    @POST("/sync/location")
    suspend fun syncLocation(@Body body: LocationSyncRequest): Response<Unit>
}

object ApiClient {
    fun create(baseUrl: String): TrailKarmaApi = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(
            OkHttpClient.Builder()
                .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY })
                .build()
        )
        .addConverterFactory(
            MoshiConverterFactory.create(
                Moshi.Builder()
                    .addLast(KotlinJsonAdapterFactory())
                    .build()
            )
        )
        .build()
        .create(TrailKarmaApi::class.java)
}
