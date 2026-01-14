package pl.pg.pwta.pwta2d3dmodels.ui

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import pl.pg.pwta.pwta2d3dmodels.model.AssetInfo
import pl.pg.pwta.pwta2d3dmodels.model.ImageAsset
import pl.pg.pwta.pwta2d3dmodels.model.Model3DAsset
import java.util.Date
import pl.pg.pwta.pwta2d3dmodels.model.ModelFormat
import pl.pg.pwta.pwta2d3dmodels.model.ModelInfo
import android.graphics.BitmapFactory
import android.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.exifinterface.media.ExifInterface
import java.text.SimpleDateFormat
import java.util.*
import com.caverock.androidsvg.SVG
import pl.pg.pwta.pwta2d3dmodels.model.ImageColorModel
import pl.pg.pwta.pwta2d3dmodels.model.ImageInfo

fun loadAsset(
    context: Context,
    uri: Uri
): AssetInfo {
    val baseInfo = getBaseInfo(context, uri);
    return when (baseInfo.format) {
        ModelFormat.PNG,
        ModelFormat.JPEG,
        ModelFormat.WEBP,
        ModelFormat.SVG -> {
            val imageInfo =
                if (baseInfo.format == ModelFormat.SVG)
                    readSvgImageInfo(context, uri)
                else
                    readRasterImageInfo(context, uri, baseInfo.mimeType ?: "")
            ImageAsset(base = baseInfo, imageInfo = imageInfo)
        }
        ModelFormat.OBJ,
        ModelFormat.GLTF,
        ModelFormat.GLB,
        ModelFormat.STL,
        ModelFormat.PLY -> {
            Model3DAsset(base = baseInfo)
        }
        else -> {
            Model3DAsset(base = baseInfo)
        }
    }
}

