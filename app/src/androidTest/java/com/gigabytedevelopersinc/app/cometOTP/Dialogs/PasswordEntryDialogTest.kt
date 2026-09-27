@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Dialogs

import android.graphics.Color
import android.view.ContextThemeWrapper
import android.widget.Button
import android.widget.EditText
import androidx.test.platform.app.InstrumentationRegistry
import com.gigabytedevelopersinc.app.cometOTP.R
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Constants
import com.google.android.material.color.MaterialColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The OK button of the password dialog has to look disabled until the password is long enough.
 *
 * It was disabled from the start, but Widget.Comet.Button.Text gave it a plain primary text colour
 * and the Expressive button family fills a disabled button's container, so an empty dialog showed
 * an OK that looked more ready to press than Cancel. Checked on every app theme, since each one
 * resolves the colours differently.
 */
class PasswordEntryDialogTest {

    private val themes = mapOf(
            "light" to R.style.AppTheme,
            "dark" to R.style.AppTheme_Dark,
            "black" to R.style.AppTheme_Black)

    @Test
    fun okButtonLooksDisabledUntilThePasswordIsLongEnough() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()

        for ((name, theme) in themes) {
            instrumentation.runOnMainSync {
                val context = ContextThemeWrapper(instrumentation.targetContext, theme)
                val dialog = PasswordEntryDialog(context, PasswordEntryDialog.Mode.ENTER,
                        blockAccessibility = false, blockAutofill = false, newCallback = null)
                val ok = dialog.findViewById<Button>(R.id.buttonOk)!!
                val input = dialog.findViewById<EditText>(R.id.passwordInput)!!

                val primary = MaterialColors.getColor(ok, androidx.appcompat.R.attr.colorPrimary)
                val onSurface = MaterialColors.getColor(ok, com.google.android.material.R.attr.colorOnSurface)

                // Empty: disabled, and drawn as disabled.
                assertFalse("$name: OK enabled with no password", ok.isEnabled)
                val disabledText = ok.currentTextColor
                assertNotEquals("$name: disabled OK still uses the primary colour", primary, disabledText)
                assertEquals("$name: disabled OK text is not on-surface",
                        onSurface and 0xFFFFFF, disabledText and 0xFFFFFF)
                // 38% of the on-surface colour's own alpha, give or take rounding.
                val expectedAlpha = Color.alpha(onSurface) * 0.38f
                assertTrue("$name: disabled OK text alpha ${Color.alpha(disabledText)}, expected about $expectedAlpha",
                        Math.abs(Color.alpha(disabledText) - expectedAlpha) <= 2f)
                val container = ok.backgroundTintList?.getColorForState(ok.drawableState, Color.TRANSPARENT)
                        ?: Color.TRANSPARENT
                assertEquals("$name: disabled OK has a filled container", 0, Color.alpha(container))

                // Long enough: enabled, and back to the primary colour.
                input.setText("x".repeat(Constants.AUTH_MIN_PASSWORD_LENGTH))
                assertTrue("$name: OK disabled with a valid password", ok.isEnabled)
                assertEquals("$name: enabled OK is not primary", primary, ok.currentTextColor)
            }
        }
    }
}
