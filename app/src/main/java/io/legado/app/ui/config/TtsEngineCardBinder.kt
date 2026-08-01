package io.legado.app.ui.config

import android.content.Context
import io.legado.app.R
import io.legado.app.databinding.ItemTtsEngineBinding
import io.legado.app.help.tts.TtsEngineSetting
import io.legado.app.help.tts.TtsEngineType
import io.legado.app.lib.theme.accentColor
import io.legado.app.ui.design.components.NgManagementTrailing
import io.legado.app.ui.design.components.NgStatusTagSpec
import io.legado.app.ui.design.components.NgStatusTagVariant

object TtsEngineCardBinder {

    fun bind(
        context: Context,
        binding: ItemTtsEngineBinding,
        engine: TtsEngineSetting,
        trailing: Trailing = Trailing.DRAG
    ) = binding.run {
        root.apply {
            setLeadingImage(
                iconRes = R.drawable.ic_ai_capability_tts,
                contentDescription = context.getString(R.string.speak_engine),
                tint = context.accentColor
            )
            setTitle(engine.name)
            setSummary(null)
            setHeaderTags(emptyList())
            setDetailTags(
                listOf(
                    NgStatusTagSpec(
                        text = context.getString(
                            if (engine.enabled) R.string.enabled else R.string.disabled
                        ),
                        variant = if (engine.enabled) {
                            NgStatusTagVariant.SUCCESS
                        } else {
                            NgStatusTagVariant.WARNING
                        }
                    ),
                    NgStatusTagSpec(
                        text = when (engine.type) {
                            TtsEngineType.SYSTEM ->
                                context.getString(R.string.tts_engine_type_system)
                            TtsEngineType.SCRIPT ->
                                context.getString(R.string.tts_engine_type_script)
                        },
                        variant = NgStatusTagVariant.INFO
                    ),
                    NgStatusTagSpec(
                        text = when {
                            engine.type == TtsEngineType.SYSTEM ->
                                context.getString(R.string.character_tts_system_default_voice)
                            engine.effectiveVoices().isEmpty() ->
                                context.getString(R.string.tts_engine_voice_not_loaded)
                            else -> context.getString(
                                R.string.tts_engine_voice_count,
                                engine.effectiveVoices().size
                            )
                        },
                        variant = NgStatusTagVariant.INFO
                    )
                )
            )
            setSelectionIndicatorVisible(
                visible = trailing == Trailing.SELECTED,
                color = context.accentColor
            )
            setTrailing(
                trailing = if (trailing == Trailing.DRAG) {
                    NgManagementTrailing.DRAG
                } else {
                    NgManagementTrailing.NONE
                },
                contentDescription = context.getString(R.string.ai_provider_drag_sort)
            )
        }
    }

    enum class Trailing {
        DRAG,
        SELECTED,
        NONE
    }
}
