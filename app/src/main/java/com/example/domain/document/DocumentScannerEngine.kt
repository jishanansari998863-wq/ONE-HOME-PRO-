package com.example.domain.document

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.media.ExifInterface
import android.net.Uri
import java.io.ByteArrayOutputStream
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class DocumentInputMode {
    TAKE_PHOTO,
    SCAN_DOCUMENT,
    GALLERY,
    UPLOAD_FILE
}

enum class DocumentCategoryOption(
    val code: String,
    val label: String,
    val frameAspectRatio: Float // width / height
) {
    QID("QID", "Qatar ID", 1.586f),
    PASSPORT("PASSPORT", "Passport", 1.42f),
    RENTAL_AGREEMENT("AGREEMENT", "Rental Agreement", 0.707f),
    RECEIPT("RECEIPT", "Receipt", 0.65f),
    INVOICE("INVOICE", "Invoice", 0.707f),
    CHEQUE("CHEQUE", "Cheque", 2.15f),
    PROPERTY_DOCUMENT("PROPERTY_RECORD", "Property Document", 0.707f),
    OTHER("OTHER", "Other Document", 0.75f)
}

enum class DocumentTargetType(val entityTypeCode: String, val label: String) {
    TENANT("CUSTOMER", "Tenant"),
    PROPERTY("PROPERTY", "Property"),
    EMPLOYEE("EMPLOYEE", "Employee"),
    AGREEMENT("AGREEMENT", "Rental Agreement"),
    PAYMENT("PAYMENT", "Payment"),
    BUSINESS("BUSINESS", "Company / Branch")
}

enum class ReadabilityMode(val label: String) {
    ORIGINAL("Original"),
    AUTO_ENHANCE("Auto Enhance"),
    COLOR_SCAN("Color Scan"),
    CRISP_BW("Crisp B&W")
}

enum class DocumentOutputFormat(val mimeType: String, val ext: String, val label: String) {
    PDF("application/pdf", "pdf", "PDF"),
    JPG("image/jpeg", "jpg", "JPG"),
    JPEG("image/jpeg", "jpeg", "JPEG"),
    PNG("image/png", "png", "PNG")
}

data class NormalizedCropRect(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 1f,
    val bottom: Float = 1f
) {
    fun normalized(): NormalizedCropRect {
        val l = left.coerceIn(0f, 0.8f)
        val t = top.coerceIn(0f, 0.8f)
        val r = right.coerceIn(l + 0.15f, 1f)
        val b = bottom.coerceIn(t + 0.15f, 1f)
        return NormalizedCropRect(l, t, r, b)
    }

    val isFullFrame: Boolean
        get() = left <= 0.01f && top <= 0.01f && right >= 0.99f && bottom >= 0.99f
}

data class ScannedPageDraft(
    val id: String,
    val sourceUri: Uri?,
    val sourceBitmap: Bitmap,
    val rotationDegrees: Int = 0,
    val cropRect: NormalizedCropRect = NormalizedCropRect(),
    val autoDetectedCrop: NormalizedCropRect = NormalizedCropRect(),
    val readabilityMode: ReadabilityMode = ReadabilityMode.AUTO_ENHANCE,
    val processedBitmap: Bitmap = sourceBitmap
)

data class DocumentUploadProgress(
    val isUploading: Boolean = false,
    val fraction: Float = 0f,
    val stageLabel: String = "",
    val errorMessage: String? = null,
    val successMessage: String? = null
)

data class ValidatedUploadFile(
    val bytes: ByteArray,
    val mimeType: String,
    val extension: String,
    val displayName: String
)

object DocumentImageProcessor {

    private val SUPPORTED_EXTENSIONS = setOf("jpg", "jpeg", "png", "pdf")

