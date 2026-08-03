package io.legado.app.ui.config

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.ui.design.components.compose.NgSlider
import io.legado.app.ui.design.components.compose.NgSliderVariant
import io.legado.app.ui.design.theme.NgTheme
import kotlin.math.roundToInt

@Composable
internal fun TtsVoiceParamsSliderPanel(
    speed: Int,
    volume: Int,
    pitch: Int,
    onSpeedChange: (Int) -> Unit,
    onVolumeChange: (Int) -> Unit,
    onPitchChange: (Int) -> Unit,
    onValueChangeFinished: () -> Unit
) {
    Column {
        TtsVoiceParamSliderRow(
            label = stringResource(R.string.tts_speed),
            value = speed,
            onValueChange = onSpeedChange,
            onValueChangeFinished = onValueChangeFinished
        )
        TtsVoiceParamSliderRow(
            label = stringResource(R.string.tts_volume),
            value = volume,
            onValueChange = onVolumeChange,
            onValueChangeFinished = onValueChangeFinished
        )
        TtsVoiceParamSliderRow(
            label = stringResource(R.string.tts_pitch),
            value = pitch,
            onValueChange = onPitchChange,
            onValueChangeFinished = onValueChangeFinished
        )
    }
}

@Composable
private fun TtsVoiceParamSliderRow(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    onValueChangeFinished: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            modifier = Modifier.width(40.dp),
            color = Color(NgTheme.colors.onSurfaceVariant),
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 14.sp,
                lineHeight = 18.sp
            )
        )
        NgSlider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.roundToInt()) },
            valueRange = 0f..100f,
            modifier = Modifier.weight(1f),
            variant = NgSliderVariant.CONTINUOUS,
            onValueChangeFinished = onValueChangeFinished
        )
        Text(
            text = value.toString(),
            modifier = Modifier.width(36.dp),
            color = Color(NgTheme.colors.onSurfaceVariant),
            textAlign = TextAlign.End,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 14.sp,
                lineHeight = 18.sp
            )
        )
    }
}
