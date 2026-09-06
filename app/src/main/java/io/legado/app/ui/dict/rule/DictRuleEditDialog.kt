package io.legado.app.ui.dict.rule

import android.app.Activity.RESULT_OK
import android.app.Application
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.viewModels
import io.legado.app.R
import io.legado.app.base.BaseComposeDialogFragment
import io.legado.app.base.BaseViewModel
import io.legado.app.data.appDb
import io.legado.app.data.entities.DictRule
import io.legado.app.lib.theme.view.ThemeEditText
import io.legado.app.ui.code.CodeEditActivity
import io.legado.app.ui.design.components.NgButtonVariant
import io.legado.app.ui.design.components.NgDialogVariant
import io.legado.app.ui.design.components.compose.NgCompactEditorDialog
import io.legado.app.ui.design.components.compose.NgDialog
import io.legado.app.ui.design.components.compose.NgDialogTextActionButton
import io.legado.app.ui.design.components.compose.NgFormActionButton
import io.legado.app.ui.design.components.compose.NgFormActionButtonAppearance
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.design.theme.NgTheme
import io.legado.app.ui.widget.code.CodeView
import io.legado.app.ui.widget.code.addJsPattern
import io.legado.app.ui.widget.code.addJsonPattern
import io.legado.app.ui.widget.code.addLegadoPattern
import io.legado.app.ui.widget.dialog.applyNgDialogWindow
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.getClipText
import io.legado.app.utils.sendToClip
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers
import kotlin.math.min
import kotlin.math.roundToInt

class DictRuleEditDialog() : BaseComposeDialogFragment() {

    constructor(name: String) : this() {
        arguments = Bundle().apply { putString(ARG_NAME, name) }
    }

    private val viewModel by viewModels<DictRuleEditViewModel>()
    private var rule by mutableStateOf(DictRule(), referentialEqualityPolicy())
    private var originalFields = DictRuleFields()
    private var loaded by mutableStateOf(false)
    private var showDiscardConfirmation by mutableStateOf(false)
    private var focusedEditText: EditText? = null

    private val textEditLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != RESULT_OK) return@registerForActivityResult
        val view = focusedEditText
        if (view == null) {
            toastOnUi(R.string.focus_lost_on_textbox)
            return@registerForActivityResult
        }
        view.requestFocus()
        result.data?.getStringExtra("text")?.let(view::setText)
        result.data?.getIntExtra("cursorPosition", -1)
            ?.takeIf { it in 0..view.text.length }
            ?.let(view::setSelection)
    }

    override fun onStart() {
        super.onStart()
        applyNgDialogWindow()
        dialog?.setCanceledOnTouchOutside(false)
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        isCancelable = false
        val isEditing = arguments?.containsKey(ARG_NAME) == true
        (view as ComposeView).apply {
            setBackgroundColor(AndroidColor.TRANSPARENT)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                NgAppTheme(updateSystemBars = false) {
                    BackHandler { requestDismiss() }
                    DictRuleEditorDialogContent(
                        rule = rule,
                        loading = !loaded,
                        isEditing = isEditing,
                        onRuleChange = { rule = it },
                        onFocused = { focusedEditText = it },
                        onFullEdit = ::openFullEdit,
                        onCopy = { requireContext().sendToClip(GSON.toJson(rule)) },
                        onPaste = {
                            viewModel.pasteRule { pasted ->
                                rule = rule.copy(
                                    name = pasted.name,
                                    urlRule = pasted.urlRule,
                                    showRule = pasted.showRule,
                                )
                            }
                        },
                        onCancel = ::requestDismiss,
                        onSave = ::save,
                    )
                    if (showDiscardConfirmation) {
                        DictRuleDiscardDialog(
                            onContinueEditing = { showDiscardConfirmation = false },
                            onDiscard = ::dismissDirectly,
                        )
                    }
                }
            }
        }
        viewModel.initData(arguments?.getString(ARG_NAME)) {
            val initialRule = it?.copy() ?: DictRule()
            originalFields = initialRule.fields()
            rule = initialRule
            loaded = true
        }
    }

    private fun openFullEdit() {
        val view = focusedEditText
        if (view == null) {
            toastOnUi(R.string.please_focus_cursor_on_textbox)
            return
        }
        textEditLauncher.launch(
            Intent(requireActivity(), CodeEditActivity::class.java).apply {
                putExtra("text", view.text.toString())
                putExtra("title", view.tag?.toString().orEmpty())
                putExtra("cursorPosition", view.selectionStart)
            }
        )
    }

    private fun requestDismiss() {
        if (!loaded || rule.fields() == originalFields) {
            dismissDirectly()
        } else {
            showDiscardConfirmation = true
        }
    }

    private fun dismissDirectly() {
        super.dismissAllowingStateLoss()
    }

    private fun save() {
        viewModel.save(rule) { dismissDirectly() }
    }

    class DictRuleEditViewModel(application: Application) : BaseViewModel(application) {

        private var dictRule: DictRule? = null

        fun initData(name: String?, onFinally: (DictRule?) -> Unit) {
            dictRule?.let {
                onFinally(it)
                return
            }
            execute {
                if (name != null) dictRule = appDb.dictRuleDao.getByName(name)
            }.onFinally { onFinally(dictRule) }
        }

        fun save(newDictRule: DictRule, onFinally: () -> Unit) {
            execute {
                dictRule?.let { appDb.dictRuleDao.delete(it) }
                appDb.dictRuleDao.insert(newDictRule)
                dictRule = newDictRule
            }.onFinally { onFinally() }
        }

        fun pasteRule(success: (DictRule) -> Unit) {
            val text = context.getClipText()
            if (text.isNullOrBlank()) {
                context.toastOnUi("剪贴板没有内容")
                return
            }
            execute(context = Dispatchers.Main) {
                GSON.fromJsonObject<DictRule>(text).getOrThrow()
            }.onSuccess { success(it) }.onError {
                context.toastOnUi("格式不对")
            }
        }
    }

    companion object {
        private const val ARG_NAME = "name"
    }
}

