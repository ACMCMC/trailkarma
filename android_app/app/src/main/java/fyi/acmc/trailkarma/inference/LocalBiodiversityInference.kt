package fyi.acmc.trailkarma.inference

import android.content.Context
import android.content.res.AssetManager
import android.util.Half
import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt
import org.tensorflow.lite.Interpreter

data class AcousticCandidate(
    val label: String,
    val scientificName: String? = null,
    val taxonomicLevel: String,
    val score: Float,
    val genus: String? = null,
    val family: String? = null
)

data class StructuredCandidatePayload(
    val observationId: String,
    val topCandidates: List<AcousticCandidate>,
    val rawConfidence: Float,
    val backgroundFlags: List<String>,
    val lat: Double? = null,
    val lon: Double? = null,
    val timestamp: String
)

data class LocalInferenceDecision(
    val finalLabel: String,
    val finalTaxonomicLevel: String,
    val confidenceBand: String,
    val explanation: String,
    val safeForRewarding: Boolean
)

data class LocalInferenceResult(
    val topCandidates: List<AcousticCandidate>,
    val rawConfidence: Float,
    val decision: LocalInferenceDecision,
    val modelMetadata: Map<String, Any?>
)

private data class SignalFeatures(
    val rms: Float,
    val zcr: Float,
    val dominantFreqHz: Float,
    val centroidHz: Float,
    val lowRatio: Float,
    val midRatio: Float,
    val highRatio: Float
)

private data class LabelMetadata(
    @Json(name = "display_label") val displayLabel: String? = null,
    @Json(name = "scientific_name") val scientificName: String? = null,
    @Json(name = "taxonomic_level") val taxonomicLevel: String? = null,
    val genus: String? = null,
    val family: String? = null
)

private data class PrototypeEntry(
    @Json(name = "label_key") val labelKey: String,
    val label: String,
    val source: String? = null,
    @Json(name = "source_id") val sourceId: String? = null
)

private data class ModelManifest(
    @Json(name = "model_file") val modelFile: String,
    @Json(name = "classifier_weights_file") val classifierWeightsFile: String,
    @Json(name = "classifier_bias_file") val classifierBiasFile: String,
    @Json(name = "classifier_classes_file") val classifierClassesFile: String,
    @Json(name = "prototype_embeddings_file") val prototypeEmbeddingsFile: String,
    @Json(name = "prototype_embeddings_dtype") val prototypeEmbeddingsDtype: String = "float32",
    @Json(name = "prototype_bank_file") val prototypeBankFile: String,
    @Json(name = "label_metadata_file") val labelMetadataFile: String,
    @Json(name = "embedding_dim") val embeddingDim: Int,
    @Json(name = "window_sample_rate_hz") val windowSampleRateHz: Int = 32000,
    @Json(name = "window_seconds") val windowSeconds: Float = 5f,
    @Json(name = "model_version") val modelVersion: String = "unknown"
)

private data class LoadedModelBundle(
    val source: String,
    val manifest: ModelManifest,
    val classifierWeights: FloatArray,
    val classifierBias: FloatArray,
    val classifierClasses: List<String>,
    val prototypeEmbeddings: FloatArray,
    val prototypeCount: Int,
    val prototypeBank: List<PrototypeEntry>,
    val labelMetadata: Map<String, LabelMetadata>
)

class LocalBiodiversityInferenceEngine(private val context: Context) {
    private val moshi = Moshi.Builder().build()
    private val manifestAdapter = moshi.adapter(ModelManifest::class.java)
    private val prototypeListAdapter = moshi.adapter<List<PrototypeEntry>>(
        com.squareup.moshi.Types.newParameterizedType(List::class.java, PrototypeEntry::class.java)
    )
    private val stringListAdapter = moshi.adapter<List<String>>(
        com.squareup.moshi.Types.newParameterizedType(List::class.java, String::class.java)
    )
    private val metadataMapAdapter = moshi.adapter<Map<String, LabelMetadata>>(
        com.squareup.moshi.Types.newParameterizedType(
            Map::class.java,
            String::class.java,
            LabelMetadata::class.java
        )
    )
    private val bundle: LoadedModelBundle? by lazy { loadModelBundleOrNull() }

