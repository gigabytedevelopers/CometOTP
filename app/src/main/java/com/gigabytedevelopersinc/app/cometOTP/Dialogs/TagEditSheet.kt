@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Dialogs

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import com.gigabytedevelopersinc.app.cometOTP.R
import com.gigabytedevelopersinc.app.cometOTP.Utilities.TagStore
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

/**
 * "Create Tag" / "Edit Tag" bottom sheet: name, colour picker and the show-item-count switch.
 */
object TagEditSheet {

    fun interface Callback {
        /**
         * @param oldName the tag being edited, or null when a new tag is created
         */
        fun onTagSaved(oldName: String?, name: String, @ColorInt color: Int, showCount: Boolean)
    }

    @JvmStatic
    fun show(context: Context, existingTag: String?,
             takenNames: List<String>, callback: Callback) {
        val sheet = BottomSheetDialog(context)
        sheet.setContentView(R.layout.sheet_tag_edit)

        val title = sheet.findViewById<TextView>(R.id.sheetTitle)
        val close = sheet.findViewById<View>(R.id.sheetClose)
        val nameLayout = sheet.findViewById<TextInputLayout>(R.id.tag_name_layout)
        val nameInput = sheet.findViewById<TextInputEditText>(R.id.tag_name)
        val colorRow = sheet.findViewById<View>(R.id.tag_color_row)
        val colorDot = sheet.findViewById<View>(R.id.tag_color_dot)
        val colorName = sheet.findViewById<TextView>(R.id.tag_color_name)
        val colorChevron = sheet.findViewById<ImageView>(R.id.tag_color_chevron)
        val palette = sheet.findViewById<LinearLayout>(R.id.tag_palette)
        val showCount = sheet.findViewById<MaterialSwitch>(R.id.tag_show_count)
        val submit = sheet.findViewById<MaterialButton>(R.id.tag_submit)

        if (title == null || close == null || nameLayout == null || nameInput == null || colorRow == null
                || colorDot == null || colorName == null || colorChevron == null || palette == null
                || showCount == null || submit == null)
            return

        val isNew = existingTag == null
        var selected = TagStore.defaultColor(context)

        title.setText(if (isNew) R.string.tags_sheet_create else R.string.tags_sheet_edit)
        submit.setText(if (isNew) R.string.button_create else R.string.button_save)

        if (existingTag != null) {
            nameInput.setText(existingTag)
            val meta = TagStore.metaOf(context, existingTag)
            if (meta != null) {
                selected = meta.color
                showCount.isChecked = meta.showCount
            }
        }

        val applyColor = Runnable {
            tint(colorDot, selected)
            colorName.text = TagStore.colorName(context, selected)
        }
        applyColor.run()

        /* Colour picker: the row toggles a swatch strip open and closed. */
        val colors = TagStore.palette(context)
        val swatch = dp(context, 32)
        val gap = dp(context, 6)
        for (color in colors) {
            val holder = FrameLayout(context)
            val lp = LinearLayout.LayoutParams(swatch + gap * 2, swatch + gap * 2)
            holder.layoutParams = lp

            val dot = View(context)
            val dotParams = FrameLayout.LayoutParams(swatch, swatch, Gravity.CENTER)
            dot.layoutParams = dotParams
            dot.setBackgroundResource(R.drawable.bg_color_dot)
            tint(dot, color)
            holder.addView(dot)

            val ring = View(context)
            val ringParams = FrameLayout.LayoutParams(swatch + gap * 2, swatch + gap * 2, Gravity.CENTER)
            ring.layoutParams = ringParams
            ring.setBackgroundResource(R.drawable.bg_color_dot_selected)
            ring.visibility = if (color == selected) View.VISIBLE else View.GONE
            ring.tag = "ring:$color"
            holder.addView(ring)

            holder.setOnClickListener {
                selected = color
                for (i in 0 until palette.childCount) {
                    val child = palette.getChildAt(i)
                    if (child is FrameLayout) {
                        val childRing = child.getChildAt(1)
                        val tag = childRing.tag
                        childRing.visibility = if ("ring:$color" == tag) View.VISIBLE else View.GONE
                    }
                }
                applyColor.run()
            }

            palette.addView(holder)
        }

        colorRow.setOnClickListener {
            val open = palette.visibility != View.VISIBLE
            palette.visibility = if (open) View.VISIBLE else View.GONE
            colorChevron.setImageResource(if (open) R.drawable.ic_expand_less else R.drawable.ic_expand_more)
        }

        nameLayout.setEndIconOnClickListener {
            MaterialAlertDialogBuilder(context)
                    .setTitle(R.string.tags_hint_name)
                    .setMessage(R.string.tags_help_name)
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
        }

        val validate = Runnable {
            val text = nameInput.text
            // trim { it <= ' ' } is java.lang.String.trim(); Kotlin's trim() strips Unicode whitespace.
            val value = text?.toString()?.trim { it <= ' ' } ?: ""
            val duplicate = value.isNotEmpty()
                    && !value.equals(existingTag, ignoreCase = true)
                    && containsIgnoreCase(takenNames, value)

            nameLayout.error = if (duplicate) context.getString(R.string.tags_error_exists) else null
            submit.isEnabled = !TextUtils.isEmpty(value) && !duplicate
        }

        nameInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            }

            override fun afterTextChanged(s: Editable?) {
                validate.run()
            }
        })

        // Editing an existing tag starts with a valid name, so enable the button right away
        validate.run()

        close.setOnClickListener { sheet.dismiss() }
        submit.setOnClickListener {
            val text = nameInput.text
            val value = text?.toString()?.trim { it <= ' ' } ?: ""
            if (value.isEmpty())
                return@setOnClickListener

            sheet.dismiss()
            callback.onTagSaved(existingTag, value, selected, showCount.isChecked)
        }

        sheet.show()
    }

    private fun containsIgnoreCase(values: List<String>, value: String): Boolean {
        for (candidate in values) {
            if (candidate.equals(value, ignoreCase = true))
                return true
        }
        return false
    }

    private fun tint(view: View, @ColorInt color: Int) {
        var background = ContextCompat.getDrawable(view.context, R.drawable.bg_color_dot) as GradientDrawable?
        if (background != null) {
            background = background.mutate() as GradientDrawable
            background.setColor(color)
            view.background = background
        }
    }

    private fun dp(context: Context, value: Int): Int {
        return Math.round(value * context.resources.displayMetrics.density)
    }
}
