package fyi.acmc.trailkarma.api

import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import fyi.acmc.trailkarma.BuildConfig
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

data class AcousticCandidateDto(
    val label: String,
    @Json(name = "scientific_name") val scientificName: String? = null,
    @Json(name = "taxonomic_level") val taxonomicLevel: String,
    val score: Double,
    val genus: String? = null,
    val family: String? = null
)

data class BiodiversityAudioResponse(
    @Json(name = "topK_acoustic_candidates") val topKAcousticCandidates: List<AcousticCandidateDto>,
    val finalLabel: String,
    val finalTaxonomicLevel: String,
    val confidence: Double,
    val confidenceBand: String,
    val explanation: String,
    val safeForRewarding: Boolean,
    @Json(name = "model_metadata") val modelMetadata: Map<String, Any?> = emptyMap()
)

data class PhotoLinkResponse(
    val success: Boolean
)

data class AudioSyncResponse(
    val success: Boolean
)

interface BiodiversityApi {
    @Multipart
    @POST("/api/biodiversity/audio")
    suspend fun uploadAudio(
        @Part audio: MultipartBody.Part,
        @Part("lat") lat: RequestBody,
        @Part("lon") lon: RequestBody,
        @Part("timestamp") timestamp: RequestBody,
        @Part("observation_id") observationId: RequestBody
    ): BiodiversityAudioResponse

    @Multipart
    @POST("/api/biodiversity/audio-sync")
    suspend fun syncAudioObservation(
        @Part audio: MultipartBody.Part,
        @Part("lat") lat: RequestBody,
        @Part("lon") lon: RequestBody,
        @Part("timestamp") timestamp: RequestBody,
        @Part("observation_id") observationId: RequestBody,
        @Part("final_label") finalLabel: RequestBody,
        @Part("final_taxonomic_level") finalTaxonomicLevel: RequestBody,
        @Part("confidence") confidence: RequestBody,
        @Part("confidence_band") confidenceBand: RequestBody,
        @Part("explanation") explanation: RequestBody,
        @Part("safe_for_rewarding") safeForRewarding: RequestBody,
        @Part("top_k_json") topKJson: RequestBody,
        @Part("model_metadata_json") modelMetadataJson: RequestBody,
        @Part("classification_source") classificationSource: RequestBody,
        @Part("local_model_version") localModelVersion: RequestBody
    ): AudioSyncResponse

    @Multipart
    @POST("/api/biodiversity/photo-link")
    suspend fun linkPhoto(
        @Part("observation_id") observationId: RequestBody,
        @Part photo: MultipartBody.Part
    ): PhotoLinkResponse
}

object BiodiversityApiClient {
    fun create(baseUrl: String = BuildConfig.API_BASE_URL): BiodiversityApi {
        val client = OkHttpClient.Builder()
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(
                MoshiConverterFactory.create(
                    Moshi.Builder()
                        .add(KotlinJsonAdapterFactory())
                        .build()
                )
            )
            .build()
            .create(BiodiversityApi::class.java)
    }
}