fun readRasterImageInfo(
    context: Context,
    uri: Uri,
    mimeType: String
): ImageInfo {
    val opts = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
    }
    context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, opts)
    }
    val width = opts.outWidth.takeIf { it > 0 }
    val height = opts.outHeight.takeIf { it > 0 }
    val exif = runCatching {
        context.contentResolver.openInputStream(uri)?.use { ExifInterface(it) }
    }.getOrNull()
    fun exifDate(tag: String): Date? =
        exif?.getAttribute(tag)?.let {
            runCatching {
                SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).parse(it)
            }.getOrNull()
        }
    val orientation = exif?.getAttributeInt(
        ExifInterface.TAG_ORIENTATION,
        ExifInterface.ORIENTATION_UNDEFINED
    )?.let {
        when (it) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    }
    val hasAlpha = when {
        mimeType.contains("png", true) -> true
        mimeType.contains("webp", true) -> true
        mimeType.contains("jpeg", true) -> false
        else -> null
    }
    val colorModel = when {
        hasAlpha == true -> ImageColorModel.RGBA
        hasAlpha == false -> ImageColorModel.RGB
        else -> ImageColorModel.UNKNOWN
    }
    val latLong = exif?.latLong
    return ImageInfo(
        widthPx = width,
        heightPx = height,
        viewBoxWidth = null,
        viewBoxHeight = null,
        aspectRatio = if (width != null && height != null) width.toFloat() / height else null,
        isVector = false,
        hasAlpha = hasAlpha,
        colorModel = colorModel,
        bitDepthPerChannel = 8,
        dpiX = exif?.getAttributeInt(ExifInterface.TAG_X_RESOLUTION, -1)?.takeIf { it > 0 },
        dpiY = exif?.getAttributeInt(ExifInterface.TAG_Y_RESOLUTION, -1)?.takeIf { it > 0 },
        exifOrientation = orientation,
        exifDateTime = exifDate(ExifInterface.TAG_DATETIME),
        exifDateTimeOriginal = exifDate(ExifInterface.TAG_DATETIME_ORIGINAL),
        exifDateTimeDigitized = exifDate(ExifInterface.TAG_DATETIME_DIGITIZED),
        subSecTime = exif?.getAttribute(ExifInterface.TAG_SUBSEC_TIME),
        cameraMake = exif?.getAttribute(ExifInterface.TAG_MAKE),
        cameraModel = exif?.getAttribute(ExifInterface.TAG_MODEL),
        software = exif?.getAttribute(ExifInterface.TAG_SOFTWARE),
        lensMake = exif?.getAttribute(ExifInterface.TAG_LENS_MAKE),
        lensModel = exif?.getAttribute(ExifInterface.TAG_LENS_MODEL),
        exposureTime = exif?.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, Double.NaN)
            ?.takeIf { !it.isNaN() },
        fNumber = exif?.getAttributeDouble(ExifInterface.TAG_F_NUMBER, Double.NaN)
            ?.takeIf { !it.isNaN() },
        isoSpeed = exif?.getAttributeInt(ExifInterface.TAG_ISO_SPEED_RATINGS, -1)
            ?.takeIf { it > 0 },
        focalLength = exif?.getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH, Double.NaN)
            ?.takeIf { !it.isNaN() },
        exposureBias = exif?.getAttributeDouble(ExifInterface.TAG_EXPOSURE_BIAS_VALUE, Double.NaN)
            ?.takeIf { !it.isNaN() },
        meteringMode = exif?.getAttributeInt(ExifInterface.TAG_METERING_MODE, -1)
            ?.takeIf { it >= 0 },
        whiteBalance = exif?.getAttributeInt(ExifInterface.TAG_WHITE_BALANCE, -1)
            ?.takeIf { it >= 0 },
        flash = exif?.getAttributeInt(ExifInterface.TAG_FLASH, -1)
            ?.takeIf { it >= 0 },
        brightnessValue = exif?.getAttributeDouble(ExifInterface.TAG_BRIGHTNESS_VALUE, Double.NaN)
            ?.takeIf { !it.isNaN() },
        contrast = exif?.getAttributeInt(ExifInterface.TAG_CONTRAST, -1)
            ?.takeIf { it >= 0 },
        saturation = exif?.getAttributeInt(ExifInterface.TAG_SATURATION, -1)
            ?.takeIf { it >= 0 },
        sharpness = exif?.getAttributeInt(ExifInterface.TAG_SHARPNESS, -1)
            ?.takeIf { it >= 0 },
        hasGpsMetadata = latLong != null,
        gpsLatitude = latLong?.get(0),
        gpsLongitude = latLong?.get(1),
        gpsAltitude = exif?.getAltitude(Double.NaN)?.takeIf { !it.isNaN() },
        gpsTimestamp = exif?.getAttribute(ExifInterface.TAG_GPS_TIMESTAMP)?.let {
            runCatching {
                SimpleDateFormat("HH:mm:ss", Locale.US).parse(it)
            }.getOrNull()
        },
        mimeType = mimeType
    )
}

// TODO: pobrac wiecej informacji o SVG
fun readSvgImageInfo(
    context: Context,
    uri: Uri
): ImageInfo {
    val svg = context.contentResolver.openInputStream(uri)?.use {
        SVG.getFromInputStream(it)
    }
    val vb = svg?.documentViewBox
    val vbW = vb?.width()
    val vbH = vb?.height()
    val declaredWidth = svg?.documentWidth
        ?.takeIf { it > 0f && it.isFinite() }
    val declaredHeight = svg?.documentHeight
        ?.takeIf { it > 0f && it.isFinite() }
    return ImageInfo(
        widthPx = null,
        heightPx = null,
        viewBoxWidth = vbW,
        viewBoxHeight = vbH,
        aspectRatio = when {
            vbW != null && vbH != null && vbH != 0f -> vbW / vbH
            declaredWidth != null && declaredHeight != null && declaredHeight != 0f ->
                declaredWidth / declaredHeight
            else -> null
        },
        isVector = true,
        hasAlpha = null,
        colorModel = null,
        bitDepthPerChannel = null,
        dpiX = null,
        dpiY = null,
        exifOrientation = null,
        exifDateTime = null,
        exifDateTimeOriginal = null,
        exifDateTimeDigitized = null,
        subSecTime = null,
        cameraMake = null,
        cameraModel = null,
        software = null,
        lensMake = null,
        lensModel = null,
        exposureTime = null,
        fNumber = null,
        isoSpeed = null,
        focalLength = null,
        exposureBias = null,
        meteringMode = null,
        whiteBalance = null,
        flash = null,
        brightnessValue = null,
        contrast = null,
        saturation = null,
        sharpness = null,
        hasGpsMetadata = null,
        gpsLatitude = null,
        gpsLongitude = null,
        gpsAltitude = null,
        gpsTimestamp = null,
        mimeType = "image/svg+xml"
    )
}


