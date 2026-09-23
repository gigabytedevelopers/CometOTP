@file:Suppress("PackageName", "DEPRECATION", "OVERRIDE_DEPRECATION")
package com.gigabytedevelopersinc.app.cometOTP.Preferences

import android.content.Context
import android.content.res.TypedArray
import android.preference.DialogPreference
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.gigabytedevelopersinc.app.cometOTP.R
import com.gigabytedevelopersinc.app.cometOTP.Utilities.LauncherIcon

/**
 * Picks the launcher icon by showing each icon rather than only naming it.
 *
 * The previews come straight from the mipmaps the aliases use, so what the dialog shows is what
 * the launcher will show. Choosing a row commits immediately and closes the dialog, which is why
 * there is no positive button.
 */
class LauncherIconPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet?,
    defStyleAttr: Int = android.R.attr.dialogPreferenceStyle
) : DialogPreference(context, attrs, defStyleAttr) {

    private var value: String? = LauncherIcon.DEFAULT

    init {
        // Choosing a row is the commit, so the dialog only needs a way out.
        positiveButtonText = null
    }

    private fun labelFor(variant: String?): CharSequence {
        for (i in VALUES.indices) {
            if (VALUES[i] == variant)
                return context.getString(LABELS[i])
        }
        return context.getString(LABELS[0])
    }

    override fun getSummary(): CharSequence {
        return labelFor(value)
    }

    override fun onCreateDialogView(): View {
        val inflater = LayoutInflater.from(context)
        val root = inflater.inflate(R.layout.preference_launcher_icon, null) as LinearLayout

        for (i in VALUES.indices) {
            val variant = VALUES[i]
            val row = inflater.inflate(R.layout.item_launcher_icon, root, false)

            row.findViewById<ImageView>(R.id.icon_preview).setImageResource(PREVIEWS[i])
            row.findViewById<TextView>(R.id.icon_label).setText(LABELS[i])

            val selected = variant == value
            row.isSelected = selected
            row.findViewById<View>(R.id.icon_check).visibility =
                if (selected) View.VISIBLE else View.INVISIBLE
            row.contentDescription = context.getString(LABELS[i])

            row.setOnClickListener { choose(variant) }
            root.addView(row)
        }
        return root
    }

    private fun choose(variant: String) {
        if (callChangeListener(variant)) {
            value = variant
            persistString(variant)
            notifyChanged()
        }
        dialog.dismiss()
    }

    override fun onGetDefaultValue(a: TypedArray, index: Int): Any? {
        val fromXml = a.getString(index)
        return fromXml ?: LauncherIcon.DEFAULT
    }

    override fun onSetInitialValue(restorePersistedValue: Boolean, defaultValue: Any?) {
        val fallback = if (defaultValue is String) defaultValue else LauncherIcon.DEFAULT
        value = if (restorePersistedValue) getPersistedString(fallback) else fallback
        if (!restorePersistedValue)
            persistString(value)
    }

    /** Only used by the framework to size the dialog; the rows do the work. */
    override fun onBindDialogView(view: View) {
        super.onBindDialogView(view)
        val root = view as ViewGroup
        root.minimumWidth = 0
    }

    companion object {
        private val VALUES = arrayOf(
            LauncherIcon.BLUE, LauncherIcon.MONO, LauncherIcon.WHITE, LauncherIcon.CLASSIC)

        private val LABELS = intArrayOf(
            R.string.settings_entry_launcher_icon_blue,
            R.string.settings_entry_launcher_icon_mono,
            R.string.settings_entry_launcher_icon_white,
            R.string.settings_entry_launcher_icon_classic)

        private val PREVIEWS = intArrayOf(
            R.mipmap.ic_launcher_blue, R.mipmap.ic_launcher_mono,
            R.mipmap.ic_launcher_white, R.mipmap.ic_launcher_classic)
    }
}
