@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Dialogs

import android.content.Context
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatDialog
import com.gigabytedevelopersinc.app.cometOTP.R
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Settings
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Tools

/**
 * Project - CometOTP
 * Created with Android Studio
 * Company: Gigabyte Developers
 * User: Emmanuel Nwokoma
 * Title: Founder and CEO
 * Day: Friday, 16
 * Month: July
 * Year: 2021
 * Date: 16 Jul, 2021
 * Time: 3:32 AM
 * Desc: HideableDialog
 **/
class HideableDialog(context: Context, titleId: Int, msgId: Int, private val hideSettingId: Int) :
    AppCompatDialog(context, Tools.getThemeResource(context, R.attr.dialogTheme)),
    View.OnClickListener, CompoundButton.OnCheckedChangeListener {

    private val settings: Settings = Settings(context)

    init {
        setTitle(titleId)
        setContentView(R.layout.dialog_dont_show_again)

        // The Java version asserted these were non-null (a no-op at runtime) and then used them.
        val content = findViewById<TextView>(R.id.dialogContent)!!
        val dontShowAgain = findViewById<CheckBox>(R.id.dontShowAgain)!!
        val buttonOk = findViewById<Button>(R.id.buttonOk)!!

        content.setText(msgId)

        dontShowAgain.setOnCheckedChangeListener(this)
        buttonOk.setOnClickListener(this)
    }

    override fun onCheckedChanged(compoundButton: CompoundButton, b: Boolean) {
        if (hideSettingId > 0)
            settings.setBoolean(hideSettingId, b)
    }

    override fun onClick(view: View) {
        dismiss()
    }

    companion object {
        @JvmStatic
        fun ShowHideableDialog(context: Context, titleId: Int, msgId: Int, hideSettingId: Int) {
            val settings = Settings(context)

            if (!settings.getBoolean(hideSettingId, false)) {
                val dialog = HideableDialog(context, titleId, msgId, hideSettingId)
                dialog.show()
            }
        }
    }
}
