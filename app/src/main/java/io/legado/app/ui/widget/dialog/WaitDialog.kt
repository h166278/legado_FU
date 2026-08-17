package io.legado.app.ui.widget.dialog

import android.app.Dialog
import android.content.Context
import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import android.view.ViewGroup
import android.view.Window
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.design.theme.NgTheme

@Suppress("unused")
class WaitDialog(private val dialogContext: Context) : Dialog(dialogContext) {

    private var message by mutableStateOf(dialogContext.getString(R.string.loading))

    init {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setCanceledOnTouchOutside(false)
        setContentView(
            ComposeView(dialogContext).apply {
                setBackgroundColor(AndroidColor.TRANSPARENT)
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                setContent {
                    NgAppTheme(updateSystemBars = false) {
                        WaitDialogContent(message)
                    }
                }
            },
        )
        window?.setBackgroundDrawable(ColorDrawable(AndroidColor.TRANSPARENT))
    }

    override fun show() {
        super.show()
        window?.setLayout(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
    }

    fun setText(text: String): WaitDialog {
        message = text
        return this
    }

    fun setText(res: Int): WaitDialog {
        message = dialogContext.getString(res)
        return this
    }
}

@Composable
private fun WaitDialogContent(message: String) {
    Surface(
        color = Color(NgTheme.colors.surface),
        shape = RoundedCornerShape(NgTheme.shapes.mediumDp.dp),
        shadowElevation = NgTheme.effects.overlayElevationDp.dp,
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(30.dp),
                color = Color(NgTheme.colors.primary),
                strokeWidth = 2.5.dp,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = message,
                color = Color(NgTheme.colors.onSurface),
                fontSize = 14.sp,
                lineHeight = 18.sp,
                maxLines = 2,
            )
        }
    }
}
