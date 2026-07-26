package io.legado.app.help.tts

import io.legado.app.utils.MD5Utils
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.putPrefBoolean
import splitties.init.appCtx

/**
 * 按书保存角色发现和自动选音开关。
 *
 * 两个开关只控制后续自动操作，不删除已经存在的角色或发音人绑定。
 */
object BookTtsAutomationConfig {

    data class Settings(
        val autoCreateTemporaryRoles: Boolean = true,
        val autoAssignVoices: Boolean = true
    )

    fun get(workKey: String): Settings = Settings(
        autoCreateTemporaryRoles = appCtx.getPrefBoolean(key(ROLE_KEY_PREFIX, workKey), true),
        autoAssignVoices = appCtx.getPrefBoolean(key(VOICE_KEY_PREFIX, workKey), true)
    )

    fun setAutoCreateTemporaryRoles(workKey: String, enabled: Boolean) {
        appCtx.putPrefBoolean(key(ROLE_KEY_PREFIX, workKey), enabled)
    }

    fun setAutoAssignVoices(workKey: String, enabled: Boolean) {
        appCtx.putPrefBoolean(key(VOICE_KEY_PREFIX, workKey), enabled)
    }

    private fun key(prefix: String, workKey: String): String {
        return "$prefix:${MD5Utils.md5Encode16(workKey)}"
    }

    private const val ROLE_KEY_PREFIX = "bookTtsAutoCreateTemporaryRoles"
    private const val VOICE_KEY_PREFIX = "bookTtsAutoAssignVoices"
}
