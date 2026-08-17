package io.legado.app.ui.widget.dialog

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.viewinterop.AndroidView
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.DownsampleStrategy
import com.bumptech.glide.request.RequestOptions
import io.legado.app.R
import io.legado.app.base.BaseComposeDialogFragment
import io.legado.app.help.book.BookHelp
import io.legado.app.help.glide.ImageLoader
import io.legado.app.help.glide.OkHttpModelLoader
import io.legado.app.model.BookCover
import io.legado.app.model.ImageProvider
import io.legado.app.model.ReadBook
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.widget.image.PhotoView
import io.legado.app.utils.setLayout

/** 全屏图片预览；Compose 接管外壳，PhotoView 继续提供缩放手势。 */
class PhotoDialog() : BaseComposeDialogFragment() {

    constructor(src: String, sourceOrigin: String? = null, isBook: Boolean = false) : this() {
        arguments = Bundle().apply {
            putString(ARG_SRC, src)
            putString(ARG_SOURCE_ORIGIN, sourceOrigin)
            putBoolean(ARG_IS_BOOK, isBook)
        }
    }

    override fun onStart() {
        super.onStart()
        setLayout(1f, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        val args = arguments ?: run {
            dismissAllowingStateLoss()
            return
        }
        val src = args.getString(ARG_SRC) ?: run {
            dismissAllowingStateLoss()
            return
        }
        val sourceOrigin = args.getString(ARG_SOURCE_ORIGIN)
        val isBook = args.getBoolean(ARG_IS_BOOK)
        (view as ComposeView).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                NgAppTheme(updateSystemBars = false) {
                    PhotoDialogContent(
                        src = src,
                        sourceOrigin = sourceOrigin,
                        isBook = isBook,
                    )
                }
            }
        }
    }

    private companion object {
        const val ARG_SRC = "src"
        const val ARG_SOURCE_ORIGIN = "sourceOrigin"
        const val ARG_IS_BOOK = "isBook"
    }
}

@SuppressLint("CheckResult")
@Composable
private fun PhotoDialogContent(
    src: String,
    sourceOrigin: String?,
    isBook: Boolean,
) {
    AndroidView(
        factory = { context ->
            PhotoView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                loadPhoto(
                    photoView = this,
                    src = src,
                    sourceOrigin = sourceOrigin,
                    isBook = isBook,
                )
            }
        },
        modifier = Modifier
            .fillMaxSize()
            .background(colorResource(R.color.background)),
    )
}

@SuppressLint("CheckResult")
private fun loadPhoto(
    photoView: PhotoView,
    src: String,
    sourceOrigin: String?,
    isBook: Boolean,
) {
    ImageProvider.get(src)?.let {
        photoView.setImageBitmap(it)
        return
    }
    val file = if (isBook) ReadBook.book?.let { book ->
        BookHelp.getImage(book, src)
    } else null
    if (file?.exists() == true) {
        ImageLoader.load(photoView.context, file)
            .error(R.drawable.image_loading_error)
            .dontTransform()
            .downsample(DownsampleStrategy.NONE)
            .diskCacheStrategy(DiskCacheStrategy.NONE)
            .into(photoView)
    } else {
        ImageLoader.load(photoView.context, src).apply {
            sourceOrigin?.let {
                apply(RequestOptions().set(OkHttpModelLoader.sourceOriginOption, it))
            }
        }.error(if (isBook) BookCover.defaultDrawable else R.drawable.image_loading_error)
            .dontTransform()
            .downsample(DownsampleStrategy.NONE)
            .into(photoView)
    }
}