private data class DictRuleFields(
    val name: String = "",
    val urlRule: String = "",
    val showRule: String = "",
)

private fun DictRule.fields() = DictRuleFields(name, urlRule, showRule)

@Composable
private fun DictRuleEditorDialogContent(
    rule: DictRule,
    loading: Boolean,
    isEditing: Boolean,
    onRuleChange: (DictRule) -> Unit,
    onFocused: (EditText) -> Unit,
    onFullEdit: () -> Unit,
    onCopy: () -> Unit,
    onPaste: () -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    NgCompactEditorDialog(
        title = stringResource(if (isEditing) R.string.edit_rule else R.string.new_rule),
        titleFontSize = 20.sp,
        titleLineHeight = 24.sp,
        titleFontWeight = FontWeight.Medium,
        titleAction = {
            DictRuleHeaderAction(
                iconRes = R.drawable.ic_code,
                iconSize = 20.dp,
                description = stringResource(R.string.edit_content),
                enabled = !loading,
                onClick = onFullEdit,
            )
            DictRuleHeaderAction(
                iconRes = R.drawable.ic_copy,
                iconSize = 20.dp,
                description = stringResource(R.string.copy_rule),
                enabled = !loading,
                onClick = onCopy,
            )
            DictRuleHeaderAction(
                iconRes = R.drawable.ic_paste,
                iconSize = 16.dp,
                description = stringResource(R.string.paste_rule),
                enabled = !loading,
                onClick = onPaste,
            )
        },
    ) {
        if (loading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(Modifier.size(28.dp))
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 350.dp)
                    .padding(start = 4.dp, top = 12.dp, end = 4.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DictRuleUnderlineAndroidField(
                    label = stringResource(R.string.name),
                    value = rule.name,
                    onValueChange = { onRuleChange(rule.copy(name = it)) },
                    onFocused = onFocused,
                    fieldHeight = 38.dp,
                    singleLine = true,
                    maxLines = 1,
                )
                DictRuleUnderlineAndroidField(
                    label = stringResource(R.string.url_rule),
                    value = rule.urlRule,
                    onValueChange = { onRuleChange(rule.copy(urlRule = it)) },
                    onFocused = onFocused,
                    fieldHeight = 90.dp,
                    singleLine = false,
                    maxLines = 4,
                )
                DictRuleUnderlineAndroidField(
                    label = stringResource(R.string.show_rule),
                    value = rule.showRule,
                    onValueChange = { onRuleChange(rule.copy(showRule = it)) },
                    onFocused = onFocused,
                    fieldHeight = 126.dp,
                    singleLine = false,
                    maxLines = 6,
                    codeField = true,
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, end = 4.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NgFormActionButton(
                text = stringResource(R.string.cancel),
                onClick = onCancel,
                appearance = NgFormActionButtonAppearance.DIALOG,
            )
            Spacer(Modifier.size(10.dp))
            NgFormActionButton(
                text = stringResource(R.string.save),
                onClick = onSave,
                enabled = !loading,
                variant = NgButtonVariant.PRIMARY,
                appearance = NgFormActionButtonAppearance.DIALOG,
            )
        }
    }
}

@Composable
private fun DictRuleHeaderAction(
    iconRes: Int,
    iconSize: Dp,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = description,
            tint = Color(NgTheme.colors.onSurface).copy(
                alpha = if (enabled) 1f else 0.38f,
            ),
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
private fun DictRuleUnderlineAndroidField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    onFocused: (EditText) -> Unit,
    fieldHeight: Dp,
    singleLine: Boolean,
    maxLines: Int,
    codeField: Boolean = false,
) {
    val colors = NgTheme.colors
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    var focused by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            color = Color(colors.primary),
            fontSize = 13.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Normal,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(fieldHeight)
                .drawBehind {
                    val strokeWidth = (if (focused) 1.5.dp else 1.dp).toPx()
                    val y = size.height - strokeWidth / 2f
                    drawLine(
                        color = Color(if (focused) colors.primary else colors.outline),
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = strokeWidth,
                    )
                },
        ) {
            AndroidView(
                factory = { context ->
                    val horizontal = (2 * context.resources.displayMetrics.density).roundToInt()
                    val vertical = (5 * context.resources.displayMetrics.density).roundToInt()
                    val editText: EditText = if (codeField) {
                        CodeView(context).apply {
                            addLegadoPattern()
                            addJsonPattern()
                            addJsPattern()
                        }
                    } else {
                        ThemeEditText(context)
                    }
                    editText.apply {
                        tag = label
                        background = null
                        gravity = Gravity.TOP or Gravity.START
                        includeFontPadding = false
                        setPadding(horizontal, vertical, horizontal, vertical)
                        setTextColor(Color(colors.onSurface).toArgb())
                        setHintTextColor(Color(colors.onSurfaceVariant).toArgb())
                        textSize = 15f
                        isSingleLine = singleLine
                        this.maxLines = maxLines
                        setHorizontallyScrolling(false)
                        onFocusChangeListener = View.OnFocusChangeListener { view, hasFocus ->
                            focused = hasFocus
                            if (hasFocus) onFocused(view as EditText)
                        }
                        doAfterTextChanged {
                            currentOnValueChange(it?.toString().orEmpty())
                        }
                    }
                },
                update = { editText ->
                    editText.tag = label
                    if (editText.text?.toString() != value) {
                        val oldSelection = editText.selectionStart.coerceAtLeast(0)
                        if (editText is CodeView && value.isNotEmpty()) {
                            editText.setTextHighlighted(value)
                        } else {
                            editText.setText(value)
                        }
                        editText.setSelection(min(oldSelection, value.length))
                    }
                },
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

@Composable
private fun DictRuleDiscardDialog(
    onContinueEditing: () -> Unit,
    onDiscard: () -> Unit,
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onContinueEditing) {
        NgDialog(
            title = stringResource(R.string.exit),
            variant = NgDialogVariant.CLASSIC_CONFIRMATION,
            titleFontWeight = FontWeight.Normal,
            actions = {
                NgDialogTextActionButton(
                    text = stringResource(R.string.continue_editing),
                    onClick = onContinueEditing,
                )
                NgDialogTextActionButton(
                    text = stringResource(R.string.discard_changes),
                    onClick = onDiscard,
                    danger = true,
                )
            },
        ) {
            Text(
                text = stringResource(R.string.exit_no_save),
                color = Color(NgTheme.colors.onSurface),
            )
        }
    }
}
