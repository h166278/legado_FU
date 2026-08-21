package io.legado.app.ui.main.bookshelf

import android.app.DatePickerDialog
import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.base.BaseComposeDialogFragment
import io.legado.app.data.entities.Book
import io.legado.app.ui.design.components.NgButtonVariant
import io.legado.app.ui.design.components.NgDialogVariant
import io.legado.app.ui.design.components.compose.NgDialog
import io.legado.app.ui.design.components.compose.NgDialogDivider
import io.legado.app.ui.design.components.compose.NgDialogValueRow
import io.legado.app.ui.design.components.compose.NgFormActionButton
import io.legado.app.ui.design.components.compose.NgFormActionButtonAppearance
import io.legado.app.ui.design.components.compose.NgFormField
import io.legado.app.ui.design.components.compose.NgFormFieldVariant
import io.legado.app.ui.design.components.compose.NgFormSwitchRow
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.widget.dialog.applyNgDialogWindow
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class SimulatedReadingDialog() : BaseComposeDialogFragment() {

    constructor(book: Book) : this() {
        arguments = Bundle().apply {
            putString(ARG_BOOK_URL, book.bookUrl)
            putInt(ARG_TOTAL_CHAPTERS, book.totalChapterNum)
            putBoolean(ARG_ENABLED, book.getReadSimulating())
            putInt(ARG_START_CHAPTER, book.getStartChapter())
            putInt(ARG_DAILY_CHAPTERS, book.getDailyChapters())
            putString(
                ARG_START_DATE,
                (book.getStartDate() ?: LocalDate.now()).format(DATE_FORMATTER),
            )
        }
    }

    private val callback get() = parentFragment as? Callback

    override fun onStart() {
        super.onStart()
        applyNgDialogWindow()
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        val arguments = arguments
        val bookUrl = arguments?.getString(ARG_BOOK_URL).orEmpty()
        if (bookUrl.isBlank()) {
            dismissAllowingStateLoss()
            return
        }

        (view as ComposeView).apply {
            setBackgroundColor(AndroidColor.TRANSPARENT)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                NgAppTheme(updateSystemBars = false) {
                    SimulatedReadingDialogContent(
                        initialEnabled = arguments?.getBoolean(ARG_ENABLED) == true,
                        initialStartDate = arguments?.getString(ARG_START_DATE)
                            ?: LocalDate.now().format(DATE_FORMATTER),
                        initialStartChapter = arguments?.getInt(ARG_START_CHAPTER, 0)
                            ?.toString() ?: "0",
                        initialDailyChapters = arguments?.getInt(ARG_DAILY_CHAPTERS, 0)
                            ?.toString() ?: "0",
                        totalChapters = arguments?.getInt(ARG_TOTAL_CHAPTERS, 0) ?: 0,
                        onCancel = ::dismissAllowingStateLoss,
                        onConfirm = { enabled, startDate, startChapter, dailyChapters ->
                            callback?.onSimulatedReadingConfirmed(
                                bookUrl = bookUrl,
                                enabled = enabled,
                                startDate = startDate,
                                startChapter = startChapter,
                                dailyChapters = dailyChapters,
                            )
                            dismissAllowingStateLoss()
                        },
                    )
                }
            }
        }
    }

    interface Callback {
        fun onSimulatedReadingConfirmed(
            bookUrl: String,
            enabled: Boolean,
            startDate: LocalDate,
            startChapter: Int,
            dailyChapters: Int,
        )
    }

    private companion object {
        const val ARG_BOOK_URL = "bookUrl"
        const val ARG_TOTAL_CHAPTERS = "totalChapters"
        const val ARG_ENABLED = "enabled"
        const val ARG_START_DATE = "startDate"
        const val ARG_START_CHAPTER = "startChapter"
        const val ARG_DAILY_CHAPTERS = "dailyChapters"
    }
}

@Composable
private fun SimulatedReadingDialogContent(
    initialEnabled: Boolean,
    initialStartDate: String,
    initialStartChapter: String,
    initialDailyChapters: String,
    totalChapters: Int,
    onCancel: () -> Unit,
    onConfirm: (Boolean, LocalDate, Int, Int) -> Unit,
) {
    var enabled by rememberSaveable { mutableStateOf(initialEnabled) }
    var startDate by rememberSaveable { mutableStateOf(initialStartDate) }
    var startChapter by rememberSaveable { mutableStateOf(initialStartChapter) }
    var dailyChapters by rememberSaveable { mutableStateOf(initialDailyChapters) }
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    NgDialog(
        title = stringResource(R.string.simulated_reading),
        variant = NgDialogVariant.EDITOR,
        actions = {
            NgFormActionButton(
                text = stringResource(R.string.cancel),
                onClick = onCancel,
                appearance = NgFormActionButtonAppearance.DIALOG,
            )
            NgFormActionButton(
                text = stringResource(R.string.ok),
                onClick = {
                    onConfirm(
                        enabled,
                        parseDate(startDate),
                        startChapter.toIntOrNull() ?: 0,
                        dailyChapters.toIntOrNull() ?: totalChapters,
                    )
                },
                variant = NgButtonVariant.PRIMARY,
                appearance = NgFormActionButtonAppearance.DIALOG,
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp),
        ) {
            NgFormSwitchRow(
                title = stringResource(R.string.enable),
                checked = enabled,
                onCheckedChange = { enabled = it },
                modifier = Modifier.height(56.dp),
            )
            NgDialogDivider()
            NgDialogValueRow(
                title = stringResource(R.string.start_from),
                value = startDate,
                onClick = {
                    val currentDate = parseDate(startDate)
                    DatePickerDialog(
                        context,
                        { _, year, month, dayOfMonth ->
                            startDate = LocalDate.of(year, month + 1, dayOfMonth)
                                .format(DATE_FORMATTER)
                        },
                        currentDate.year,
                        currentDate.monthValue - 1,
                        currentDate.dayOfMonth,
                    ).show()
                },
            )
        }
        Spacer(Modifier.height(20.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            NgFormField(
                label = stringResource(R.string.start_chapter),
                value = startChapter,
                onValueChange = { startChapter = it.filter(Char::isDigit).take(5) },
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next,
                ),
                variant = NgFormFieldVariant.INLINE_UNDERLINE,
            )
            NgFormField(
                label = stringResource(R.string.daily_chapters),
                value = dailyChapters,
                onValueChange = { dailyChapters = it.filter(Char::isDigit).take(5) },
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = { focusManager.clearFocus() }
                ),
                variant = NgFormFieldVariant.INLINE_UNDERLINE,
            )
        }
    }
}

private fun parseDate(value: String?): LocalDate {
    return runCatching {
        LocalDate.parse(value, DATE_FORMATTER)
    }.getOrDefault(LocalDate.now())
}

private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