    suspend fun infer(
        audioFile: File,
        observationId: String,
        lat: Double?,
        lon: Double?,
        timestamp: String
    ): LocalInferenceResult {
        val (sampleRate, signal) = WavReader.readMonoPcm16(audioFile)
        val features = extractFeatures(signal, sampleRate)
        val modelBundle = bundle
        val payload = if (modelBundle != null) {
            val embedding = embedAudio(signal, sampleRate, modelBundle)
            buildStructuredPayloadFromBundle(modelBundle, embedding, features, observationId, lat, lon, timestamp)
        } else {
            heuristicPayload(features, observationId, lat, lon, timestamp)
        }

        val decision = DeterministicDecisionPolicy.decide(payload)
        val modelMetadata = mutableMapOf<String, Any?>(
            "provider" to if (modelBundle != null) "local_tflite_perch" else "heuristic_fallback",
            "model_version" to modelBundle?.manifest?.modelVersion,
            "model_source" to modelBundle?.source,
            "sample_rate_hz" to (modelBundle?.manifest?.windowSampleRateHz ?: sampleRate),
            "window_seconds" to (modelBundle?.manifest?.windowSeconds ?: 5f),
            "background_flags" to payload.backgroundFlags,
            "llm_provider" to "deterministic_only"
        )
        return LocalInferenceResult(
            topCandidates = payload.topCandidates,
            rawConfidence = payload.rawConfidence,
            decision = decision,
            modelMetadata = modelMetadata
        )
    }

    private fun loadModelBundleOrNull(): LoadedModelBundle? {
        resolveBundleSource()?.let { source ->
            val manifest = source.open(source.manifestPath).use { stream ->
                manifestAdapter.fromJson(stream.bufferedReader().use { it.readText() }) ?: return null
            }
            if (!source.contains(manifest.modelFile)) {
                return null
            }

            val classifierClasses = source.open(manifest.classifierClassesFile).use { stream ->
                stringListAdapter.fromJson(stream.bufferedReader().use { it.readText() }) ?: emptyList()
            }
            val prototypeBank = source.open(manifest.prototypeBankFile).use { stream ->
                prototypeListAdapter.fromJson(stream.bufferedReader().use { it.readText() }) ?: emptyList()
            }
            val labelMetadata = source.open(manifest.labelMetadataFile).use { stream ->
                metadataMapAdapter.fromJson(stream.bufferedReader().use { it.readText() }) ?: emptyMap()
            }

            val classifierBias = readFloatArray(source, manifest.classifierBiasFile, "float32")
            val classifierWeights = readFloatArray(source, manifest.classifierWeightsFile, "float32")
            val prototypeEmbeddings = readFloatArray(
                source,
                manifest.prototypeEmbeddingsFile,
                manifest.prototypeEmbeddingsDtype
            )
            val prototypeCount = if (manifest.embeddingDim == 0) 0 else prototypeEmbeddings.size / manifest.embeddingDim

            return LoadedModelBundle(
                source = source.description,
                manifest = manifest,
                classifierWeights = classifierWeights,
                classifierBias = classifierBias,
                classifierClasses = classifierClasses,
                prototypeEmbeddings = prototypeEmbeddings,
                prototypeCount = prototypeCount,
                prototypeBank = prototypeBank,
                labelMetadata = labelMetadata
            )
        }
        return null
    }

