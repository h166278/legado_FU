package io.legado.app.data.entities

import android.annotation.SuppressLint
import android.os.Parcelable
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Ignore
import androidx.room.Index
import io.legado.app.constant.AppLog
import io.legado.app.constant.AppPattern
import io.legado.app.data.appDb
import io.legado.app.exception.RegexTimeoutException
import io.legado.app.help.RuleBigDataHelp
import io.legado.app.help.config.AppConfig
import io.legado.app.model.analyzeRule.AnalyzeUrl
import io.legado.app.model.analyzeRule.RuleDataInterface
import io.legado.app.utils.ChineseUtils
import io.legado.app.utils.GSON
import io.legado.app.utils.MD5Utils
import io.legado.app.utils.NetworkUtils
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.replace
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.CancellationException
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import splitties.init.appCtx

@Parcelize
@Entity(
    tableName = "chapters",
    primaryKeys = ["url", "bookUrl"],
    indices = [(Index(value = ["bookUrl"], unique = false)),
        (Index(value = ["bookUrl", "index"], unique = true))],
    foreignKeys = [(ForeignKey(
        entity = Book::class,
        parentColumns = ["bookUrl"],
        childColumns = ["bookUrl"],
        onDelete = ForeignKey.CASCADE
    ))]
)    // 删除书籍时自动删除章节
data class BookChapter(
    var url: String = "",               // 章节地址
    var title: String = "",             // 章节标题
    var isVolume: Boolean = false,      // 是否是卷名
    var baseUrl: String = "",           // 用来拼接相对url
    var bookUrl: String = "",           // 书籍地址
    var index: Int = 0,                 // 章节序号
    var isVip: Boolean = false,         // 是否VIP
    var isPay: Boolean = false,         // 是否已购买
    var resourceUrl: String? = null,    // 音频真实URL
    var tag: String? = null,            // 更新时间或其他章节附加信息
    var wordCount: String? = null,      // 本章节字数
    var start: Long? = null,            // 章节起始位置
    var end: Long? = null,              // 章节终止位置
    var startFragmentId: String? = null,  //EPUB书籍当前章节的fragmentId
    var endFragmentId: String? = null,    //EPUB书籍下一章节的fragmentId
    var variable: String? = null,        //变量
    var imgUrl: String? = null // 标题段评图或者视频封面
) : Parcelable, RuleDataInterface {

    @delegate:Transient
    @delegate:Ignore
    @IgnoredOnParcel
    override val variableMap: HashMap<String, String> by lazy {
        GSON.fromJsonObject<HashMap<String, String>>(variable).getOrNull() ?: hashMapOf()
    }

    fun putImgUrl(value: String?) {
        imgUrl =value
        update()
    }

    fun putLyric(value: String?) { //存入歌词文本
        if (super.putVariable("lyric", value)) {
            variable = GSON.toJson(variableMap)
            update()
        }
    }

    fun putDanmaku(value: String?) { //存入弹幕文本
        if (super.putVariable("danmaku", value)) {
            variable = GSON.toJson(variableMap)
            update()
        }
    }

    fun update() {
        appDb.bookChapterDao.update(this)
    }

    @Ignore
    @IgnoredOnParcel
    var titleMD5: String? = null

    override fun putVariable(key: String, value: String?): Boolean {
        if (super.putVariable(key, value)) {
            variable = GSON.toJson(variableMap)
        }
        return true
    }

    override fun putBigVariable(key: String, value: String?) {
        RuleBigDataHelp.putChapterVariable(bookUrl, url, key, value)
    }

    override fun getBigVariable(key: String): String? {
        return RuleBigDataHelp.getChapterVariable(bookUrl, url, key)
    }

    override fun hashCode() = url.hashCode()

    override fun equals(other: Any?): Boolean {
        if (other is BookChapter) {
            return other.url == url
        }
        return false
    }

    fun primaryStr(): String {
        return bookUrl + url
    }

    fun getDisplayTitle(
        replaceRules: List<ReplaceRule>? = null,
        useReplace: Boolean = true,
        chineseConvert: Boolean = true,
        replaceBook: ReplaceBook? = null
    ): String {
        var displayTitle = title.replace(AppPattern.rnRegex, "")
        if (chineseConvert) {
            when (AppConfig.chineseConverterType) {
                1 -> displayTitle = ChineseUtils.t2s(displayTitle)
                2 -> displayTitle = ChineseUtils.s2t(displayTitle)
            }
        }
        if (useReplace && replaceRules != null) kotlin.run {
            replaceRules.forEach { item ->
                if (item.pattern.isNotEmpty()) {
                    try {
                        val mDisplayTitle = if (item.isRegex) {
                            displayTitle.replace(
                                item.name,
                                item.regex,
                                item.replacement,
                                item.getValidTimeoutMillisecond(),
                                this@BookChapter,
                                replaceBook
                            )
                        } else {
                            displayTitle.replace(item.pattern, item.replacement)
                        }
                        if (mDisplayTitle.isNotBlank()) {
                            displayTitle = mDisplayTitle
                        }
                    } catch (_: RegexTimeoutException) {
                        item.isEnabled = false
                        appDb.replaceRuleDao.update(item)
                    } catch (_: CancellationException) {
                        return@run
                    } catch (e: Exception) {
                        AppLog.put("${item.name}替换出错\n替换内容\n${displayTitle}", e)
                        appCtx.toastOnUi("${item.name}替换出错")
                    }
                }
            }
        }
        if (!isVolume) {
            displayTitle = displayTitle.toChineseChapterTitle()
        }
        return displayTitle
    }

    /**
     * 将「数字.标题」形式的章节标题转为「第N章 标题」。
     * 仅处理 1–5 位阿拉伯数字 + 常见分隔符开头、且原本不带「第/卷」的标题，其余原样返回。
     */
    private fun String.toChineseChapterTitle(): String {
        val m = Regex("^\\s*(\\d{1,5})\\s*[.．、,，:：_—\\-]\\s*(.*)$").find(this)
            ?: return this
        val num = m.groupValues[1].toIntOrNull() ?: return this
        if (num !in 1..99999) return this
        val tail = m.groupValues[2]
        return "第${num.toChineseNumber()}章" + if (tail.isNotEmpty()) " $tail" else ""
    }

    /** 整数（1–99999）转中文数字，支持万以内，如 938 → 九百三十八。 */
    private fun Int.toChineseNumber(): String {
        if (this == 0) return "零"
        val digits = "零一二三四五六七八九"
        val units = arrayOf("", "十", "百", "千", "万")
        var n = this
        var result = ""
        var zeroPending = false
        var pos = 0
        while (n > 0) {
            val d = n % 10
            if (d == 0) {
                if (result.isNotEmpty() && !zeroPending) zeroPending = true
            } else {
                if (zeroPending) {
                    result = "零$result"
                    zeroPending = false
                }
                result = digits[d] + units[pos] + result
            }
            n /= 10
            pos++
        }
        return result.replace(Regex("^一十"), "十")
    }

    fun getAbsoluteURL(): String {
        //二级目录解析的卷链接为空 返回目录页的链接
        if (url.startsWith(title) && isVolume) return baseUrl
        val urlMatcher = AnalyzeUrl.paramPattern.matcher(url)
        val urlBefore = if (urlMatcher.find()) url.substring(0, urlMatcher.start()) else url
        val urlAbsoluteBefore = NetworkUtils.getAbsoluteURL(baseUrl, urlBefore)
        return if (urlBefore.length == url.length) {
            urlAbsoluteBefore
        } else {
            "$urlAbsoluteBefore," + url.substring(urlMatcher.end())
        }
    }

    private fun ensureTitleMD5Init() {
        if (titleMD5 == null) {
            titleMD5 = MD5Utils.md5Encode16(title)
        }
    }

    @SuppressLint("DefaultLocale")
    @Suppress("unused")
    fun getFileName(suffix: String = "nb"): String {
        ensureTitleMD5Init()
        return String.format("%05d-%s.%s", index, titleMD5, suffix)
    }

    @SuppressLint("DefaultLocale")
    @Suppress("unused")
    fun getFontName(): String {
        ensureTitleMD5Init()
        return String.format("%05d-%s.ttf", index, titleMD5)
    }
}

