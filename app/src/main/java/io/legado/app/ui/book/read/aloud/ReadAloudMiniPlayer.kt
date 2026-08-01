package io.legado.app.ui.book.read.aloud

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.Animatable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewOutlineProvider
import android.view.animation.LinearInterpolator
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import io.legado.app.R
import io.legado.app.lib.theme.accentColor
import io.legado.app.model.BookCover
import io.legado.app.model.ReadAloud
import io.legado.app.model.ReadBook
import io.legado.app.service.BaseReadAloudService
import io.legado.app.utils.dpToPx
import java.util.WeakHashMap
import kotlin.math.abs

object ReadAloudMiniPlayer {

    private const val TAG_ID = R.id.read_aloud_mini_player
    private const val ROTATION_DURATION = 16000L
    private const val PREPARATION_VISUAL_DELAY = 120L
    private const val PREPARATION_REVEAL_DELAY = 500L
    private const val PREPARATION_ANIMATION_DURATION = 280L
    private const val COVER_REVEAL_DURATION = 200L
    private val coverAnimators = WeakHashMap<ImageView, ObjectAnimator>()
    private val coverRevealAnimators = WeakHashMap<ImageView, ValueAnimator>()
    private val preparationAnimators = WeakHashMap<View, ValueAnimator>()
    private val preparationVisualRunnables = WeakHashMap<View, Runnable>()
    private val preparationVisualStates = WeakHashMap<View, Boolean>()
    private val preparationRevealRunnables = WeakHashMap<View, Runnable>()
    private val preparationStates = WeakHashMap<View, Boolean>()
    private val launchPendingStates = WeakHashMap<View, Boolean>()
    private val coverLoadKeys = WeakHashMap<ImageView, String>()
    private val preloadedCoverKeys = WeakHashMap<Activity, String>()
    private val preparationInterpolator = PathInterpolator(0.4f, 0f, 0.2f, 1f)
    private val coverRevealInterpolator = PathInterpolator(0.2f, 0f, 0f, 1f)
    private var savedX: Float? = null
    private var savedY: Float? = null

    fun attach(activity: Activity) {
        if (!shouldShowOn(activity)) {
            detach(activity)
            return
        }
        val view = getOrCreateView(activity) ?: return
        refresh(activity)
        restorePosition(view)
        view.bringToFront()
    }

    fun showStarting(activity: Activity) {
        if (!shouldShowOn(activity)) return
        val view = getOrCreateView(activity) ?: return
        launchPendingStates[view] = true
        view.isVisible = true
        render(activity, view, preparing = true, immediatePreparation = true)
        restorePosition(view)
        view.bringToFront()
    }

    fun preloadCover(activity: Activity) {
        val book = ReadBook.book ?: return
        val path = book.getDisplayCover()
        if (path.isNullOrEmpty()) return
        val sourceOrigin = ReadBook.bookSource?.bookSourceUrl
        val loadKey = coverLoadKey(book.bookUrl, path, sourceOrigin)
        if (preloadedCoverKeys[activity] == loadKey) return
        preloadedCoverKeys[activity] = loadKey
        BookCover.load(
            context = activity,
            path = path,
            sourceOrigin = sourceOrigin
        ).preload(42.dpToPx(), 42.dpToPx())
    }

    private fun getOrCreateView(activity: Activity): View? {
        val content = activity.findViewById<FrameLayout>(android.R.id.content) ?: return null
        return content.findViewById<View>(TAG_ID) ?: createView(activity).also {
            content.addView(it)
        }
    }

    fun refresh(activity: Activity) {
        val content = activity.findViewById<FrameLayout>(android.R.id.content) ?: return
        val view = content.findViewById<View>(TAG_ID) ?: return
        val launchPending = launchPendingStates[view] == true
        val serviceRunning = BaseReadAloudService.isRun
        if (serviceRunning) {
            launchPendingStates.remove(view)
        }
        view.isVisible = (serviceRunning || launchPending) && shouldShowOn(activity)
        val cover = view.findViewById<ImageView>(R.id.iv_read_aloud_mini_cover)
        val play = view.findViewById<ImageButton>(R.id.btn_read_aloud_mini_play)
        val status = view.findViewById<TextView>(R.id.tv_read_aloud_mini_status)
        if (!view.isVisible) {
            resetPreparationState(view, status)
            stopCoverRotation(cover)
            (play.drawable as? Animatable)?.stop()
            play.isEnabled = true
            return
        }
        render(activity, view, preparing = launchPending || BaseReadAloudService.isPreparing())
    }

