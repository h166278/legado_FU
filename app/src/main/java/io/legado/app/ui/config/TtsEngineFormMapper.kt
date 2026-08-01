package io.legado.app.ui.config

import io.legado.app.help.tts.generateTtsRandomNumber
import io.legado.app.help.tts.isValidTtsRandomNumber

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