    private fun embedAudio(signal: FloatArray, sampleRate: Int, bundle: LoadedModelBundle): FloatArray {
        val targetSamples = (bundle.manifest.windowSampleRateHz * bundle.manifest.windowSeconds).roundToInt()
        val resampled = resample(signal, sampleRate, bundle.manifest.windowSampleRateHz)
        val normalized = FloatArray(targetSamples)
        for (index in 0 until min(targetSamples, resampled.size)) {
            normalized[index] = resampled[index]
        }

        val interpreter = Interpreter(loadMappedModel(bundle, bundle.manifest.modelFile), Interpreter.Options().apply {
            setNumThreads(4)
        })
        try {
            val inputTensor = interpreter.getInputTensor(0)
            val inputElements = inputTensor.shape().fold(1) { acc, size -> acc * size }
            val inputBuffer = ByteBuffer.allocateDirect(inputElements * 4).order(ByteOrder.nativeOrder())
            for (index in 0 until inputElements) {
                inputBuffer.putFloat(if (index < normalized.size) normalized[index] else 0f)
            }
            inputBuffer.rewind()

            val outputShape = interpreter.getOutputTensor(0).shape()
            val outputElements = outputShape.fold(1) { acc, size -> acc * size }
            val outputBuffer = ByteBuffer.allocateDirect(outputElements * 4).order(ByteOrder.nativeOrder())
            interpreter.run(inputBuffer, outputBuffer)
            outputBuffer.rewind()
            val rawOutput = FloatArray(outputElements)
            outputBuffer.asFloatBuffer().get(rawOutput)
            return when {
                rawOutput.size == bundle.manifest.embeddingDim -> rawOutput
                rawOutput.size > bundle.manifest.embeddingDim -> rawOutput.copyOfRange(rawOutput.size - bundle.manifest.embeddingDim, rawOutput.size)
                else -> FloatArray(bundle.manifest.embeddingDim).also { destination ->
                    rawOutput.copyInto(destination, endIndex = rawOutput.size)
                }
            }
        } finally {
            interpreter.close()
        }
    }

    private fun buildStructuredPayloadFromBundle(
        bundle: LoadedModelBundle,
        embedding: FloatArray,
        features: SignalFeatures,
        observationId: String,
        lat: Double?,
        lon: Double?,
        timestamp: String
    ): StructuredCandidatePayload {
        val classifier = predictClassifierProbabilities(embedding, bundle)
        val retrieval = retrieveNearestPrototypes(embedding, bundle)
        val (candidates, rawConfidence, backgroundFlags) = combineSignals(bundle, classifier, retrieval, features)
        return StructuredCandidatePayload(
            observationId = observationId,
            topCandidates = candidates,
            rawConfidence = rawConfidence,
            backgroundFlags = backgroundFlags,
            lat = lat,
            lon = lon,
            timestamp = timestamp
        )
    }

    private fun predictClassifierProbabilities(
        embedding: FloatArray,
        bundle: LoadedModelBundle
    ): List<Pair<String, Float>> {
        val labelCount = bundle.classifierClasses.size
        if (labelCount == 0) return emptyList()
        val logits = FloatArray(labelCount)
        var maxLogit = Float.NEGATIVE_INFINITY
        for (row in 0 until labelCount) {
            val offset = row * bundle.manifest.embeddingDim
            var sum = bundle.classifierBias.getOrElse(row) { 0f }
            for (col in 0 until bundle.manifest.embeddingDim) {
                sum += bundle.classifierWeights[offset + col] * embedding[col]
            }
            logits[row] = sum
            if (sum > maxLogit) maxLogit = sum
        }
        var expSum = 0.0
        val probabilities = DoubleArray(labelCount)
        for (index in 0 until labelCount) {
            val value = kotlin.math.exp((logits[index] - maxLogit).toDouble())
            probabilities[index] = value
            expSum += value
        }
        return probabilities.indices
            .map { index -> bundle.classifierClasses[index] to (probabilities[index] / expSum).toFloat() }
            .sortedByDescending { it.second }
    }

    private fun retrieveNearestPrototypes(
        embedding: FloatArray,
        bundle: LoadedModelBundle
    ): List<Pair<String, Float>> {
        if (bundle.prototypeCount == 0) return emptyList()
        val embeddingNorm = l2Normalize(embedding)
        val grouped = linkedMapOf<String, MutableList<Float>>()
        for (row in 0 until bundle.prototypeCount) {
            val offset = row * bundle.manifest.embeddingDim
            val similarity = cosineSimilarity(embeddingNorm, bundle.prototypeEmbeddings, offset, bundle.manifest.embeddingDim)
            val labelKey = bundle.prototypeBank.getOrNull(row)?.labelKey ?: continue
            grouped.getOrPut(labelKey) { mutableListOf() }.add(similarity)
        }
        return grouped
            .mapValues { (_, sims) ->
                val ranked = sims.sortedDescending()
                val first = ranked.firstOrNull() ?: 0f
                val secondAvg = ranked.take(2).average().toFloat()
                first * 0.7f + secondAvg * 0.3f
            }
            .entries
            .sortedByDescending { it.value }
            .map { it.key to it.value }
    }

