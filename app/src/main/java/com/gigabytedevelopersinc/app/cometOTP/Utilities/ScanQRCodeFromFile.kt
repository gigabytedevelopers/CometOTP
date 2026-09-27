@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Utilities

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.annotation.WorkerThread
import androidx.core.graphics.scale
import com.gigabytedevelopersinc.app.cometOTP.R
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.ChecksumException
import com.google.zxing.DecodeHintType
import com.google.zxing.FormatException
import com.google.zxing.LuminanceSource
import com.google.zxing.NotFoundException
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import java.io.IOException
import java.util.EnumMap
import java.util.Vector

/**
 * Project - CometOTP
 * Created with Android Studio
 * Company: Gigabyte Developers
 * User: Emmanuel Nwokoma
 * Title: Founder and CEO
 * Day: Friday, 27
 * Month: December
 * Year: 2019
 * Date: 27 Dec, 2019
 * Time: 7:08 PM
 * Desc: ScanQRCodeFromFile
 **/
object ScanQRCodeFromFile {

    private val HINTS: MutableMap<DecodeHintType, Any>

    private val HINTS_HARDER: MutableMap<DecodeHintType, Any>

    /** The image is nothing but the code and its margin, as in a screenshot or a generated image. */
    private val HINTS_PURE: MutableMap<DecodeHintType, Any>

    init {
        val barcodeFormats = Vector<BarcodeFormat>()
        barcodeFormats.add(BarcodeFormat.QR_CODE)

        HINTS = EnumMap(DecodeHintType::class.java)
        HINTS[DecodeHintType.POSSIBLE_FORMATS] = barcodeFormats

        HINTS_HARDER = EnumMap(HINTS)
        HINTS_HARDER[DecodeHintType.TRY_HARDER] = java.lang.Boolean.TRUE

        HINTS_PURE = EnumMap(HINTS)
        HINTS_PURE[DecodeHintType.PURE_BARCODE] = java.lang.Boolean.TRUE
    }

    fun scanQRImage(context: Context, uri: Uri): String? {
        //Check if external storage is accessible
        if (!Tools.isExternalStorageReadable()) {
            Toast.makeText(context, R.string.backup_toast_storage_not_accessible, Toast.LENGTH_LONG).show()
            return null
        }
        //Get image in bytes
        val imageInBytes: ByteArray
        try {
            imageInBytes = StorageAccessHelper.loadFile(context, uri)
        } catch (e: IOException) {
            e.printStackTrace()
            Toast.makeText(context, R.string.toast_file_load_error, Toast.LENGTH_LONG).show()
            return null
        }

        // null when the file is not an image the platform can decode
        val bMap = BitmapFactory.decodeByteArray(imageInBytes, 0, imageInBytes.size)
        if (bMap == null) {
            Toast.makeText(context, R.string.toast_file_load_error, Toast.LENGTH_LONG).show()
            return null
        }
        var contents: String? = null
        val intArray = IntArray(bMap.width * bMap.height)

        bMap.getPixels(intArray, 0, bMap.width, 0, 0, bMap.width, bMap.height)
        val source: LuminanceSource = RGBLuminanceSource(bMap.width, bMap.height, intArray)
        val bitmap = BinaryBitmap(HybridBinarizer(source))

        val reader = QRCodeReader()
        var savedException: ReaderException? = null

        try {
            //Try finding QR code
            val result = reader.decode(bitmap, HINTS)
            contents = result.text
        } catch (re: ReaderException) {
            savedException = re
        }

        if (contents == null) {
            try {
                //Try finding QR code really hard
                val result = reader.decode(bitmap, HINTS_HARDER)
                contents = result.text
            } catch (re: ReaderException) {
                savedException = re
            }
        }

        // What the two attempts above miss. A failure is still reported from them, as before.
        if (contents == null)
            contents = fallbackDecode(bMap, intArray)

        if (contents == null) {
            try {
                throw savedException ?: NotFoundException.getNotFoundInstance()
            } catch (e: ChecksumException) {
                e.printStackTrace()
                Toast.makeText(context, R.string.toast_qr_checksum_exception, Toast.LENGTH_LONG).show()
            } catch (e: FormatException) {
                e.printStackTrace()
                Toast.makeText(context, R.string.toast_qr_format_error, Toast.LENGTH_LONG).show()
            } catch (e: ReaderException) {  // Including NotFoundException
                e.printStackTrace()
                Toast.makeText(context, R.string.toast_qr_error, Toast.LENGTH_LONG).show()
            }
        }

        //Return QR code (if found)
        return contents
    }

    /** Longest side a photo is scaled down to before looking for a code in it. */
    private const val QUIET_MAX_SIDE = 2048

    /**
     * Reads the QR code in an image without reporting anything, for going through several images
     * at once; returns null when there is none. Call it off the main thread.
     *
     * Photos are decoded at a reduced size: a code that fills a fair part of the frame reads fine
     * at 2048 pixels, and a 12-megapixel photo at full size needs about 100 MB. If nothing is found
     * at the reduced size, it tries once more at twice that.
     */
    @WorkerThread
    fun decodeQuietly(context: Context, uri: Uri): String? {
        val bytes = try {
            StorageAccessHelper.loadFile(context, uri)
        } catch (e: IOException) {
            return null
        }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        if (longest <= 0)
            return null

        var sample = 1
        while (longest / sample > QUIET_MAX_SIDE)
            sample *= 2

        return decodeAt(bytes, sample) ?: if (sample > 1) decodeAt(bytes, sample / 2) else null
    }

    private fun decodeAt(bytes: ByteArray, sample: Int): String? {
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return try {
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
            try {
                val pixels = pixelsOf(bitmap)
                decode(pixels, bitmap.width, bitmap.height, HINTS)
                        ?: decode(pixels, bitmap.width, bitmap.height, HINTS_HARDER)
                        ?: fallbackDecode(bitmap, pixels)
            } finally {
                bitmap.recycle()
            }
        } catch (e: OutOfMemoryError) {
            // Only the larger retry can get here; the image simply counts as unreadable.
            null
        }
    }

    /**
     * Codes ZXing's usual two attempts cannot read, including some perfectly clean generated ones:
     * reading the image as nothing but the code, which suits screenshots and generated images, then
     * reading it at three quarters of its size, which changes how the finder patterns fall on the
     * pixel grid. Both were chosen by testing codes the usual attempts fail on.
     */
    private fun fallbackDecode(bitmap: Bitmap, pixels: IntArray): String? {
        decode(pixels, bitmap.width, bitmap.height, HINTS_PURE)?.let { return it }

        return try {
            val smaller = bitmap.scale(bitmap.width * 3 / 4, bitmap.height * 3 / 4, filter = true)
            try {
                decode(pixelsOf(smaller), smaller.width, smaller.height, HINTS_HARDER)
            } finally {
                if (smaller !== bitmap)
                    smaller.recycle()
            }
        } catch (e: OutOfMemoryError) {
            null
        }
    }

    private fun pixelsOf(bitmap: Bitmap): IntArray {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return pixels
    }

    /** One attempt; null when no code is found. */
    private fun decode(pixels: IntArray, width: Int, height: Int, hints: Map<DecodeHintType, Any>): String? {
        return try {
            QRCodeReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(width, height, pixels))), hints).text
        } catch (e: ReaderException) {
            null
        }
    }
}
