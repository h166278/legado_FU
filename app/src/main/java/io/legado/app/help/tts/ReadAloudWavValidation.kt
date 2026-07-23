package io.legado.app.help.tts

import io.legado.app.exception.NoStackTraceException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile
import kotlin.math.max
import kotlin.math.sqrt

private const val WAV_VALIDATION_ATTEMPTS = 3
private const val MIN_SPEECH_UNITS_TO_VALIDATE = 12
private const val MIN_SECONDS_PER_SPEECH_UNIT = 0.075
private const val TAIL_WINDOW_MILLIS = 100

internal data class AbruptWavTruncation(
    val durationMillis: Long,
    val minimumExpectedMillis: Long,
    val speechUnits: Int,
    val tailRms: Double,
    val overallRms: Double
)

internal suspend fun writeReadAloudAudioWithWavRetry(
    target: File,
    text: String,
    onRejected: (issue: AbruptWavTruncation, nextAttempt: Int) -> Unit = { _, _ -> },
    openInput: suspend () -> InputStream
): File {
    target.takeIf(File::isFile)?.let { cached ->
        if (detectAbruptWavTruncation(cached, text) == null) {
            return cached
        }
        cached.delete()
    }

    var lastIssue: AbruptWavTruncation? = null
    repeat(WAV_VALIDATION_ATTEMPTS) { attempt ->
        currentCoroutineContext().ensureActive()
        writeReadAloudAudioAtomically(target, openInput())
        val issue = detectAbruptWavTruncation(target, text)
        if (issue == null) {
            return target
        }
        lastIssue = issue
        target.delete()
        if (attempt < WAV_VALIDATION_ATTEMPTS - 1) {
            onRejected(issue, attempt + 2)
        }
    }

    val issue = requireNotNull(lastIssue)
    throw NoStackTraceException(
        "TTS连续返回句中截断音频（${issue.durationMillis}ms，文本长度 ${issue.speechUnits}）"
    )
}

internal fun detectAbruptWavTruncation(
    file: File,
    text: String
): AbruptWavTruncation? {
    val speechUnits = countSpeechUnits(text)
    if (speechUnits < MIN_SPEECH_UNITS_TO_VALIDATE || !file.isFile) return null
    val metrics = runCatching { readPcmWavMetrics(file) }.getOrNull() ?: return null
    val minimumExpectedMillis =
        (speechUnits * MIN_SECONDS_PER_SPEECH_UNIT * 1_000).toLong()
    if (metrics.durationMillis >= minimumExpectedMillis) return null

    val abruptTailThreshold = max(300.0, metrics.overallRms * 0.18)
    if (metrics.tailRms < abruptTailThreshold) return null
    return AbruptWavTruncation(
        durationMillis = metrics.durationMillis,
        minimumExpectedMillis = minimumExpectedMillis,
        speechUnits = speechUnits,
        tailRms = metrics.tailRms,
        overallRms = metrics.overallRms
    )
}

private data class PcmWavMetrics(
    val durationMillis: Long,
    val tailRms: Double,
    val overallRms: Double
)

private fun readPcmWavMetrics(file: File): PcmWavMetrics? {
    RandomAccessFile(file, "r").use { input ->
        if (input.length() < 44L || input.readAscii(4) != "RIFF") return null
        input.skipBytes(4)
        if (input.readAscii(4) != "WAVE") return null

        var audioFormat = 0
        var channels = 0
        var sampleRate = 0L
        var bitsPerSample = 0
        var dataOffset = -1L
        var dataSize = -1L
        while (input.filePointer + 8 <= input.length()) {
            val chunkId = input.readAscii(4)
            val chunkSize = input.readUInt32Le()
            val chunkStart = input.filePointer
            if (chunkSize < 0 || chunkStart + chunkSize > input.length()) return null
            when (chunkId) {
                "fmt " -> {
                    if (chunkSize < 16) return null
                    audioFormat = input.readUInt16Le()
                    channels = input.readUInt16Le()
                    sampleRate = input.readUInt32Le()
                    input.skipBytes(6)
                    bitsPerSample = input.readUInt16Le()
                }

                "data" -> {
                    dataOffset = chunkStart
                    dataSize = chunkSize
                }
            }
            input.seek(chunkStart + chunkSize + (chunkSize and 1L))
            if (dataOffset >= 0 && audioFormat != 0) break
        }
        if (audioFormat != 1 || bitsPerSample != 16 || channels <= 0 || sampleRate <= 0L ||
            dataOffset < 0 || dataSize <= 0L
        ) {
            return null
        }

        val bytesPerFrame = channels * 2L
        val frameCount = dataSize / bytesPerFrame
        if (frameCount <= 0L) return null
        val durationMillis = frameCount * 1_000L / sampleRate
        val tailFrames = (sampleRate * TAIL_WINDOW_MILLIS / 1_000L).coerceAtLeast(1L)
        val tailBytes = (tailFrames * bytesPerFrame).coerceAtMost(dataSize)

        val overallRms = input.pcm16Rms(dataOffset, dataSize)
        val tailRms = input.pcm16Rms(dataOffset + dataSize - tailBytes, tailBytes)
        return PcmWavMetrics(durationMillis, tailRms, overallRms)
    }
}

private fun RandomAccessFile.pcm16Rms(offset: Long, byteCount: Long): Double {
    seek(offset)
    val buffer = ByteArray(8_192)
    var remaining = byteCount
    var squareSum = 0.0
    var sampleCount = 0L
    while (remaining >= 2L) {
        val count = read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
        if (count <= 0) break
        val evenCount = count - (count and 1)
        var index = 0
        while (index < evenCount) {
            val sample = ((buffer[index].toInt() and 0xff) or
                    (buffer[index + 1].toInt() shl 8)).toShort().toInt()
            squareSum += sample.toDouble() * sample
            sampleCount++
            index += 2
        }
        remaining -= count
    }
    return if (sampleCount == 0L) 0.0 else sqrt(squareSum / sampleCount)
}

private fun countSpeechUnits(text: String): Int {
    var count = 0
    var inAsciiWord = false
    text.forEach { char ->
        when {
            char.isCjkCharacter() -> {
                count++
                inAsciiWord = false
            }

            char.isLetterOrDigit() && char.code < 128 -> {
                if (!inAsciiWord) count++
                inAsciiWord = true
            }

            char.isLetterOrDigit() -> {
                count++
                inAsciiWord = false
            }

            else -> inAsciiWord = false
        }
    }
    return count
}

private fun Char.isCjkCharacter(): Boolean = code in 0x3400..0x4DBF ||
        code in 0x4E00..0x9FFF || code in 0xF900..0xFAFF

private fun RandomAccessFile.readAscii(length: Int): String {
    val bytes = ByteArray(length)
    readFully(bytes)
    return bytes.toString(Charsets.US_ASCII)
}

private fun RandomAccessFile.readUInt16Le(): Int {
    val low = read()
    val high = read()
    if (low < 0 || high < 0) return -1
    return low or (high shl 8)
}

private fun RandomAccessFile.readUInt32Le(): Long {
    var value = 0L
    repeat(4) { index ->
        val next = read()
        if (next < 0) return -1L
        value = value or (next.toLong() shl (index * 8))
    }
    return value
}