    private fun combineSignals(
        bundle: LoadedModelBundle,
        classifier: List<Pair<String, Float>>,
        retrieval: List<Pair<String, Float>>,
        features: SignalFeatures
    ): Triple<List<AcousticCandidate>, Float, List<String>> {
        val combined = linkedMapOf<String, Float>()
        classifier.forEach { (labelKey, probability) ->
            combined[labelKey] = combined.getOrDefault(labelKey, 0f) + 0.7f * probability
        }
        retrieval.forEach { (labelKey, score) ->
            val normalizedScore = max(0f, (score + 1f) / 2f)
            combined[labelKey] = combined.getOrDefault(labelKey, 0f) + 0.3f * normalizedScore
        }
        val topClassifier = classifier.firstOrNull()?.first
        val topRetrieval = retrieval.firstOrNull()?.first
        if (topClassifier != null && topClassifier == topRetrieval) {
            combined[topClassifier] = combined.getOrDefault(topClassifier, 0f) + 0.08f
        }

        val ranked = combined.entries.sortedByDescending { it.value }.take(5)
        if (ranked.isEmpty()) {
            val fallback = heuristicPayload(features, "unknown", null, null, "")
            return Triple(fallback.topCandidates, fallback.rawConfidence, fallback.backgroundFlags)
        }

        val backgroundFlags = backgroundFlags(features)
        val topScore = ranked.first().value
        val secondScore = ranked.getOrNull(1)?.value ?: 0f
        val margin = max(0f, topScore - secondScore)
        val rawConfidence = min(0.99f, max(0.05f, topScore + 0.25f * margin))

        val candidates = ranked.take(3).map { (labelKey, score) ->
            candidateFromLabel(bundle, labelKey, score)
        }.toMutableList()

        val topMeta = bundle.labelMetadata[ranked.first().key]
        if (topMeta?.genus != null && candidates.none { it.taxonomicLevel == "genus" && it.label == topMeta.genus }) {
            candidates += AcousticCandidate(
                label = topMeta.genus,
                taxonomicLevel = "genus",
                score = max(0.05f, rawConfidence * 0.45f),
                genus = topMeta.genus,
                family = topMeta.family
            )
        }
        if (topMeta?.family != null && candidates.none { it.taxonomicLevel == "family" && it.label == topMeta.family }) {
            candidates += AcousticCandidate(
                label = topMeta.family,
                taxonomicLevel = "family",
                score = max(0.04f, rawConfidence * 0.28f),
                family = topMeta.family
            )
        }
        if (rawConfidence < 0.38f && candidates.none { it.label == "unknown animal sound" }) {
            candidates.add(
                0,
                AcousticCandidate(
                    label = "unknown animal sound",
                    taxonomicLevel = "unknown",
                    score = max(0.2f, 1f - rawConfidence)
                )
            )
        }

        val normalized = normalizeCandidateScores(candidates.take(3))
        return Triple(normalized, rawConfidence, backgroundFlags)
    }

    private fun candidateFromLabel(bundle: LoadedModelBundle, labelKey: String, score: Float): AcousticCandidate {
        val meta = bundle.labelMetadata[labelKey]
        return AcousticCandidate(
            label = meta?.displayLabel ?: "unknown animal sound",
            scientificName = meta?.scientificName,
            taxonomicLevel = meta?.taxonomicLevel ?: "unknown",
            score = score,
            genus = meta?.genus,
            family = meta?.family
        )
    }

    private fun normalizeCandidateScores(candidates: List<AcousticCandidate>): List<AcousticCandidate> {
        val total = candidates.sumOf { max(it.score, 0f).toDouble() }.toFloat().takeIf { it > 0f } ?: 1f
        return candidates.map { it.copy(score = ((it.score / total) * 10000f).roundToInt() / 10000f) }
    }

