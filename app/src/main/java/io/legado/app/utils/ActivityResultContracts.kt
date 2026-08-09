package io.legado.app.utils

import android.app.Activity.RESULT_OK
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import io.legado.app.constant.AppLog
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

    override fun createIntent(context: Context, input: Int?): Intent {
        requestCode = input
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
        val uri = if (useFallback) {
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

}

class SelectDirectoryContract :
    ActivityResultContract<SelectDirectoryContract.Request?, SelectDirectoryContract.Result>() {

    private val delegate = ActivityResultContracts.OpenDocumentTree()
    private var request = Request()

    override fun createIntent(context: Context, input: Request?): Intent {
        request = input ?: Request()
        return delegate.createIntent(context, request.initialUri)
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Result {
        val uri = delegate.parseResult(resultCode, intent)?.let { selectedUri ->
            if (RealPathUtil.getTreePath(selectedUri)
                    ?.startsWith(appCtx.externalFiles.parent!!) == true
            ) {
                null
            } else {
                selectedUri.takePersistableReadWritePermission()
                selectedUri
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

class CreateFileContract :
    ActivityResultContract<CreateFileContract.FileData, CreateFileContract.Result>() {

    private var fileData: FileData? = null

    override fun createIntent(context: Context, input: FileData): Intent {
        fileData = input
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
