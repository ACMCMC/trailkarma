package fyi.acmc.trailkarma.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

object TrailAudioRecorder {
    const val SAMPLE_RATE = 16_000
    private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
    private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
    private const val BITS_PER_SAMPLE = 16
    private const val RECORD_DURATION_SECONDS = 5

    @SuppressLint("MissingPermission")
    fun recordFiveSecondWav(outputFile: File) {
        outputFile.parentFile?.mkdirs()

        val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, ENCODING)
        val buffer = ByteArray(minBufferSize)
        val totalBytesNeeded = SAMPLE_RATE * RECORD_DURATION_SECONDS * (BITS_PER_SAMPLE / 8)
        var totalBytesWritten = 0

        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            ENCODING,
            minBufferSize * 2
        )

        RandomAccessFile(outputFile, "rw").use { wavFile ->
            wavFile.setLength(0)
            wavFile.write(ByteArray(44))

            try {
                recorder.startRecording()
                while (totalBytesWritten < totalBytesNeeded) {
                    val maxReadable = minOf(buffer.size, totalBytesNeeded - totalBytesWritten)
                    val read = recorder.read(buffer, 0, maxReadable)
                    if (read <= 0) continue
                    wavFile.write(buffer, 0, read)
                    totalBytesWritten += read
                }
            } finally {
                recorder.stop()
                recorder.release()
            }

            wavFile.seek(0)
            wavFile.write(buildWavHeader(totalBytesWritten))
        }
    }

    private fun buildWavHeader(audioLength: Int): ByteArray {
        val byteRate = SAMPLE_RATE * (BITS_PER_SAMPLE / 8)
        val totalDataLen = audioLength + 36

        return ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray())
            putInt(totalDataLen)
            put("WAVE".toByteArray())
            put("fmt ".toByteArray())
            putInt(16)
            putShort(1)
            putShort(1)
            putInt(SAMPLE_RATE)
            putInt(byteRate)
            putShort((BITS_PER_SAMPLE / 8).toShort())
            putShort(BITS_PER_SAMPLE.toShort())
            put("data".toByteArray())
            putInt(audioLength)
        }.array()
    }
}
