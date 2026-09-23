@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.View

import android.content.Context
import android.text.TextUtils
import android.util.TypedValue
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.widget.TextViewCompat
import androidx.recyclerview.widget.RecyclerView
import com.gigabytedevelopersinc.app.cometOTP.Database.Entry
import com.gigabytedevelopersinc.app.cometOTP.R
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Constants
import com.gigabytedevelopersinc.app.cometOTP.Utilities.EntryThumbnail
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Settings
import com.gigabytedevelopersinc.app.cometOTP.Utilities.TagStore
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Tools
import com.gigabytedevelopersinc.app.cometOTP.View.ItemTouchHelper.ItemTouchHelperViewHolder
import com.google.android.material.card.MaterialCardView
import java.util.Locale

/**
 * Binds one [Entry] to the redesigned service card: issuer icon, issuer, token, account
 * label, optional tag chip, HOTP counter, per-card countdown ring and the overflow menu.
 */
class EntryViewHolder(private val context: Context, v: View, tapToReveal: Boolean) :
    RecyclerView.ViewHolder(v), ItemTouchHelperViewHolder {
    private var callback: Callback? = null
    private var tapToReveal = false

    private val card: MaterialCardView = v.findViewById(R.id.card_view)
    private val tagBar: View = v.findViewById(R.id.tagBar)
    private val valueLayout: LinearLayout
    private val coverLayout: LinearLayout
    private val counterLayout: LinearLayout
    private val thumbnailFrame: FrameLayout
    private val thumbnailImg: ImageView

    private val copyButton: ImageButton
    private val menuButton: ImageButton
    private val value: TextView = v.findViewById(R.id.valueText)
    private val valuePrev: TextView = v.findViewById(R.id.valueTextPrev)
    private val issuer: TextView
    private val label: TextView
    private val counter: TextView
    private val tags: TextView
    private val countdown: CountdownRingView

    private val defaultValueColor: Int

    init {
        valueLayout = v.findViewById(R.id.valueLayout)
        thumbnailFrame = v.findViewById(R.id.thumbnailFrame)
        thumbnailImg = v.findViewById(R.id.thumbnailImg)
        coverLayout = v.findViewById(R.id.coverLayout)
        issuer = v.findViewById(R.id.textViewIssuer)
        label = v.findViewById(R.id.textViewLabel)
        tags = v.findViewById(R.id.textViewTags)
        counterLayout = v.findViewById(R.id.counterLayout)
        counter = v.findViewById(R.id.counter)
        countdown = v.findViewById(R.id.cardCountdown)
        copyButton = v.findViewById(R.id.copyButton)
        menuButton = v.findViewById(R.id.menuButton)

        defaultValueColor = value.currentTextColor

        menuButton.setOnClickListener {
            callback?.onMenuButtonClicked(itemView, bindingAdapterPosition)
        }

        copyButton.setOnClickListener {
            val cb = callback
            if (cb != null && value.tag != null)
                cb.onCopyButtonClicked(value.tag.toString(), bindingAdapterPosition)
        }

        counterLayout.setOnClickListener {
            callback?.onCounterClicked(bindingAdapterPosition)
        }

        counterLayout.setOnLongClickListener {
            callback?.onCounterLongPressed(bindingAdapterPosition)

            false
        }

        card.setOnClickListener(object : SimpleDoubleClickListener() {
            // value.tag is null while no token has been worked out; the Java version threw an NPE
            // here in that case (getTag().toString()), and !! keeps that.
            override fun onSingleClick(v: View) {
                callback?.onCardSingleClicked(bindingAdapterPosition, value.tag!!.toString())
            }

            override fun onDoubleClick(v: View) {
                callback?.onCardDoubleClicked(bindingAdapterPosition, value.tag!!.toString())
            }
        })

        setTapToReveal(tapToReveal)
    }

    fun updateValues(entry: Entry) {
        val settings = Settings(context)

        if (entry.type == Entry.OTPType.HOTP) {
            counterLayout.visibility = View.VISIBLE
            counter.text = String.format(Locale.ENGLISH, "%d", entry.counter)
        } else {
            counterLayout.visibility = View.GONE
        }

        val tokenFormatted = Tools.formatToken(entry.currentOTP, settings.tokenSplitGroupSize)

        val issuerText = entry.issuer
        val labelText = entry.label
        val showIssuer = !TextUtils.isEmpty(issuerText) && !settings.isHideIssuerEnabled

        var contentHint: String? = ""
        if (showIssuer) {
            issuer.text = issuerText
            issuer.visibility = View.VISIBLE
            contentHint = issuerText
        } else {
            issuer.visibility = View.GONE
        }

        if (!TextUtils.isEmpty(labelText)) {
            label.text = labelText
            label.visibility = View.VISIBLE
            if (!showIssuer)
                contentHint = labelText
        } else {
            label.visibility = View.GONE
        }

        menuButton.contentDescription = context.getString(R.string.button_card_options_format, contentHint)
        copyButton.contentDescription = context.getString(R.string.button_card_copy_format, contentHint)

        // A token should be ready by the time the card is bound. If one is not, the line shows a
        // mask rather than emptying out, so the card keeps its shape instead of collapsing and
        // springing back a moment later.
        val hasToken = !TextUtils.isEmpty(tokenFormatted)
        value.text = if (hasToken) tokenFormatted else context.getString(R.string.bullet_placeholder)
        value.alpha = if (hasToken) 1f else 0.4f
        // save the unformatted token to the tag of this TextView for copy/paste
        value.tag = entry.currentOTP

        if (settings.showPrevToken) {
            val tokenPrev = entry.prevOTP

            if (tokenPrev != null && tokenPrev.isNotEmpty()) {
                valuePrev.visibility = View.VISIBLE
                valuePrev.text = Tools.formatToken(tokenPrev, settings.tokenSplitGroupSize)
            } else {
                valuePrev.visibility = View.GONE
            }
        } else {
            valuePrev.visibility = View.GONE
        }

        val entryTags: List<String> = entry.tags
        if (entryTags.isEmpty()) {
            tags.visibility = View.GONE
            tagBar.visibility = View.GONE
        } else {
            // The leading bar and the chip take the colour of the entry's first tag
            val tagColor = TagStore.colorOf(context, entryTags[0])
            tagBar.setBackgroundColor(tagColor)

            tags.text = TextUtils.join(", ", entryTags)
            tags.visibility = View.VISIBLE
            tagBar.visibility = View.VISIBLE

            var chipBackground = ContextCompat.getDrawable(context, R.drawable.bg_tag_chip)
            if (chipBackground != null) {
                chipBackground = chipBackground.mutate()
                chipBackground.setTint(tagColor)
                tags.background = chipBackground
            }
        }

        thumbnailFrame.visibility = if (settings.thumbnailVisible) View.VISIBLE else View.GONE
        if (settings.thumbnailVisible) {
            val thumbnailSize = settings.thumbnailSize
            thumbnailImg.setImageBitmap(EntryThumbnail.getThumbnailGraphic(context, issuerText, labelText, thumbnailSize, entry.thumbnail))
        }

        val showCountdown = entry.isTimeBased
                && (entry.hasNonDefaultPeriod() || settings.isShowIndividualTimeoutsEnabled)
        if (showCountdown && (!this.tapToReveal || entry.isVisible)) {
            countdown.visibility = View.VISIBLE
            countdown.setHighlightExpiring(settings.isHighlightTokenOptionEnabled)
            countdown.update(entry.period)
        } else {
            countdown.stop()
            countdown.visibility = if (showCountdown) View.INVISIBLE else View.GONE
        }

        if (this.tapToReveal) {
            if (entry.isVisible) {
                valueLayout.visibility = View.VISIBLE
                coverLayout.visibility = View.GONE
            } else {
                valueLayout.visibility = View.GONE
                coverLayout.visibility = View.VISIBLE
            }
        }
    }

    fun setLabelSize(size: Int) {
        // The design uses a fixed type scale; the label size setting scales the token instead so
        // the user preference still has a visible effect. The token auto-sizes to fit its line,
        // so the preference sets the ceiling rather than the size, which setTextSize cannot do
        // on an auto-sizing view.
        // The auto-size ceiling has to stay above the floor, or the configuration is rejected.
        val max = Math.max(MIN_TOKEN_TEXT_SIZE_SP + 2, size + 4)
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                value, MIN_TOKEN_TEXT_SIZE_SP, max, 1, TypedValue.COMPLEX_UNIT_SP)
    }

    fun setThumbnailSize(size: Int) {
        thumbnailFrame.layoutParams.height = size + thumbnailImg.paddingTop + thumbnailImg.paddingBottom
        thumbnailFrame.layoutParams.width = size + thumbnailImg.paddingLeft + thumbnailImg.paddingRight
        thumbnailFrame.requestLayout()
    }

    fun setLabelScroll(labelDisplay: Constants.LabelDisplay) {
        when (labelDisplay) {
            Constants.LabelDisplay.TRUNCATE -> {
                label.ellipsize = TextUtils.TruncateAt.END
                label.setHorizontallyScrolling(false)
                label.isSelected = false
                label.maxLines = 1
            }
            Constants.LabelDisplay.SCROLL -> {
                label.ellipsize = TextUtils.TruncateAt.MARQUEE
                label.setHorizontallyScrolling(true)
                label.isSelected = true
                label.maxLines = 1
            }
            Constants.LabelDisplay.MULTILINE -> {
                label.ellipsize = null
                label.setHorizontallyScrolling(false)
                label.isSelected = false
                label.maxLines = 10
            }
        }
    }

    private fun setTapToReveal(enabled: Boolean) {
        tapToReveal = enabled

        if (enabled) {
            valueLayout.visibility = View.GONE
            coverLayout.visibility = View.VISIBLE
        } else {
            valueLayout.visibility = View.VISIBLE
            coverLayout.visibility = View.GONE
        }
    }

    override fun onItemSelected() {
        callback?.onMoveEventStart()
        card.alpha = 0.5f
    }

    override fun onItemClear() {
        callback?.onMoveEventStop()
        card.alpha = 1f
    }

    fun setCallback(cb: Callback?) {
        this.callback = cb
    }

    interface Callback {
        fun onMoveEventStart()
        fun onMoveEventStop()

        fun onMenuButtonClicked(parentView: View, position: Int)
        fun onCopyButtonClicked(text: String, position: Int)

        fun onCardSingleClicked(position: Int, text: String)
        fun onCardDoubleClicked(position: Int, text: String)

        fun onCounterClicked(position: Int)
        fun onCounterLongPressed(position: Int)
    }

    /**
     * Updates the color of OTP to red (if expiring) or default color (if new OTP)
     *
     * @param color will define if the color needs to be changed to red or default
     * */
    fun updateColor(color: Int) {
        val textColor: Int = if (color == Entry.COLOR_RED) {
            Tools.getThemeColor(context, R.attr.colorExpiring)
        } else {
            defaultValueColor
        }

        value.setTextColor(textColor)
        valuePrev.setTextColor(textColor)
    }

    companion object {
        /** The token never shrinks below this, however narrow the card gets. */
        private const val MIN_TOKEN_TEXT_SIZE_SP = 14
    }
}
