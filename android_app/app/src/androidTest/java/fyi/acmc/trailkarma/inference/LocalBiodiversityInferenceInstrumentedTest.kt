package fyi.acmc.trailkarma.inference

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.Instant
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalBiodiversityInferenceInstrumentedTest {

    @Test
    fun bundledPerchModelLoadsWithoutFallback() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val audioFile = File(context.cacheDir, "instrumented-perch-smoke.wav")
        writeSineWaveWav(
            destination = audioFile,
            sampleRateHz = 32_000,
            durationSeconds = 5,
            frequencyHz = 880.0
        )

        val result = LocalBiodiversityInferenceEngine(context).infer(
            audioFile = audioFile,
            observationId = "instrumented-${System.currentTimeMillis()}",
            lat = 32.8801,
            lon = -117.2340,
            timestamp = Instant.now().toString()
        )

        assertEquals("local_tflite_perch", result.modelMetadata["provider"])
        assertNull(result.modelMetadata["fallback_reason"])
    }

    private fun writeSineWaveWav(
        destination: File,
        sampleRateHz: Int,
        durationSeconds: Int,
        frequencyHz: Double
    ) {
        val sampleCount = sampleRateHz * durationSeconds
        val pcm = ByteBuffer.allocate(sampleCount * 2).order(ByteOrder.LITTLE_ENDIAN)
        repeat(sampleCount) { index ->
            val t = index.toDouble() / sampleRateHz.toDouble()
            val sample = (sin(2.0 * PI * frequencyHz * t) * Short.MAX_VALUE * 0.2).toInt()
            pcm.putShort(sample.toShort())
        }

        RandomAccessFile(destination, "rw").use { raf ->
            raf.setLength(0L)
            raf.writeBytes("RIFF")
            raf.writeInt(Integer.reverseBytes(36 + pcm.position()))
            raf.writeBytes("WAVE")
            raf.writeBytes("fmt ")
            raf.writeInt(Integer.reverseBytes(16))
            raf.writeShort(java.lang.Short.reverseBytes(1).toInt())
            raf.writeShort(java.lang.Short.reverseBytes(1).toInt())
            raf.writeInt(Integer.reverseBytes(sampleRateHz))
            raf.writeInt(Integer.reverseBytes(sampleRateHz * 2))
            raf.writeShort(java.lang.Short.reverseBytes(2).toInt())
            raf.writeShort(java.lang.Short.reverseBytes(16).toInt())
            raf.writeBytes("data")
            raf.writeInt(Integer.reverseBytes(pcm.position()))
            raf.write(pcm.array(), 0, pcm.position())
        }
    }
}