    private fun heuristicPayload(
        features: SignalFeatures,
        observationId: String,
        lat: Double?,
        lon: Double?,
        timestamp: String
    ): StructuredCandidatePayload {
        val backgroundFlags = backgroundFlags(features)
        val (candidates, rawConfidence) = when {
            features.rms < 0.015f -> normalizeCandidateScores(
                listOf(
                    AcousticCandidate("unknown animal sound", taxonomicLevel = "unknown", score = 0.18f),
                    AcousticCandidate("Wind / stream noise", taxonomicLevel = "unknown", score = 0.52f),
                    AcousticCandidate("Human speech", taxonomicLevel = "unknown", score = 0.10f)
                )
            ) to 0.18f
            features.lowRatio > 0.58f -> normalizeCandidateScores(
                listOf(
                    AcousticCandidate("Wind / stream noise", taxonomicLevel = "unknown", score = 0.72f),
                    AcousticCandidate("unknown animal sound", taxonomicLevel = "unknown", score = 0.16f),
                    AcousticCandidate("Human speech", taxonomicLevel = "unknown", score = 0.08f)
                )
            ) to 0.16f
            features.dominantFreqHz in 250f..3400f && features.zcr in 0.04f..0.22f && features.highRatio in 0.18f..0.6f ->
                normalizeCandidateScores(
                    listOf(
                        AcousticCandidate("Human speech", taxonomicLevel = "unknown", score = 0.81f),
                        AcousticCandidate("unknown animal sound", taxonomicLevel = "unknown", score = 0.12f),
                        AcousticCandidate("Wind / stream noise", taxonomicLevel = "unknown", score = 0.05f)
                    )
                ) to 0.12f
            features.dominantFreqHz >= 3000f && features.highRatio > 0.55f ->
                normalizeCandidateScores(
                    listOf(
                        AcousticCandidate(
                            label = "Pacific tree cricket",
                            scientificName = "Oecanthus californicus",
                            taxonomicLevel = "species",
                            score = 0.62f,
                            genus = "Oecanthus",
                            family = "Gryllidae"
                        ),
                        AcousticCandidate("Gryllidae", taxonomicLevel = "family", score = 0.21f, family = "Gryllidae"),
                        AcousticCandidate("Insect chorus", taxonomicLevel = "unknown", score = 0.09f)
                    )
                ) to 0.62f
            features.centroidHz in 1200f..3200f && features.highRatio > 0.35f ->
                normalizeCandidateScores(
                    listOf(
                        AcousticCandidate(
                            label = "California scrub-jay",
                            scientificName = "Aphelocoma californica",
                            taxonomicLevel = "species",
                            score = 0.67f,
                            genus = "Aphelocoma",
                            family = "Corvidae"
                        ),
                        AcousticCandidate(
                            label = "Steller's jay",
                            scientificName = "Cyanocitta stelleri",
                            taxonomicLevel = "species",
                            score = 0.19f,
                            genus = "Cyanocitta",
                            family = "Corvidae"
                        ),
                        AcousticCandidate("Corvidae", taxonomicLevel = "family", score = 0.09f, family = "Corvidae")
                    )
                ) to 0.67f
            features.dominantFreqHz in 300f..1100f && features.zcr in 0.18f..0.42f ->
                normalizeCandidateScores(
                    listOf(
                        AcousticCandidate(
                            label = "Pacific treefrog",
                            scientificName = "Pseudacris regilla",
                            taxonomicLevel = "species",
                            score = 0.58f,
                            genus = "Pseudacris",
                            family = "Hylidae"
                        ),
                        AcousticCandidate("Pseudacris", taxonomicLevel = "genus", score = 0.23f, genus = "Pseudacris", family = "Hylidae"),
                        AcousticCandidate("Hylidae", taxonomicLevel = "family", score = 0.10f, family = "Hylidae")
                    )
                ) to 0.58f
            else -> normalizeCandidateScores(
                listOf(
                    AcousticCandidate("unknown animal sound", taxonomicLevel = "unknown", score = 0.41f),
                    AcousticCandidate("Mammalia", taxonomicLevel = "family", score = 0.22f, family = "Mammalia"),
                    AcousticCandidate("Corvidae", taxonomicLevel = "family", score = 0.14f, family = "Corvidae")
                )
            ) to 0.41f
        }

        return StructuredCandidatePayload(
            observationId = observationId,
            topCandidates = candidates,
            rawConfidence = rawConfidence,
            backgroundFlags = backgroundFlags,
            lat = lat,
            lon = lon,
            timestamp = timestamp
        )
    }

