package com.example.features.printing

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ParchiImageSharer {

  private const val SHARED_SLIPS_DIR = "shared_slips"
  private const val STALE_CACHE_MS = 24 * 60 * 60 * 1000L

  suspend fun saveParchiPngFile(
    context: Context,
    parchiData: ParchiSlipData,
    nowMillis: Long = System.currentTimeMillis(),
  ): Result<File> = withContext(Dispatchers.IO) {
    try {
      val dir = File(context.cacheDir, SHARED_SLIPS_DIR).apply { mkdirs() }
      cleanupStaleFiles(dir, nowMillis)

      val safeSlug = parchiData.nameValue
        .trim()
        .replace(Regex("[^A-Za-z0-9_-]+"), "_")
        .trim('_')
        .take(24)
        .ifEmpty { "customer" }
      val targetFile = File(dir, "parchi_${safeSlug}_$nowMillis.png")

      val bitmap = ThermalReceiptFormatter.renderParchiShareBitmap(parchiData)
      try {
        FileOutputStream(targetFile).use { out ->
          val compressed = bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
          if (!compressed) {
            return@withContext Result.failure(IllegalStateException("Failed to encode parchi PNG."))
          }
          out.flush()
        }
      } finally {
        bitmap.recycle()
      }
      Result.success(targetFile)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  fun contentUriForFile(context: Context, file: File): Uri =
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

  fun buildShareImageIntent(
    imageUri: Uri,
    chooserTitle: String,
    caption: String? = null,
    fromActivityContext: Boolean = true,
  ): Intent {
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
      type = "image/png"
      putExtra(Intent.EXTRA_STREAM, imageUri)
      if (!caption.isNullOrBlank()) {
        putExtra(Intent.EXTRA_TEXT, caption)
      }
      clipData = ClipData.newRawUri("", imageUri)
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    return Intent.createChooser(sendIntent, chooserTitle).apply {
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      if (!fromActivityContext) {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
    }
  }

  suspend fun shareParchiImage(
    context: Context,
    parchiData: ParchiSlipData,
    chooserTitle: String,
  ): Result<Uri> {
    val fileResult = saveParchiPngFile(context, parchiData)
    val file = fileResult.getOrElse { return Result.failure(it) }
    return try {
      val uri = contentUriForFile(context, file)
      val caption = buildString {
        append(parchiData.nameValue)
        if (parchiData.headerBannerText.isNotBlank()) {
          append(" — ")
          append(parchiData.headerBannerText)
        }
      }
      val chooser = buildShareImageIntent(
        imageUri = uri,
        chooserTitle = chooserTitle,
        caption = caption,
        fromActivityContext = context is Activity,
      )
      context.startActivity(chooser)
      Result.success(uri)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  private fun cleanupStaleFiles(dir: File, nowMillis: Long) {
    val files = dir.listFiles() ?: return
    for (file in files) {
      if (file.isFile && nowMillis - file.lastModified() > STALE_CACHE_MS) {
        runCatching { file.delete() }
      }
    }
  }
}
