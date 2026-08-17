package io.legado.app.ui.main.bookshelf

import android.content.DialogInterface
import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.base.BaseComposeDialogFragment
import io.legado.app.ui.design.components.NgButtonVariant
import io.legado.app.ui.design.components.NgDialogVariant
import io.legado.app.ui.design.components.compose.NgDialog
import io.legado.app.ui.design.components.compose.NgFormActionButton
import io.legado.app.ui.design.components.compose.NgFormActionButtonAppearance
import io.legado.app.ui.design.components.compose.NgFormField
import io.legado.app.ui.design.components.compose.NgFormFieldVariant
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.design.theme.NgTheme
import io.legado.app.ui.widget.dialog.applyNgDialogWindow

class BookshelfActionDialog : BaseComposeDialogFragment() {

    private val callback get() = parentFragment as? Callback

    override fun onStart() {
        super.onStart()
        applyNgDialogWindow()
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        val mode = arguments?.getInt(ARG_MODE, MODE_INVALID) ?: MODE_INVALID
        if (mode !in MODE_ADD_URL..MODE_EXPORT_SUCCESS) {
            dismissAllowingStateLoss()
            return
        }
        val initialValue = arguments?.getString(ARG_VALUE).orEmpty()
        (view as ComposeView).apply {
            setBackgroundColor(AndroidColor.TRANSPARENT)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                NgAppTheme(updateSystemBars = false) {
                    BookshelfActionDialogContent(
                        mode = mode,
                        initialValue = initialValue,
                        onCancel = ::dismissAllowingStateLoss,
                        onSelectFile = {
                            dismissAllowingStateLoss()
                            callback?.onBookshelfImportFileRequested()
                        },
                        onConfirm = { value ->
                            dismissAllowingStateLoss()
                            when (mode) {
                                MODE_ADD_URL -> callback?.onBookshelfAddUrlConfirmed(value)
                                MODE_IMPORT_BOOKSHELF -> {
                                    callback?.onBookshelfImportConfirmed(value)
                                }
                                MODE_EXPORT_SUCCESS -> {
                                    callback?.onBookshelfExportPathCopied(value)
                                }
                            }
                        },
                    )
                }
            }
        }
    }

    interface Callback {
        fun onBookshelfAddUrlConfirmed(value: String)
        fun onBookshelfImportConfirmed(value: String)
        fun onBookshelfImportFileRequested()
        fun onBookshelfExportPathCopied(value: String)
    }

    companion object {
        private const val ARG_MODE = "mode"
        private const val ARG_VALUE = "value"
        private const val MODE_INVALID = -1
        internal const val MODE_ADD_URL = 0
        internal const val MODE_IMPORT_BOOKSHELF = 1
        internal const val MODE_EXPORT_SUCCESS = 2

        fun addUrl() = BookshelfActionDialog().apply {
            arguments = Bundle().apply { putInt(ARG_MODE, MODE_ADD_URL) }
        }

        fun importBookshelf() = BookshelfActionDialog().apply {
            arguments = Bundle().apply { putInt(ARG_MODE, MODE_IMPORT_BOOKSHELF) }
        }

        fun exportSuccess(path: String) = BookshelfActionDialog().apply {
            arguments = Bundle().apply {
                putInt(ARG_MODE, MODE_EXPORT_SUCCESS)
                putString(ARG_VALUE, path)
            }
        }
    }
}

@Composable
private fun BookshelfActionDialogContent(
    mode: Int,
    initialValue: String,
    onCancel: () -> Unit,
    onSelectFile: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by rememberSaveable { mutableStateOf(initialValue) }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val inputMode = mode != BookshelfActionDialog.MODE_EXPORT_SUCCESS

    LaunchedEffect(inputMode) {
        if (inputMode) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    val title = when (mode) {
        BookshelfActionDialog.MODE_ADD_URL -> stringResource(R.string.add_book_url)
        BookshelfActionDialog.MODE_IMPORT_BOOKSHELF -> {
            stringResource(R.string.import_bookshelf)
        }
        else -> stringResource(R.string.export_success)
    }

    NgDialog(
        title = title,
        variant = NgDialogVariant.STANDARD,
        actions = {
            if (mode == BookshelfActionDialog.MODE_IMPORT_BOOKSHELF) {
                NgFormActionButton(
                    text = stringResource(R.string.select_file),
                    onClick = onSelectFile,
                    appearance = NgFormActionButtonAppearance.DIALOG,
                )
            }
            if (inputMode) {
                NgFormActionButton(
                    text = stringResource(R.string.cancel),
                    onClick = onCancel,
                    appearance = NgFormActionButtonAppearance.DIALOG,
                )
            }
            NgFormActionButton(
                text = stringResource(R.string.ok),
                onClick = { onConfirm(value) },
                variant = NgButtonVariant.PRIMARY,
                appearance = NgFormActionButtonAppearance.DIALOG,
            )
        },
    ) {
        if (inputMode) {
            NgFormField(
                label = if (mode == BookshelfActionDialog.MODE_ADD_URL) {
                    "URL"
                } else {
                    "URL / JSON"
                },
                value = value,
                onValueChange = { value = it },
                modifier = Modifier.focusRequester(focusRequester),
                placeholder = if (mode == BookshelfActionDialog.MODE_ADD_URL) {
                    "url"
                } else {
                    "url/json"
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                variant = NgFormFieldVariant.PLAIN_UNDERLINE,
            )
        } else {
            Text(
                text = stringResource(R.string.path),
                color = Color(NgTheme.colors.onSurfaceVariant),
                fontSize = 13.sp,
            )
            SelectionContainer {
                Text(
                    text = value,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    color = Color(NgTheme.colors.onSurface),
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                )
            }
        }
    }
}

class BookshelfAddProgressDialog : BaseComposeDialogFragment() {

    private var progressCount by mutableStateOf<Int?>(null)
    private val callback get() = parentFragment as? Callback

    override fun onStart() {
        super.onStart()
        applyNgDialogWindow(marginDp = 56)
        dialog?.setCanceledOnTouchOutside(false)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        (view as ComposeView).apply {
            setBackgroundColor(AndroidColor.TRANSPARENT)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                NgAppTheme(updateSystemBars = false) {
                    BookshelfAddProgressContent(progressCount)
                }
            }
        }
    }

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)
        callback?.onBookshelfAddProgressCancelled()
    }

    fun updateProgress(count: Int?) {
        progressCount = count
    }

    interface Callback {
        fun onBookshelfAddProgressCancelled()
    }

    companion object {
        const val TAG = "BookshelfAddProgressDialog"
    }
}

@Composable
private fun BookshelfAddProgressContent(progressCount: Int?) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = colorResource(R.color.ng_surface_card),
        shape = RoundedCornerShape(NgTheme.shapes.largeDp.dp),
        shadowElevation = NgTheme.effects.overlayElevationDp.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(30.dp),
                color = Color(NgTheme.colors.primary),
                strokeWidth = 3.dp,
            )
            Text(
                text = if (progressCount == null) {
                    stringResource(R.string.bookshelf_adding)
                } else {
                    stringResource(R.string.bookshelf_adding_progress, progressCount)
                },
                modifier = Modifier.padding(start = 12.dp),
                color = Color(NgTheme.colors.onSurface),
                fontSize = 15.sp,
            )
        }
    }
}