    private fun render(
        activity: Activity,
        view: View,
        preparing: Boolean,
        immediatePreparation: Boolean = false
    ) {
        val cover = view.findViewById<ImageView>(R.id.iv_read_aloud_mini_cover)
        val play = view.findViewById<ImageButton>(R.id.btn_read_aloud_mini_play)
        val status = view.findViewById<TextView>(R.id.tv_read_aloud_mini_status)
        updateCover(activity, cover)
        updatePreparationState(view, status, preparing)
        updatePreparationVisualState(
            view,
            cover,
            play,
            preparing,
            immediatePreparation
        )
    }

    private fun shouldShowOn(activity: Activity): Boolean {
        if (activity is ReadAloudPlayerActivity) {
            return false
        }
        return activity.intent?.getBooleanExtra(
            ReadAloudLauncher.EXTRA_SUPPRESS_MINI_PLAYER,
            false
        ) != true
    }

    fun detach(activity: Activity) {
        val content = activity.findViewById<FrameLayout>(android.R.id.content) ?: return
        content.findViewById<View>(TAG_ID)?.let {
            cancelPreparationVisualDelay(it)
            cancelPreparationReveal(it)
            preparationAnimators.remove(it)?.cancel()
            preparationVisualStates.remove(it)
            preparationStates.remove(it)
            launchPendingStates.remove(it)
            it.findViewById<ImageView>(R.id.iv_read_aloud_mini_cover)?.let { cover ->
                coverLoadKeys.remove(cover)
                coverRevealAnimators.remove(cover)?.cancel()
                stopCoverRotation(cover)
            }
            content.removeView(it)
        }
    }

