@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Activities

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewStub
import android.widget.Button
import androidx.appcompat.widget.Toolbar
import com.gigabytedevelopersinc.app.cometOTP.R
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.snackbar.Snackbar
import java.io.File
import java.text.DecimalFormat

/**
 * @author Created by Emmanuel Nwokoma (Founder and CEO at Gigabyte Developers) on 6/21/2018
 **/
class CacheActivity : BaseActivity() {

    private var mBottomSheetDialog: BottomSheetDialog? = null

    @SuppressLint("MissingInflatedId")   // the ids live in the layout inflated through the ViewStub
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTitle(R.string.clear_cache_button)
        setContentView(R.layout.activity_container)

        val toolbar = findViewById<Toolbar>(R.id.container_toolbar)
        setSupportActionBar(toolbar)

        val stub = findViewById<ViewStub>(R.id.container_stub)
        stub.layoutResource = R.layout.clear_cache_activity
        stub.inflate()

        val clearCache = findViewById<Button>(R.id.clear_cache_button)
        clearCache.setOnClickListener {
            val bottomSheetLayout = layoutInflater.inflate(R.layout.bottom_sheet_cache_dialog, null)
            bottomSheetLayout.findViewById<View>(R.id.button_no).setOnClickListener { mBottomSheetDialog!!.dismiss() }
            bottomSheetLayout.findViewById<View>(R.id.button_yes).setOnClickListener {
                deleteCache(baseContext)
                mBottomSheetDialog!!.dismiss()
                Snackbar.make(findViewById(R.id.clear_cache_layout),
                        "CometOTP Cache Storage has been Cleared!",
                        Snackbar.LENGTH_LONG).show()
            }
            mBottomSheetDialog = BottomSheetDialog(this@CacheActivity)
            mBottomSheetDialog!!.setContentView(bottomSheetLayout)
            mBottomSheetDialog!!.setCancelable(false)
            mBottomSheetDialog!!.show()
        }
    }

    override fun onDestroy() {
        // A sheet still showing when the screen closes (rotation, finish) would leak its window.
        mBottomSheetDialog?.dismiss()
        mBottomSheetDialog = null
        super.onDestroy()
    }

    companion object {
        @JvmStatic
        fun deleteCache(context: Context) {
            try {
                val dir = context.cacheDir
                deleteDir(dir)
            } catch (e: Exception) {
                //
            }
        }

        @JvmStatic
        fun deleteDir(dir: File?): Boolean {
            return if (dir != null && dir.isDirectory) {
                // list() is null when the folder cannot be read; it cannot be emptied then either.
                val children = dir.list() ?: return false
                for (aChildren in children) {
                    val success = deleteDir(File(dir, aChildren))
                    if (!success) {
                        return false
                    }
                }
                dir.delete()
            } else
                dir != null && dir.isFile && dir.delete()
        }

        @JvmStatic
        fun readableFileSize(size: Long): String {
            if (size <= 0) return "0 Bytes"
            val units = arrayOf("Bytes", "kB", "MB", "GB", "TB")
            val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
            return DecimalFormat("#,##0.#").format(size / Math.pow(1024.0, digitGroups.toDouble())) + " " + units[digitGroups]
        }
    }
}