    private fun extractFeatures(signal: FloatArray, sampleRate: Int): SignalFeatures {
        if (signal.isEmpty()) {
            return SignalFeatures(0f, 0f, 0f, 0f, 1f, 0f, 0f)
        }
        val rms = sqrt(signal.map { it * it }.average()).toFloat()
        var zeroCrossings = 0
        for (index in 1 until signal.size) {
            if ((signal[index - 1] >= 0f) != (signal[index] >= 0f)) zeroCrossings++
        }
        val zcr = zeroCrossings.toFloat() / max(1, signal.size - 1)

        val spectrum = magnitudeSpectrum(signal)
        val totalEnergy = spectrum.sum().takeIf { it > 0f } ?: 1e-8f
        val frequencyStep = sampleRate.toFloat() / signal.size
        var dominantIndex = 0
        var dominantValue = Float.NEGATIVE_INFINITY
        var centroidNumerator = 0.0
        var lowEnergy = 0.0
        var midEnergy = 0.0
        var highEnergy = 0.0
        for (index in spectrum.indices) {
            val value = spectrum[index]
            if (value > dominantValue) {
                dominantValue = value
                dominantIndex = index
            }
            val freq = index * frequencyStep
            centroidNumerator += freq * value
            when {
                freq < 400f -> lowEnergy += value
                freq < 2000f -> midEnergy += value
                else -> highEnergy += value
            }
        }
        return SignalFeatures(
            rms = rms,
            zcr = zcr,
            dominantFreqHz = dominantIndex * frequencyStep,
            centroidHz = (centroidNumerator / totalEnergy).toFloat(),
            lowRatio = (lowEnergy / totalEnergy).toFloat(),
            midRatio = (midEnergy / totalEnergy).toFloat(),
            highRatio = (highEnergy / totalEnergy).toFloat()
        )
    }

    private fun backgroundFlags(features: SignalFeatures): List<String> {
        val flags = mutableListOf<String>()
        if (features.rms < 0.015f) flags += "silence_high"
        if (features.lowRatio > 0.58f) flags += "wind_low"
        if (features.dominantFreqHz in 250f..3400f && features.zcr in 0.04f..0.22f && features.highRatio in 0.18f..0.6f) {
            flags += "speech_like"
        }
        return flags
    }

    private fun magnitudeSpectrum(signal: FloatArray): FloatArray {
        val trimmed = if (signal.size > 2048) {
            val stride = signal.size.toFloat() / 2048f
            FloatArray(2048) { index ->
                signal[min(signal.lastIndex, (index * stride).toInt())]
            }
        } else {
            signal
        }
        val half = trimmed.size / 2
        val output = FloatArray(half + 1)
        for (k in 0..half) {
            var real = 0.0
            var imag = 0.0
            for (n in trimmed.indices) {
                val angle = 2.0 * Math.PI * k * n / trimmed.size
                val window = 0.5 - 0.5 * kotlin.math.cos(2.0 * Math.PI * n / max(1, trimmed.size - 1))
                val sample = trimmed[n] * window.toFloat()
                real += sample * kotlin.math.cos(angle)
                imag -= sample * kotlin.math.sin(angle)
            }
            output[k] = sqrt((real * real + imag * imag).toFloat())
        }
        return output
    }

    private fun resample(audio: FloatArray, sourceRate: Int, targetRate: Int): FloatArray {
        if (sourceRate == targetRate || audio.isEmpty()) return audio.copyOf()
        val duration = audio.size / sourceRate.toFloat()
        val targetSize = max(1, (duration * targetRate).roundToInt())
        val output = FloatArray(targetSize)
        val scale = audio.size.toFloat() / targetSize.toFloat()
        for (index in output.indices) {
            val sourceIndex = index * scale
            val left = sourceIndex.toInt().coerceIn(0, audio.lastIndex)
            val right = min(audio.lastIndex, left + 1)
            val fraction = sourceIndex - left
            output[index] = audio[left] * (1f - fraction) + audio[right] * fraction
        }
        return output
    }