    private fun createView(activity: Activity): View {
        val capsule = LinearLayout(activity).apply {
            id = TAG_ID
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = capsuleBackground(activity.accentColor)
            elevation = 2.dpToPx().toFloat()
            setPadding(5.dpToPx(), 5.dpToPx(), 9.dpToPx(), 5.dpToPx())
            isClickable = true
            isFocusable = true
        }
        installDragTouch(activity, capsule, capsule) {
            ReadAloudLauncher.openPlayer(activity)
        }
        val cover = ImageView(activity).apply {
            id = R.id.iv_read_aloud_mini_cover
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = circleBackground(
                Color.TRANSPARENT,
                Color.WHITE
            )
            setPadding(1.dpToPx(), 1.dpToPx(), 1.dpToPx(), 1.dpToPx())
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setOval(0, 0, view.width, view.height)
                }
            }
        }
        installDragTouch(activity, capsule, cover) {
            ReadAloudLauncher.openPlayer(activity)
        }
        capsule.addView(cover, LinearLayout.LayoutParams(42.dpToPx(), 42.dpToPx()))
        val play = ImageButton(activity).apply {
            id = R.id.btn_read_aloud_mini_play
            background = playButtonBackground(activity.accentColor)
            setColorFilter(Color.WHITE)
            setPadding(11.dpToPx(), 11.dpToPx(), 11.dpToPx(), 11.dpToPx())
        }
        installDragTouch(activity, capsule, play) {
            if (BaseReadAloudService.isPlay()) {
                ReadAloud.pause(activity)
            } else {
                ReadAloud.resume(activity)
            }
        }
        capsule.addView(
            play,
            LinearLayout.LayoutParams(42.dpToPx(), 42.dpToPx()).apply {
                marginStart = 7.dpToPx()
            }
        )
        val status = TextView(activity).apply {
            id = R.id.tv_read_aloud_mini_status
            alpha = 0f
            gravity = Gravity.CENTER_VERTICAL
            includeFontPadding = false
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
            setTextColor(ColorUtils.setAlphaComponent(Color.WHITE, (255 * 0.92f).toInt()))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(9.dpToPx(), 0, 1.dpToPx(), 0)
            visibility = View.GONE
        }
        capsule.addView(
            status,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT)
        )
        val close = ImageButton(activity).apply {
            id = R.id.btn_read_aloud_mini_close
            setImageResource(R.drawable.ic_read_aloud_mini_close)
            setBackgroundColor(Color.TRANSPARENT)
            setColorFilter(Color.WHITE)
            scaleType = ImageView.ScaleType.CENTER
            setPadding(0, 0, 0, 0)
        }
        installDragTouch(activity, capsule, close) {
            ReadAloud.stop(activity)
            detach(activity)
        }
        capsule.addView(
            close,
            LinearLayout.LayoutParams(30.dpToPx(), 30.dpToPx()).apply {
                marginStart = 8.dpToPx()
            }
        )
        capsule.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.START or Gravity.BOTTOM
        ).apply {
            marginStart = 24.dpToPx()
            bottomMargin = 108.dpToPx()
        }
        return capsule
    }

    private fun updateCover(activity: Activity, cover: ImageView) {
        val path = ReadBook.book?.getDisplayCover()
        val sourceOrigin = ReadBook.bookSource?.bookSourceUrl
        val loadKey = coverLoadKey(ReadBook.book?.bookUrl, path, sourceOrigin)
        if (coverLoadKeys[cover] == loadKey) return
        coverLoadKeys[cover] = loadKey
        coverRevealAnimators.remove(cover)?.cancel()
        cover.animate().cancel()
        cover.imageAlpha = 0
        cover.scaleX = 0.96f
        cover.scaleY = 0.96f
        BookCover.load(
            context = activity,
            path = path,
            sourceOrigin = sourceOrigin
        ).placeholder(ColorDrawable(Color.TRANSPARENT))
            .error(ColorDrawable(Color.TRANSPARENT))
            .addListener(object : RequestListener<Drawable> {
                override fun onLoadFailed(
                    e: GlideException?,
                    model: Any?,
                    target: Target<Drawable?>,
                    isFirstResource: Boolean
                ): Boolean {
                    cover.post {
                        if (coverLoadKeys[cover] == loadKey) {
                            coverRevealAnimators.remove(cover)?.cancel()
                            cover.imageAlpha = 0
                            cover.scaleX = 1f
                            cover.scaleY = 1f
                        }
                    }
                    return false
                }

                override fun onResourceReady(
                    resource: Drawable,
                    model: Any,
                    target: Target<Drawable?>?,
                    dataSource: DataSource,
                    isFirstResource: Boolean
                ): Boolean {
                    cover.post {
                        if (coverLoadKeys[cover] == loadKey) {
                            revealCover(cover, animate = dataSource != DataSource.MEMORY_CACHE)
                        }
                    }
                    return false
                }
            })
            .into(cover)
    }

    private fun revealCover(cover: ImageView, animate: Boolean) {
        coverRevealAnimators.remove(cover)?.cancel()
        if (!animate) {
            cover.imageAlpha = 255
            cover.scaleX = 1f
            cover.scaleY = 1f
            return
        }
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = COVER_REVEAL_DURATION
            interpolator = coverRevealInterpolator
            addUpdateListener { animator ->
                val fraction = animator.animatedValue as Float
                cover.imageAlpha = (255 * fraction).toInt()
                val scale = 0.96f + 0.04f * fraction
                cover.scaleX = scale
                cover.scaleY = scale
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    cover.imageAlpha = 255
                    cover.scaleX = 1f
                    cover.scaleY = 1f
                    coverRevealAnimators.remove(cover)
                }
            })
            coverRevealAnimators[cover] = this
            start()
        }
    }

    private fun coverLoadKey(bookUrl: String?, path: String?, sourceOrigin: String?): String {
        return "${bookUrl.orEmpty()}\n${path.orEmpty()}\n${sourceOrigin.orEmpty()}"
    }

    private fun installDragTouch(
        activity: Activity,
        capsule: View,
        touchView: View,
        clickAction: () -> Unit
    ) {
        val touchSlop = ViewConfiguration.get(activity).scaledTouchSlop
        var downRawX = 0f
        var downRawY = 0f
        var startX = 0f
        var startY = 0f
        var dragging = false
        touchView.setOnTouchListener { _, event ->
            val parentView = capsule.parent as? View ?: return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = capsule.x
                    startY = capsule.y
                    dragging = false
                    parentView.parent?.requestDisallowInterceptTouchEvent(true)
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    if (!dragging && abs(dx) + abs(dy) > touchSlop) {
                        dragging = true
                    }
                    if (dragging) {
                        val margin = 8.dpToPx().toFloat()
                        val maxX = (parentView.width - capsule.width - margin).coerceAtLeast(margin)
                        val maxY = (parentView.height - capsule.height - margin).coerceAtLeast(margin)
                        capsule.x = (startX + dx).coerceIn(margin, maxX)
                        capsule.y = (startY + dy).coerceIn(margin, maxY)
                    }
                    true
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    parentView.parent?.requestDisallowInterceptTouchEvent(false)
                    if (dragging) {
                        savedX = capsule.x
                        savedY = capsule.y
                    } else if (event.actionMasked == MotionEvent.ACTION_UP) {
                        clickAction()
                    }
                    dragging = false
                    true
                }

                else -> false
            }
        }
    }

    private fun restorePosition(view: View) {
        val x = savedX
        val y = savedY
        if (x == null || y == null) return
        view.post {
            val parentView = view.parent as? View ?: return@post
            val margin = 8.dpToPx().toFloat()
            val maxX = (parentView.width - view.width - margin).coerceAtLeast(margin)
            val maxY = (parentView.height - view.height - margin).coerceAtLeast(margin)
            view.x = x.coerceIn(margin, maxX)
            view.y = y.coerceIn(margin, maxY)
        }
    }

    private fun updatePreparationVisualState(
        capsule: View,
        cover: ImageView,
        play: ImageButton,
        preparing: Boolean,
        immediate: Boolean
    ) {
        if (!preparing) {
            cancelPreparationVisualDelay(capsule)
            preparationVisualStates[capsule] = false
            upCoverRotation(cover, preparing = false)
            updatePlayButton(play, preparing = false)
            return
        }
        if (immediate || preparationVisualStates[capsule] == true) {
            cancelPreparationVisualDelay(capsule)
            preparationVisualStates[capsule] = true
            upCoverRotation(cover, preparing = true)
            updatePlayButton(play, preparing = true)
            return
        }
        if (preparationVisualRunnables[capsule] != null) return
        val runnable = Runnable {
            preparationVisualRunnables.remove(capsule)
            val stillPreparing = launchPendingStates[capsule] == true ||
                    BaseReadAloudService.isPreparing()
            if (capsule.isVisible && stillPreparing) {
                preparationVisualStates[capsule] = true
                upCoverRotation(cover, preparing = true)
                updatePlayButton(play, preparing = true)
            }
        }
        preparationVisualRunnables[capsule] = runnable
        capsule.postDelayed(runnable, PREPARATION_VISUAL_DELAY)
    }

    private fun cancelPreparationVisualDelay(capsule: View) {
        preparationVisualRunnables.remove(capsule)?.let(capsule::removeCallbacks)
    }

    private fun updatePreparationState(
        capsule: View,
        status: TextView,
        preparing: Boolean
    ) {
        val label = preparationLabel()
        val previous = preparationStates[capsule]
        val labelChanged = preparing && status.text.toString() != label
        if (preparing) {
            status.text = label
        }
        if (previous == preparing) {
            if (preparing && status.visibility == View.VISIBLE && labelChanged) {
                animatePreparationStatus(capsule, status, expanded = true)
            }
            return
        }
        preparationStates[capsule] = preparing
        if (preparing) {
            schedulePreparationReveal(capsule, status)
        } else {
            cancelPreparationReveal(capsule)
            animatePreparationStatus(capsule, status, expanded = false)
        }
    }

    private fun schedulePreparationReveal(capsule: View, status: TextView) {
        cancelPreparationReveal(capsule)
        val runnable = Runnable {
            preparationRevealRunnables.remove(capsule)
            val stillPreparing = launchPendingStates[capsule] == true ||
                    BaseReadAloudService.isPreparing()
            if (preparationStates[capsule] == true && stillPreparing) {
                animatePreparationStatus(capsule, status, expanded = true)
            }
        }
        preparationRevealRunnables[capsule] = runnable
        capsule.postDelayed(runnable, PREPARATION_REVEAL_DELAY)
    }

    private fun cancelPreparationReveal(capsule: View) {
        preparationRevealRunnables.remove(capsule)?.let(capsule::removeCallbacks)
    }

    private fun animatePreparationStatus(
        capsule: View,
        status: TextView,
        expanded: Boolean
    ) {
        preparationAnimators.remove(capsule)?.cancel()
        if (expanded) {
            status.visibility = View.VISIBLE
        }
        val startWidth = status.layoutParams.width.coerceAtLeast(0)
        val targetWidth = if (expanded) measurePreparationStatusWidth(status) else 0
        val startAlpha = status.alpha
        val targetAlpha = if (expanded) 1f else 0f
        if (startWidth == targetWidth && startAlpha == targetAlpha) {
            if (!expanded) status.visibility = View.GONE
            return
        }
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = PREPARATION_ANIMATION_DURATION
            interpolator = preparationInterpolator
            addUpdateListener { animator ->
                val fraction = animator.animatedValue as Float
                status.layoutParams = status.layoutParams.apply {
                    width = (startWidth + (targetWidth - startWidth) * fraction).toInt()
                }
                status.alpha = startAlpha + (targetAlpha - startAlpha) * fraction
                capsule.requestLayout()
            }
            addListener(object : AnimatorListenerAdapter() {
                private var cancelled = false

                override fun onAnimationCancel(animation: Animator) {
                    cancelled = true
                }

                override fun onAnimationEnd(animation: Animator) {
                    if (cancelled) return
                    status.layoutParams = status.layoutParams.apply { width = targetWidth }
                    status.alpha = targetAlpha
                    if (!expanded) status.visibility = View.GONE
                    preparationAnimators.remove(capsule)
                }
            })
            preparationAnimators[capsule] = this
            start()
        }
    }

    private fun measurePreparationStatusWidth(status: TextView): Int {
        status.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        return status.measuredWidth.coerceAtMost(108.dpToPx())
    }

    private fun resetPreparationState(capsule: View, status: TextView) {
        cancelPreparationVisualDelay(capsule)
        cancelPreparationReveal(capsule)
        preparationAnimators.remove(capsule)?.cancel()
        preparationVisualStates.remove(capsule)
        preparationStates.remove(capsule)
        status.layoutParams = status.layoutParams.apply { width = 0 }
        status.alpha = 0f
        status.visibility = View.GONE
    }

    private fun preparationLabel(): String = when (BaseReadAloudService.preparationStage) {
        BaseReadAloudService.PREPARATION_STORYBOARD -> "生成分镜中…"
        BaseReadAloudService.PREPARATION_CASTING -> "分配发音人中…"
        BaseReadAloudService.PREPARATION_AUDIO -> "准备朗读中…"
        else -> "准备朗读中…"
    }

    private fun updatePlayButton(play: ImageButton, preparing: Boolean) {
        if (preparing) {
            if (play.isEnabled || play.drawable !is Animatable) {
                (play.drawable as? Animatable)?.stop()
                play.setImageResource(R.drawable.avd_read_aloud_loading_bars)
                (play.drawable as? Animatable)?.start()
            }
            play.isEnabled = false
            play.contentDescription = BaseReadAloudService.preparationMessage()
                .ifEmpty { "正在准备朗读…" }
            return
        }
        (play.drawable as? Animatable)?.stop()
        play.isEnabled = true
        val playing = BaseReadAloudService.isPlay()
        play.setImageResource(if (playing) R.drawable.ic_pause_24dp else R.drawable.ic_play_24dp)
        play.contentDescription = if (playing) "暂停朗读" else "继续朗读"
    }

    private fun upCoverRotation(cover: ImageView, preparing: Boolean) {
        val animator = coverAnimators.getOrPut(cover) {
            ObjectAnimator.ofFloat(cover, View.ROTATION, 0f, 360f).apply {
                duration = ROTATION_DURATION
                interpolator = LinearInterpolator()
                repeatCount = ObjectAnimator.INFINITE
            }
        }
        if (BaseReadAloudService.isPlay() && !preparing) {
            if (!animator.isStarted) {
                animator.start()
            } else if (animator.isPaused) {
                animator.resume()
            }
        } else if (animator.isStarted && !animator.isPaused) {
            animator.pause()
        }
    }

    private fun stopCoverRotation(cover: ImageView) {
        coverAnimators.remove(cover)?.cancel()
        cover.rotation = 0f
    }

    private fun capsuleBackground(accent: Int): Drawable {
        val halo = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 28.dpToPx().toFloat()
            setColor(
                ColorUtils.setAlphaComponent(
                    ColorUtils.blendARGB(accent, Color.WHITE, 0.34f),
                    (255 * 0.14f).toInt()
                )
            )
        }
        val fill = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(
                ColorUtils.setAlphaComponent(
                    ColorUtils.blendARGB(accent, Color.WHITE, 0.30f),
                    (255 * 0.38f).toInt()
                ),
                ColorUtils.setAlphaComponent(accent, (255 * 0.34f).toInt()),
                ColorUtils.setAlphaComponent(
                    ColorUtils.blendARGB(accent, Color.BLACK, 0.04f),
                    (255 * 0.36f).toInt()
                )
            )
        ).apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 28.dpToPx().toFloat()
        }
        val topGlow = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(
                ColorUtils.setAlphaComponent(Color.WHITE, (255 * 0.08f).toInt()),
                Color.TRANSPARENT
            )
        ).apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 28.dpToPx().toFloat()
        }
        val lowerShade = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(
                Color.TRANSPARENT,
                ColorUtils.setAlphaComponent(Color.BLACK, (255 * 0.03f).toInt())
            )
        ).apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 28.dpToPx().toFloat()
        }
        return LayerDrawable(arrayOf(halo, fill, topGlow, lowerShade)).apply {
            setLayerInset(1, 1.dpToPx(), 1.dpToPx(), 1.dpToPx(), 1.dpToPx())
            setLayerInset(2, 8.dpToPx(), 3.dpToPx(), 8.dpToPx(), 40.dpToPx())
            setLayerInset(3, 3.dpToPx(), 28.dpToPx(), 3.dpToPx(), 3.dpToPx())
        }
    }

    private fun circleBackground(fill: Int, stroke: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(fill)
            setStroke(1.dpToPx(), ColorUtils.setAlphaComponent(stroke, (255 * 0.20f).toInt()))
        }
    }

    private fun playButtonBackground(accent: Int): Drawable {
        val softHalo = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(ColorUtils.setAlphaComponent(Color.WHITE, (255 * 0.08f).toInt()))
        }
        val glass = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(
                ColorUtils.setAlphaComponent(Color.WHITE, (255 * 0.12f).toInt()),
                ColorUtils.setAlphaComponent(
                    ColorUtils.blendARGB(accent, Color.WHITE, 0.20f),
                    (255 * 0.10f).toInt()
                )
            )
        ).apply {
            shape = GradientDrawable.OVAL
            setStroke(2.dpToPx(), ColorUtils.setAlphaComponent(Color.WHITE, (255 * 0.58f).toInt()))
        }
        return LayerDrawable(arrayOf(softHalo, glass)).apply {
            setLayerInset(1, 3.dpToPx(), 3.dpToPx(), 3.dpToPx(), 3.dpToPx())
        }
    }

}
