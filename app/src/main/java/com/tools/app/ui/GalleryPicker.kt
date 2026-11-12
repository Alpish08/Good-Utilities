package com.tools.app.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia

/**
 * Photo and video picking through Samsung Gallery (albums, Samsung's grid, search) instead of Google's photo picker.
 * Gallery hands back MediaStore links; with All files access they stay readable later, so nothing has to be copied
 * just to keep access. Phones without Samsung Gallery fall back to the system photo picker.
 */
object SamsungGallery {
    const val PACKAGE = "com.sec.android.gallery3d"

    fun mime(request: PickVisualMediaRequest): String = when (val t = request.mediaType) {
        PickVisualMedia.ImageOnly -> "image/*"
        PickVisualMedia.VideoOnly -> "video/*"
        is PickVisualMedia.SingleMimeType -> t.mimeType
        else -> "*/*"
    }

    fun intent(context: Context, request: PickVisualMediaRequest, multiple: Boolean): Intent? {
        val mime = mime(request)
        val intent = Intent(Intent.ACTION_GET_CONTENT)
            .setPackage(PACKAGE)
            .setType(mime)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, multiple)
        if (mime == "*/*") intent.putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))
        return intent.takeIf { it.resolveActivity(context.packageManager) != null }
    }

    fun uris(intent: Intent?): List<Uri> {
        if (intent == null) return emptyList()
        val out = LinkedHashSet<Uri>()
        intent.data?.let(out::add)
        intent.clipData?.let { c -> for (i in 0 until c.itemCount) c.getItemAt(i).uri?.let(out::add) }
        return out.toList()
    }
}

/** Drop-in for [PickVisualMedia]: one photo or video from Samsung Gallery. */
class GalleryPick : ActivityResultContract<PickVisualMediaRequest, Uri?>() {
    override fun createIntent(context: Context, input: PickVisualMediaRequest): Intent =
        SamsungGallery.intent(context, input, multiple = false) ?: PickVisualMedia().createIntent(context, input)

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        if (resultCode == Activity.RESULT_OK) SamsungGallery.uris(intent).firstOrNull() else null
}

/** Drop-in for [PickMultipleVisualMedia]: several photos or videos from Samsung Gallery. */
class GalleryPickMultiple : ActivityResultContract<PickVisualMediaRequest, List<Uri>>() {
    override fun createIntent(context: Context, input: PickVisualMediaRequest): Intent =
        SamsungGallery.intent(context, input, multiple = true) ?: PickMultipleVisualMedia().createIntent(context, input)

    override fun parseResult(resultCode: Int, intent: Intent?): List<Uri> =
        if (resultCode == Activity.RESULT_OK) SamsungGallery.uris(intent) else emptyList()
}

/**
 * Document picking through Samsung My Files (Recent, Internal storage, OneDrive, Google Drive tabs) instead of
 * Google's Files picker. Falls back to the system picker when My Files isn't installed.
 */
object SamsungMyFiles {
    const val PACKAGE = "com.sec.android.app.myfiles"

    fun intent(context: Context, mimes: Array<String>, multiple: Boolean): Intent? {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
            .setPackage(PACKAGE)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, multiple)
        if (mimes.size == 1) intent.type = mimes[0]
        else intent.setType("*/*").putExtra(Intent.EXTRA_MIME_TYPES, mimes)
        return intent.takeIf { it.resolveActivity(context.packageManager) != null }
    }
}

/** Drop-in for OpenDocument: one file from My Files. */
class FilesPick : ActivityResultContract<Array<String>, Uri?>() {
    override fun createIntent(context: Context, input: Array<String>): Intent =
        SamsungMyFiles.intent(context, input, multiple = false)
            ?: androidx.activity.result.contract.ActivityResultContracts.OpenDocument().createIntent(context, input)

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        if (resultCode == Activity.RESULT_OK) SamsungGallery.uris(intent).firstOrNull() else null
}

/** Drop-in for OpenMultipleDocuments: several files from My Files. */
class FilesPickMultiple : ActivityResultContract<Array<String>, List<Uri>>() {
    override fun createIntent(context: Context, input: Array<String>): Intent =
        SamsungMyFiles.intent(context, input, multiple = true)
            ?: androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments().createIntent(context, input)

    override fun parseResult(resultCode: Int, intent: Intent?): List<Uri> =
        if (resultCode == Activity.RESULT_OK) SamsungGallery.uris(intent) else emptyList()
}
