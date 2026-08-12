package io.legado.app.utils

import android.app.Activity.RESULT_OK
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.help.config.AppConfig
import io.legado.app.ui.file.BuiltInFilePickerActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import splitties.init.appCtx
import java.io.File

fun <T> ActivityResultLauncher<T?>.launch() {
    launch(null)
}

class SelectImageContract : ActivityResultContract<Int?, SelectImageContract.Result>() {

    private val delegate = ActivityResultContracts.PickVisualMedia()
    private var requestCode: Int? = null
    private var useFallback = false
    private var useBuiltIn = false

    override fun createIntent(context: Context, input: Int?): Intent {
        requestCode = input
        useBuiltIn = AppConfig.defaultFilePicker == AppConfig.DEFAULT_FILE_PICKER_BUILT_IN
        useFallback = false
        if (useBuiltIn) {
            return BuiltInFilePickerActivity.createIntent(
                context = context,
                mode = BuiltInFilePickerActivity.FILE,
                title = context.getString(R.string.select_image),
                allowExtensions = IMAGE_EXTENSIONS,
            )
        }
        val intent = Intent(Intent.ACTION_GET_CONTENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("image/*")
        if (intent.resolveActivity(appCtx.packageManager) == null) {
            useFallback = true
            val request = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            return delegate.createIntent(context, request)
        }
        return intent
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Result {
        val uri = if (useBuiltIn) {
            if (resultCode == RESULT_OK) intent?.data else null
        } else if (useFallback) {
            delegate.parseResult(resultCode, intent)
        } else if (resultCode == RESULT_OK) {
            intent?.data
        } else {
            null
        }
        return Result(requestCode, uri)
    }

    data class Result(
        val requestCode: Int?,
        val uri: Uri? = null
    )

    private companion object {
        val IMAGE_EXTENSIONS = arrayOf("jpg", "jpeg", "png", "gif", "bmp", "webp")
    }

}

class SelectDirectoryContract :
    ActivityResultContract<SelectDirectoryContract.Request?, SelectDirectoryContract.Result>() {

    private val delegate = ActivityResultContracts.OpenDocumentTree()
    private var request = Request()
    private var useBuiltIn = false

    override fun createIntent(context: Context, input: Request?): Intent {
        request = input ?: Request()
        useBuiltIn = AppConfig.defaultFilePicker == AppConfig.DEFAULT_FILE_PICKER_BUILT_IN
        if (useBuiltIn) {
            return BuiltInFilePickerActivity.createIntent(
                context = context,
                mode = BuiltInFilePickerActivity.DIRECTORY,
                title = context.getString(R.string.select_folder),
                initialPath = request.initialUri.toInitialDirectoryPath(context),
            )
        }
        return delegate.createIntent(context, request.initialUri)
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Result {
        val selectedUri = if (useBuiltIn) {
            if (resultCode == RESULT_OK) intent?.data else null
        } else {
            delegate.parseResult(resultCode, intent)
        }
        val uri = selectedUri?.let {
            if (it.isInsideAppExternalDirectory()) {
                null
            } else {
                it.takePersistableReadWritePermission()
                it
            }
        }
        return Result(uri, request.requestCode, request.value)
    }

    data class Request(
        val requestCode: Int = 0,
        val value: String? = null,
        val initialUri: Uri? = null
    )

    data class Result(
        val uri: Uri?,
        val requestCode: Int,
        val value: String?
    )
}

class SelectFileContract : ActivityResultContract<Array<String>, Uri?>() {

    private val delegate = ActivityResultContracts.OpenDocument()
    private var useBuiltIn = false

    override fun createIntent(context: Context, input: Array<String>): Intent {
        useBuiltIn = AppConfig.defaultFilePicker == AppConfig.DEFAULT_FILE_PICKER_BUILT_IN
        return if (useBuiltIn) {
            BuiltInFilePickerActivity.createIntent(
                context = context,
                mode = BuiltInFilePickerActivity.FILE,
                title = context.getString(R.string.select_file),
                allowExtensions = extensionsOfMimeTypes(input),
            )
        } else {
            delegate.createIntent(context, input)
        }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
        return if (useBuiltIn) {
            if (resultCode == RESULT_OK) intent?.data else null
        } else {
            delegate.parseResult(resultCode, intent)
        }
    }
}

class CreateDocumentContract(private val mimeType: String) :
    ActivityResultContract<String, Uri?>() {

    private val delegate = ActivityResultContracts.CreateDocument(mimeType)
    private var useBuiltIn = false

    override fun createIntent(context: Context, input: String): Intent {
        useBuiltIn = AppConfig.defaultFilePicker == AppConfig.DEFAULT_FILE_PICKER_BUILT_IN
        return if (useBuiltIn) {
            BuiltInFilePickerActivity.createIntent(
                context = context,
                mode = BuiltInFilePickerActivity.DIRECTORY,
                title = context.getString(R.string.select_folder),
                outputFileName = input,
            )
        } else {
            delegate.createIntent(context, input)
        }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
        return if (useBuiltIn) {
            if (resultCode == RESULT_OK) intent?.data else null
        } else {
            delegate.parseResult(resultCode, intent)
        }
    }
}

class CreateFileContract :
    ActivityResultContract<CreateFileContract.FileData, CreateFileContract.Result>() {

    private var fileData: FileData? = null
    private var useBuiltIn = false

    override fun createIntent(context: Context, input: FileData): Intent {
        fileData = input
        useBuiltIn = AppConfig.defaultFilePicker == AppConfig.DEFAULT_FILE_PICKER_BUILT_IN
        if (useBuiltIn) {
            return BuiltInFilePickerActivity.createIntent(
                context = context,
                mode = BuiltInFilePickerActivity.DIRECTORY,
                title = context.getString(R.string.select_folder),
                outputFileName = input.name,
            )
        }
        return Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType(input.type)
            .putExtra(Intent.EXTRA_TITLE, input.name)
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Result {
        val uri = if (resultCode == RESULT_OK) intent?.data else null
        return Result(uri, fileData)
    }

    data class FileData(
        val name: String,
        val data: Any,
        val type: String
    )

    data class Result(
        val uri: Uri?,
        val fileData: FileData?
    ) {
        fun save(
            owner: LifecycleOwner,
            context: Context,
            onSuccess: (Uri) -> Unit
        ) {
            val target = uri ?: return
            val source = fileData ?: return
            owner.lifecycleScope.launch {
                kotlin.runCatching {
                    withContext(Dispatchers.IO) {
                        val bytes = when (val data = source.data) {
                            is File -> data.readBytes()
                            is ByteArray -> data
                            is String -> data.toByteArray()
                            else -> GSON.toJson(data).toByteArray()
                        }
                        check(target.writeBytes(context, bytes)) { "写入文件失败" }
                    }
                }.onSuccess {
                    onSuccess(target)
                }.onFailure {
                    it.printOnDebug()
                    AppLog.put("导出文件失败\n${it.localizedMessage}", it)
                    context.toastOnUi(it.localizedMessage ?: "导出文件失败")
                }
            }
        }
    }
}

private fun Uri?.toInitialDirectoryPath(context: Context): String? {
    val uri = this ?: return null
    val path = RealPathUtil.getTreePath(uri) ?: RealPathUtil.getPath(context, uri) ?: return null
    return File(path).let { if (it.isDirectory) it.path else it.parent }
}

private fun Uri.isInsideAppExternalDirectory(): Boolean {
    val path = RealPathUtil.getTreePath(this) ?: if (scheme == "file") this.path else null
    return path?.startsWith(appCtx.externalFiles.parent!!) == true
}

internal fun extensionsOfMimeTypes(mimeTypes: Array<String>): Array<String>? {
    val extensions = linkedSetOf<String>()
    mimeTypes.forEach { mimeType ->
        when (mimeType.lowercase()) {
            "*/*" -> return null
            "image/*" -> extensions.addAll(
                arrayOf("jpg", "jpeg", "png", "gif", "bmp", "webp")
            )
            "text/*" -> extensions.addAll(arrayOf("txt", "xml", "json", "md", "csv"))
            "font/*" -> extensions.addAll(arrayOf("ttf", "otf", "woff", "woff2"))
            "application/json" -> extensions.add("json")
            "application/xml", "text/xml" -> extensions.add("xml")
            "application/zip", "application/x-zip-compressed" -> extensions.add("zip")
            else -> MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)?.let {
                extensions.add(it.lowercase())
            }
        }
    }
    return extensions.takeIf { it.isNotEmpty() }?.toTypedArray()
}

class StartActivityContract(private val cls: Class<*>) :
    ActivityResultContract<(Intent.() -> Unit)?, ActivityResult>() {

    override fun createIntent(context: Context, input: (Intent.() -> Unit)?): Intent {
        val intent = Intent(context, cls)
        input?.let {
            intent.apply(input)
        }
        return intent
    }

    override fun parseResult(
        resultCode: Int, intent: Intent?
    ): ActivityResult {
        return ActivityResult(resultCode, intent)
    }

}