    private fun readFloatArray(source: BundleSource, relativePath: String, dtype: String): FloatArray {
        val bytes = readAllBytes(source, relativePath)
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        return when (dtype.lowercase()) {
            "float16", "f16" -> FloatArray(bytes.size / 2) { Half.toFloat(buffer.short) }
            else -> FloatArray(bytes.size / 4) { buffer.float }
        }
    }

    private fun l2Normalize(values: FloatArray): FloatArray {
        var sum = 0f
        values.forEach { value -> sum += value * value }
        val norm = sqrt(sum) + 1e-8f
        return FloatArray(values.size) { index -> values[index] / norm }
    }

    private fun cosineSimilarity(vector: FloatArray, matrix: FloatArray, offset: Int, length: Int): Float {
        var dot = 0f
        var norm = 0f
        for (index in 0 until length) {
            val value = matrix[offset + index]
            dot += vector[index] * value
            norm += value * value
        }
        return if (norm <= 0f) 0f else dot / (sqrt(norm) + 1e-8f)
    }

    private fun loadMappedModel(bundle: LoadedModelBundle, relativePath: String): MappedByteBuffer {
        val source = resolveBundleSource(bundle.source) ?: error("Model source unavailable: ${bundle.source}")
        return when (source) {
            is BundleSource.AssetBundleSource -> {
                val descriptor = context.assets.openFd(source.prefixed(relativePath))
                FileInputStream(descriptor.fileDescriptor).channel.use { channel ->
                    channel.map(FileChannel.MapMode.READ_ONLY, descriptor.startOffset, descriptor.declaredLength)
                }
            }
            is BundleSource.DirectoryBundleSource -> {
                FileInputStream(source.resolve(relativePath)).channel.use { channel ->
                    channel.map(FileChannel.MapMode.READ_ONLY, 0L, channel.size())
                }
            }
        }
    }

    private fun readAllBytes(source: BundleSource, relativePath: String): ByteArray {
        source.open(relativePath).use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                output.write(buffer, 0, read)
            }
            return output.toByteArray()
        }
    }

    private fun resolveBundleSource(sourceDescription: String? = null): BundleSource? {
        if (sourceDescription != null) {
            val candidates = candidateBundleSources()
            return candidates.firstOrNull { it.description == sourceDescription }
        }
        return candidateBundleSources().firstOrNull()
    }

    private fun candidateBundleSources(): List<BundleSource> {
        val sources = mutableListOf<BundleSource>()
        val internalDir = File(context.filesDir, "biodiversity_model")
        if (internalDir.exists()) {
            sources += BundleSource.DirectoryBundleSource("internal_files", internalDir)
        }
        val externalDir = context.getExternalFilesDir(null)?.let { File(it, "biodiversity_model") }
        if (externalDir != null && externalDir.exists()) {
            sources += BundleSource.DirectoryBundleSource("external_files", externalDir)
        }
        val assetNames = runCatching { context.assets.list("biodiversity")?.toSet().orEmpty() }.getOrElse { emptySet() }
        if (assetNames.contains("model_manifest.json")) {
            sources += BundleSource.AssetBundleSource("app_assets", context.assets, "biodiversity", assetNames)
        }
        return sources
    }
}

private sealed interface BundleSource {
    val description: String
    val manifestPath: String

    fun contains(relativePath: String): Boolean
    fun open(relativePath: String): java.io.InputStream

    data class AssetBundleSource(
        override val description: String,
        private val assetManager: AssetManager,
        private val root: String,
        private val assetNames: Set<String>
    ) : BundleSource {
        override val manifestPath: String = "model_manifest.json"

        fun prefixed(relativePath: String): String = "$root/$relativePath"

        override fun contains(relativePath: String): Boolean = assetNames.contains(relativePath)

        override fun open(relativePath: String): java.io.InputStream {
            return assetManager.open(prefixed(relativePath))
        }
    }

    data class DirectoryBundleSource(
        override val description: String,
        private val root: File
    ) : BundleSource {
        override val manifestPath: String = resolve("model_manifest.json").absolutePath

        fun resolve(relativePath: String): File = File(root, relativePath)

        override fun contains(relativePath: String): Boolean = resolve(relativePath).exists()

        override fun open(relativePath: String): java.io.InputStream = resolve(relativePath).inputStream()
    }
}