    fun loadOrientedBitmap(context: Context, uri: Uri, maxDimension: Int = 1800): Bitmap {
        val resolver = context.contentResolver
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, boundsOpts)
        } ?: throw IllegalArgumentException("Unable to open image stream")

        val rawW = boundsOpts.outWidth
        val rawH = boundsOpts.outHeight
        require(rawW > 0 && rawH > 0) { "Selected file is not a valid JPG/PNG image" }

        var sampleSize = 1
        while (max(rawW / sampleSize, rawH / sampleSize) > maxDimension) {
            sampleSize *= 2
        }

        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val decoded = resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOpts)
        } ?: throw IllegalArgumentException("Unable to decode image bitmap")

        val exifRotation = runCatching {
            resolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            } ?: 0
        }.getOrDefault(0)

        return if (exifRotation == 0) decoded else rotateBitmap(decoded, exifRotation)
    }

    /**
     * Detects likely document bounds inside a captured bitmap by evaluating border background
     * luminance and local pixel gradient magnitude on a downscaled analysis grid.
     */
    fun detectDocumentBounds(bitmap: Bitmap): NormalizedCropRect {
        val targetMax = 160
        val scale = min(targetMax.toFloat() / bitmap.width, targetMax.toFloat() / bitmap.height).coerceAtMost(1f)
        val w = max(24, (bitmap.width * scale).roundToInt())
        val h = max(24, (bitmap.height * scale).roundToInt())
        val small = if (w == bitmap.width && h == bitmap.height) bitmap else Bitmap.createScaledBitmap(bitmap, w, h, true)

        val pixels = IntArray(w * h)
        small.getPixels(pixels, 0, w, 0, 0, w, h)

        val lum = IntArray(w * h)
        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            lum[i] = (r * 30 + g * 59 + b * 11) / 100
        }

        // Estimate background border luminance from outer ring
        var borderSum = 0L
        var borderCount = 0
        for (x in 0 until w) {
            borderSum += lum[x] + lum[(h - 1) * w + x]
            borderCount += 2
        }
        for (y in 1 until h - 1) {
            borderSum += lum[y * w] + lum[y * w + (w - 1)]
            borderCount += 2
        }
        val borderMean = (borderSum / max(1, borderCount)).toInt()

        var minX = w
        var minY = h
        var maxX = -1
        var maxY = -1
        var activeHits = 0

        for (y in 1 until h - 1) {
            val rowOffset = y * w
            for (x in 1 until w - 1) {
                val center = lum[rowOffset + x]
                val gx = abs(lum[rowOffset + x + 1] - lum[rowOffset + x - 1])
                val gy = abs(lum[(y + 1) * w + x] - lum[(y - 1) * w + x])
                val gradient = gx + gy
                val lumDiff = abs(center - borderMean)

                if (gradient >= 42 || lumDiff >= 38) {
                    activeHits++
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }

        // Require meaningful foreground area (>= 5% of frame) and valid span (>= 25% dimensions)
        val totalArea = w * h
        if (activeHits < totalArea / 20 || maxX - minX < w / 4 || maxY - minY < h / 4) {
            return NormalizedCropRect(0f, 0f, 1f, 1f)
        }

        val padX = 0.02f
        val padY = 0.02f
        return NormalizedCropRect(
            left = ((minX.toFloat() / w) - padX).coerceIn(0f, 0.7f),
            top = ((minY.toFloat() / h) - padY).coerceIn(0f, 0.7f),
            right = ((maxX.toFloat() / w) + padX).coerceIn(0.3f, 1f),
            bottom = ((maxY.toFloat() / h) + padY).coerceIn(0.3f, 1f)
        ).normalized()
    }

    fun rotateBitmap(bitmap: Bitmap, degrees: Int): Bitmap {
        val norm = ((degrees % 360) + 360) % 360
        if (norm == 0) return bitmap
        val matrix = Matrix().apply { postRotate(norm.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    fun processPageBitmap(
        source: Bitmap,
        rotationDegrees: Int,
        cropRect: NormalizedCropRect,
        mode: ReadabilityMode
    ): Bitmap {
        val safeCrop = cropRect.normalized()
        val cropX = (safeCrop.left * source.width).roundToInt().coerceIn(0, source.width - 1)
        val cropY = (safeCrop.top * source.height).roundToInt().coerceIn(0, source.height - 1)
        val cropW = ((safeCrop.right - safeCrop.left) * source.width).roundToInt().coerceIn(1, source.width - cropX)
        val cropH = ((safeCrop.bottom - safeCrop.top) * source.height).roundToInt().coerceIn(1, source.height - cropY)

        val cropped = Bitmap.createBitmap(source, cropX, cropY, cropW, cropH)
        val rotated = rotateBitmap(cropped, rotationDegrees)
        if (mode == ReadabilityMode.ORIGINAL) return rotated

        val result = Bitmap.createBitmap(rotated.width, rotated.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        val cm = ColorMatrix()
        when (mode) {
            ReadabilityMode.ORIGINAL -> Unit
            ReadabilityMode.AUTO_ENHANCE -> {
                // Contrast 1.28f + subtle brightness lift + clarity saturation
                val contrast = 1.28f
                val translate = (-0.5f * contrast + 0.5f) * 255f + 12f
                cm.set(
                    floatArrayOf(
                        contrast, 0f, 0f, 0f, translate,
                        0f, contrast, 0f, 0f, translate,
                        0f, 0f, contrast, 0f, translate,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            }
            ReadabilityMode.COLOR_SCAN -> {
                val sat = ColorMatrix().apply { setSaturation(1.22f) }
                val contrast = 1.32f
                val translate = (-0.5f * contrast + 0.5f) * 255f + 16f
                val con = ColorMatrix(
                    floatArrayOf(
                        contrast, 0f, 0f, 0f, translate,
                        0f, contrast, 0f, 0f, translate,
                        0f, 0f, contrast, 0f, translate,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                cm.postConcat(sat)
                cm.postConcat(con)
            }
            ReadabilityMode.CRISP_BW -> {
                val gray = ColorMatrix().apply { setSaturation(0f) }
                val contrast = 1.85f
                val translate = (-0.5f * contrast + 0.5f) * 255f + 20f
                val con = ColorMatrix(
                    floatArrayOf(
                        contrast, 0f, 0f, 0f, translate,
                        0f, contrast, 0f, 0f, translate,
                        0f, 0f, contrast, 0f, translate,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                cm.postConcat(gray)
                cm.postConcat(con)
            }
        }
        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(rotated, 0f, 0f, paint)
        return result
    }

    fun encodePages(
        pages: List<Bitmap>,
        format: DocumentOutputFormat,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): ByteArray {
        require(pages.isNotEmpty()) { "At least one page is required" }
        return if (format == DocumentOutputFormat.PDF || pages.size > 1) {
            buildMultiPagePdf(pages, onProgress)
        } else {
            onProgress(0.45f, "Encoding ${format.label} image...")
            val page = pages.first()
            val baos = ByteArrayOutputStream()
            val compressFmt = if (format == DocumentOutputFormat.PNG) {
                Bitmap.CompressFormat.PNG
            } else {
                Bitmap.CompressFormat.JPEG
            }
            val quality = if (format == DocumentOutputFormat.PNG) 100 else 92
            check(page.compress(compressFmt, quality, baos)) { "Failed to encode ${format.label} image" }
            onProgress(0.75f, "Prepared ${format.label} payload")
            baos.toByteArray()
        }
    }

    fun buildMultiPagePdf(
        pages: List<Bitmap>,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): ByteArray {
        require(pages.isNotEmpty()) { "Cannot build PDF without scanned pages" }
        val nativeAttempt = runCatching {
            val pdf = PdfDocument()
            try {
                pages.forEachIndexed { idx, bmp ->
                    val frac = 0.25f + (0.50f * ((idx + 1).toFloat() / pages.size.toFloat()))
                    onProgress(frac, "Combining page ${idx + 1} of ${pages.size} into PDF...")

                    val isLandscape = bmp.width > bmp.height
                    val pageW = if (isLandscape) 842 else 595
                    val pageH = if (isLandscape) 595 else 842
                    val margin = 16f
                    val availW = pageW - (margin * 2f)
                    val availH = pageH - (margin * 2f)
                    val scale = min(availW / bmp.width.toFloat(), availH / bmp.height.toFloat())
                    val drawW = bmp.width * scale
                    val drawH = bmp.height * scale
                    val dx = (pageW - drawW) / 2f
                    val dy = (pageH - drawH) / 2f

                    val pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, idx + 1).create()
                    val page = pdf.startPage(pageInfo)
                    val destRect = android.graphics.RectF(dx, dy, dx + drawW, dy + drawH)
                    page.canvas.drawColor(android.graphics.Color.WHITE)
                    page.canvas.drawBitmap(bmp, null, destRect, Paint(Paint.FILTER_BITMAP_FLAG))
                    pdf.finishPage(page)
                }
                val out = ByteArrayOutputStream()
                pdf.writeTo(out)
                out.toByteArray()
            } finally {
                runCatching { pdf.close() }
            }
        }.getOrNull()

        if (nativeAttempt != null && nativeAttempt.size > 100) {
            return nativeAttempt
        }
        return writePureKotlinMultiPagePdf(pages, onProgress)
    }

    fun writePureKotlinMultiPagePdf(
        pages: List<Bitmap>,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): ByteArray {
        val out = ByteArrayOutputStream()
        fun writeAscii(text: String) {
            out.write(text.toByteArray(Charsets.US_ASCII))
        }

        val offsets = mutableListOf<Int>()
        writeAscii("%PDF-1.4\n%\u00E2\u00E3\u00CF\u00D3\n")

        // Obj 1: Catalog
        offsets.add(out.size())
        writeAscii("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n")

        // Obj 2: Pages (kids are 3, 6, 9, ...)
        val kidRefs = pages.indices.joinToString(" ") { idx -> "${3 + idx * 3} 0 R" }
        offsets.add(out.size())
        writeAscii("2 0 obj\n<< /Type /Pages /Kids [$kidRefs] /Count ${pages.size} >>\nendobj\n")

        pages.forEachIndexed { idx, bmp ->
            val frac = 0.25f + (0.50f * ((idx + 1).toFloat() / pages.size.toFloat()))
            onProgress(frac, "Combining page ${idx + 1} of ${pages.size} into PDF...")

            val jpegStream = ByteArrayOutputStream()
            check(bmp.compress(Bitmap.CompressFormat.JPEG, 90, jpegStream)) {
                "Failed to compress scanned page ${idx + 1}"
            }
            val jpegBytes = jpegStream.toByteArray()

            val isLandscape = bmp.width > bmp.height
            val pageW = if (isLandscape) 842 else 595
            val pageH = if (isLandscape) 595 else 842
            val margin = 16f
            val availW = pageW - (margin * 2f)
            val availH = pageH - (margin * 2f)
            val scale = min(availW / bmp.width.toFloat(), availH / bmp.height.toFloat())
            val drawW = bmp.width * scale
            val drawH = bmp.height * scale
            val dx = (pageW - drawW) / 2f
            val dy = (pageH - drawH) / 2f

            val pageObjId = 3 + idx * 3
            val imgObjId = pageObjId + 1
            val contentObjId = pageObjId + 2

            // Page object
            offsets.add(out.size())
            writeAscii(
                "$pageObjId 0 obj\n" +
                    "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 $pageW $pageH] " +
                    "/Resources << /XObject << /Im0 $imgObjId 0 R >> >> " +
                    "/Contents $contentObjId 0 R >>\nendobj\n"
            )

            // Image XObject
            offsets.add(out.size())
            writeAscii(
                "$imgObjId 0 obj\n" +
                    "<< /Type /XObject /Subtype /Image /Width ${bmp.width} /Height ${bmp.height} " +
                    "/ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length ${jpegBytes.size} >>\nstream\n"
            )
            out.write(jpegBytes)
            writeAscii("\nendstream\nendobj\n")

            // Content stream
            val contentCmd = "q %.2f 0 0 %.2f %.2f %.2f cm /Im0 Do Q".format(java.util.Locale.US, drawW, drawH, dx, dy)
            val contentBytes = contentCmd.toByteArray(Charsets.US_ASCII)
            offsets.add(out.size())
            writeAscii("$contentObjId 0 obj\n<< /Length ${contentBytes.size} >>\nstream\n")
            out.write(contentBytes)
            writeAscii("\nendstream\nendobj\n")
        }

        val xrefOffset = out.size()
        val totalObjs = offsets.size + 1
        writeAscii("xref\n0 $totalObjs\n0000000000 65535 f \n")
        offsets.forEach { offset ->
            writeAscii("%010d 00000 n \n".format(java.util.Locale.US, offset))
        }
        writeAscii("trailer\n<< /Size $totalObjs /Root 1 0 R >>\nstartxref\n$xrefOffset\n%%EOF\n")
        return out.toByteArray()
    }

    fun validatePickedFile(context: Context, uri: Uri): ValidatedUploadFile {
        val resolver = context.contentResolver
        val rawMime = resolver.getType(uri)?.lowercase().orEmpty()
        val segment = uri.lastPathSegment.orEmpty()
        val ext = segment.substringAfterLast('.', "").lowercase()

        val resolvedMime = when {
            rawMime == "application/pdf" || ext == "pdf" -> "application/pdf"
            rawMime == "image/png" || ext == "png" -> "image/png"
            rawMime == "image/jpeg" || rawMime == "image/jpg" || ext == "jpg" || ext == "jpeg" -> "image/jpeg"
            else -> throw IllegalArgumentException("Unsupported format. Please select JPG, JPEG, PNG, or PDF.")
        }
        val resolvedExt = when {
            resolvedMime == "application/pdf" -> "pdf"
            resolvedMime == "image/png" -> "png"
            ext == "jpeg" -> "jpeg"
            else -> "jpg"
        }
        require(resolvedExt in SUPPORTED_EXTENSIONS) { "Unsupported file extension" }

        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalArgumentException("Unable to read selected file")
        require(bytes.isNotEmpty() && bytes.size <= 15 * 1024 * 1024) {
            "Document must be between 1 byte and 15 MB"
        }

        // Magic header sanity check for PDF and images
        if (resolvedMime == "application/pdf") {
            val header = bytes.take(5).toByteArray().toString(Charsets.US_ASCII)
            require(header == "%PDF-") { "Selected PDF file is invalid or corrupted" }
        }

        val name = segment.substringAfterLast('/').ifBlank { "Document.$resolvedExt" }
        return ValidatedUploadFile(
            bytes = bytes,
            mimeType = resolvedMime,
            extension = resolvedExt,
            displayName = name
        )
    }
}