fun getBaseInfo(
    context: Context,
    uri: Uri
): ModelInfo {
    val resolver = context.contentResolver
    var fileName = "unknown"
    var fileSize = -1L
    var mimeType: String? = null
    var createdAt: Date? = null
    var modifiedAt: Date? = null
    resolver.query(uri, null, null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            fun getLong(name: String): Long? {
                val idx = cursor.getColumnIndex(name)
                return if (idx >= 0 && !cursor.isNull(idx)) cursor.getLong(idx) else null
            }
            fun getString(name: String): String? {
                val idx = cursor.getColumnIndex(name)
                return if (idx >= 0 && !cursor.isNull(idx)) cursor.getString(idx) else null
            }
            getString(OpenableColumns.DISPLAY_NAME)?.let {
                fileName = it
            }
            getLong(OpenableColumns.SIZE)?.let {
                fileSize = it
            }
            getLong("date_added")?.let {
                createdAt = Date(it * 1000) // sekundy → ms
            }
            getLong("last_modified")?.let {
                modifiedAt = Date(it)
            }
        }
    }
    mimeType = resolver.getType(uri)
    val extension = fileName
        .substringAfterLast('.', "")
        .lowercase()
    val format = when (extension) {
        "png" -> ModelFormat.PNG
        "jpg", "jpeg" -> ModelFormat.JPEG
        "svg" -> ModelFormat.SVG
        "webp" -> ModelFormat.WEBP
        "obj" -> ModelFormat.OBJ
        "gltf" -> ModelFormat.GLTF
        "glb" -> ModelFormat.GLB
        "ply" -> ModelFormat.PLY
        "stl" -> ModelFormat.STL
        else -> ModelFormat.UNKNOWN
    }
    val isReadable = try {
        resolver.openInputStream(uri)?.close()
        true
    } catch (_: Exception) {
        false
    }
    val isWritable = try {
        resolver.openOutputStream(uri)?.close()
        true
    } catch (_: Exception) {
        false
    }
    return ModelInfo(
        fileName = fileName,
        filePath = uri.toString(),
        extension = extension,
        format = format,
        uri = uri.toString(),
        isEmbedded = false,
        fileSizeBytes = fileSize,
        createdAt = createdAt,
        modifiedAt = modifiedAt,
        accessedAt = null,
        mimeType = mimeType,
        isReadable = isReadable,
        isWritable = isWritable
    )
}

fun loadRasterBitmap(
    context: Context,
    uri: Uri
): ImageBitmap {
    val bitmap = context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it)
    }
    return bitmap!!.asImageBitmap()
}

fun loadSvgBitmap(
    context: Context,
    uri: Uri,
    sizePx: Int = 2048
): ImageBitmap {
    val svg = context.contentResolver.openInputStream(uri)?.use {
        SVG.getFromInputStream(it)
    }
    val picture = svg!!.renderToPicture()
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawPicture(picture)
    return bitmap.asImageBitmap()
}