private object WavReader {
    fun readMonoPcm16(file: File): Pair<Int, FloatArray> {
        val bytes = file.readBytes()
        if (bytes.size < 44) throw IOException("Invalid WAV file: header too short")
        val header = ByteBuffer.wrap(bytes, 0, 44).order(ByteOrder.LITTLE_ENDIAN)
        header.position(22)
        val channels = header.short.toInt()
        val sampleRate = header.int
        header.position(34)
        val bitsPerSample = header.short.toInt()
        if (bitsPerSample != 16) throw IOException("Only 16-bit PCM WAV is supported")

        val data = ByteBuffer.wrap(bytes, 44, bytes.size - 44).order(ByteOrder.LITTLE_ENDIAN)
        val frameCount = (bytes.size - 44) / 2 / max(1, channels)
        val signal = FloatArray(frameCount)
        for (frame in 0 until frameCount) {
            var sum = 0f
            for (channel in 0 until channels) {
                sum += data.short / 32768f
            }
            signal[frame] = sum / max(1, channels)
        }
        return sampleRate to signal
    }
}

private object DeterministicDecisionPolicy {
    fun decide(payload: StructuredCandidatePayload): LocalInferenceDecision {
        val top = payload.topCandidates.firstOrNull()
            ?: return LocalInferenceDecision(
                finalLabel = "unknown animal sound",
                finalTaxonomicLevel = "unknown",
                confidenceBand = "low",
                explanation = "The clip likely contains an animal sound, but the evidence is too weak for a reliable species or genus call.",
                safeForRewarding = false
            )

        val background = mapOf(
            "Human speech" to ("high" to "This clip is dominated by human speech rather than wildlife vocalization."),
            "Wind / stream noise" to ("medium" to "The strongest evidence is environmental background noise, so this is not safe to reward as a wildlife detection."),
            "Traffic noise" to ("medium" to "The clip is dominated by traffic or road noise rather than wildlife."),
            "Dog bark" to ("medium" to "The strongest signal is a domestic dog bark, not a trail biodiversity contribution."),
            "Footsteps / gear rustle" to ("medium" to "Handling noise and footsteps dominate the clip, so the wildlife evidence is not reliable."),
            "Silence" to ("high" to "There is not enough acoustic activity in the clip to support a wildlife detection.")
        )
        background[top.label]?.let { (band, explanation) ->
            return LocalInferenceDecision(
                finalLabel = top.label,
                finalTaxonomicLevel = "unknown",
                confidenceBand = band,
                explanation = explanation,
                safeForRewarding = false
            )
        }

        if (top.taxonomicLevel == "species" && payload.rawConfidence >= 0.65f) {
            return LocalInferenceDecision(
                finalLabel = top.label,
                finalTaxonomicLevel = "species",
                confidenceBand = if (payload.rawConfidence < 0.8f) "medium-high" else "high",
                explanation = "Likely ${top.label} because it is the strongest acoustic match and the alternatives are weaker.",
                safeForRewarding = true
            )
        }

        val familyCandidate = payload.topCandidates.firstOrNull { it.taxonomicLevel == "family" }
        if (top.taxonomicLevel == "species" && payload.rawConfidence >= 0.45f) {
            val fallbackLabel = top.genus ?: familyCandidate?.label ?: top.label
            val fallbackLevel = if (top.genus != null) "genus" else "family"
            return LocalInferenceDecision(
                finalLabel = fallbackLabel,
                finalTaxonomicLevel = fallbackLevel,
                confidenceBand = "medium",
                explanation = "This sounds like $fallbackLabel, but species-level resolution is not reliable enough from this clip alone.",
                safeForRewarding = fallbackLevel == "genus"
            )
        }

        return LocalInferenceDecision(
            finalLabel = "unknown animal sound",
            finalTaxonomicLevel = "unknown",
            confidenceBand = "low",
            explanation = "The clip likely contains an animal sound, but the evidence is too weak for a reliable species or genus call.",
            safeForRewarding = false
        )
    }
}
