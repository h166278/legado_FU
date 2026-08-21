package io.legado.app.ui.config

import io.legado.app.help.tts.generateTtsRandomNumber
import io.legado.app.help.tts.isValidTtsRandomNumber
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal fun String.toTtsEngineFormFieldType(): TtsEngineFormFieldType {
    return when (this) {
        "password" -> TtsEngineFormFieldType.PASSWORD
        "number" -> TtsEngineFormFieldType.NUMBER
        "select" -> TtsEngineFormFieldType.SELECT
        "boolean" -> TtsEngineFormFieldType.BOOLEAN
        "random_number" -> TtsEngineFormFieldType.RANDOM_NUMBER
        else -> TtsEngineFormFieldType.TEXT
    }
}

internal fun normalizeTtsEngineFormFieldValue(
    type: String,
    value: String,
    digits: Int,
    allowLeadingZero: Boolean,
    randomNumberFactory: (Int, Boolean) -> String = { count, leadingZero ->
        generateTtsRandomNumber(count, leadingZero)
    }
): String {
    return if (
        type == "random_number" &&
        !isValidTtsRandomNumber(value, digits, allowLeadingZero)
    ) {
        randomNumberFactory(digits, allowLeadingZero)
    } else {
        value
    }
}

internal fun mergeTtsEngineOptionValues(
    sourceValues: Map<String, String>,
    displayedValues: Map<String, String>,
    schemaMatchesCurrentScript: Boolean
): Map<String, String> {
    return if (schemaMatchesCurrentScript) {
        displayedValues
    } else {
        sourceValues + displayedValues
    }
}

internal fun buildTtsEngineFormOptions(
    currentValue: String,
    options: List<TtsEngineFormOption>
): List<TtsEngineFormOption> {
    return buildList {
        if (currentValue.isNotBlank() && options.none { it.value == currentValue }) {
            add(TtsEngineFormOption(currentValue, currentValue))
        }
        addAll(options)
    }.distinctBy { it.value }
}

internal fun shouldSaveTtsEngineFieldImmediately(type: TtsEngineFormFieldType): Boolean {
    return type == TtsEngineFormFieldType.SELECT || type == TtsEngineFormFieldType.BOOLEAN
}

internal fun ttsLatencyProbeUrl(requestUrl: String): String? {
    val httpUrl = when {
        requestUrl.startsWith("ws://", ignoreCase = true) ->
            "http://${requestUrl.substring(5)}"
        requestUrl.startsWith("wss://", ignoreCase = true) ->
            "https://${requestUrl.substring(6)}"
        else -> requestUrl
    }.toHttpUrlOrNull() ?: return null
    return httpUrl.newBuilder()
        .query(null)
        .fragment(null)
        .build()
        .toString()
}
