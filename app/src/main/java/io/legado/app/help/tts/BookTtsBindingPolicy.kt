package io.legado.app.help.tts

import io.legado.app.data.entities.BookCharacterTtsBinding

internal object BookTtsBindingPolicy {

    const val MIN_AUTO_CONFIDENCE = 0.7f
    const val STABLE_AUTO_CONFIDENCE = 0.85f

    enum class AutoState {
        PENDING,
        PROVISIONAL,
        STABLE,
        PROTECTED
    }

    data class Resolution(
        val binding: BookCharacterTtsBinding,
        val voiceChanged: Boolean
    )

    fun autoState(
        binding: BookCharacterTtsBinding?,
        usableVoiceIds: Set<String>
    ): AutoState {
        binding ?: return AutoState.PENDING
        if (binding.bindingMode != BookCharacterTtsBinding.BindingMode.AUTO) {
            return AutoState.PROTECTED
        }
        binding.voiceId?.takeIf { it.isNotBlank() && it in usableVoiceIds }
            ?: return AutoState.PENDING
        return if (binding.autoConfidence >= STABLE_AUTO_CONFIDENCE) {
            AutoState.STABLE
        } else {
            AutoState.PROVISIONAL
        }
    }

    fun shouldEvaluate(
        binding: BookCharacterTtsBinding?,
        usableVoiceIds: Set<String>,
        evidenceSignature: String,
        replaceAuto: Boolean
    ): Boolean {
        binding ?: return true
        if (binding.bindingMode != BookCharacterTtsBinding.BindingMode.AUTO) return false
        if (replaceAuto) return true
        return when (autoState(binding, usableVoiceIds)) {
            AutoState.PENDING,
            AutoState.PROVISIONAL -> binding.autoEvidenceSignature != evidenceSignature

            AutoState.STABLE,
            AutoState.PROTECTED -> false
        }
    }

    /**
     * 播放前只等待从未完成选音、或原音色已经失效的自动绑定。
     * 已经评估但证据不足的空绑定也算“分配完成”，交给引擎默认音色继续播放；
     * 后续证据复评仍走后台富化，不能重新阻塞缓存章节首声。
     */
    fun needsPlaybackAssignment(
        binding: BookCharacterTtsBinding?,
        usableVoiceIds: Set<String>
    ): Boolean {
        binding ?: return true
        if (binding.bindingMode != BookCharacterTtsBinding.BindingMode.AUTO) return false
        val voiceId = binding.voiceId?.takeIf { it.isNotBlank() }
        if (voiceId != null) return voiceId !in usableVoiceIds
        return binding.autoEvidenceSignature.isBlank()
    }

    fun resolve(
        current: BookCharacterTtsBinding?,
        newBinding: () -> BookCharacterTtsBinding,
        usableVoiceIds: Set<String>,
        evidenceSignature: String,
        acceptedVoiceId: String?,
        confidence: Float,
        replaceAuto: Boolean,
        now: Long
    ): Resolution {
        require(current == null || current.bindingMode == BookCharacterTtsBinding.BindingMode.AUTO)
        val oldUsableVoiceId = current?.voiceId
            ?.takeIf { it.isNotBlank() && it in usableVoiceIds }
        val oldConfidence = current?.autoConfidence ?: 0f
        val normalizedConfidence = confidence.coerceIn(0f, 1f)
        val selectedVoiceId = when {
            acceptedVoiceId == null -> oldUsableVoiceId
            oldUsableVoiceId == null -> acceptedVoiceId
            replaceAuto -> acceptedVoiceId
            acceptedVoiceId == oldUsableVoiceId -> acceptedVoiceId
            oldConfidence < STABLE_AUTO_CONFIDENCE &&
                normalizedConfidence >= STABLE_AUTO_CONFIDENCE -> acceptedVoiceId

            else -> oldUsableVoiceId
        }
        val selectedConfidence = when {
            selectedVoiceId == null -> normalizedConfidence
            selectedVoiceId == acceptedVoiceId && selectedVoiceId == oldUsableVoiceId ->
                maxOf(oldConfidence, normalizedConfidence)

            selectedVoiceId == acceptedVoiceId -> normalizedConfidence
            else -> oldConfidence
        }
        val stored = (current ?: newBinding()).copy(
            voiceId = selectedVoiceId,
            bindingMode = BookCharacterTtsBinding.BindingMode.AUTO,
            autoConfidence = selectedConfidence,
            autoEvidenceSignature = evidenceSignature,
            updatedAt = now
        )
        return Resolution(
            binding = stored,
            voiceChanged = selectedVoiceId != null && selectedVoiceId != oldUsableVoiceId
        )
    }
}
