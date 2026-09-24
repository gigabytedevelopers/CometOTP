@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Utilities

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
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

    init {
        val barcodeFormats = Vector<BarcodeFormat>()
        barcodeFormats.add(BarcodeFormat.QR_CODE)

        HINTS = EnumMap(DecodeHintType::class.java)
        HINTS[DecodeHintType.POSSIBLE_FORMATS] = barcodeFormats

        HINTS_HARDER = EnumMap(HINTS)
        HINTS_HARDER[DecodeHintType.TRY_HARDER] = java.lang.Boolean.TRUE
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
}
