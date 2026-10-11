@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.View

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.text.TextUtils
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat
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
import com.google.android.material.checkbox.MaterialCheckBox
import java.util.Locale

/**
 * Binds one [Entry] to the redesigned service card: issuer icon, issuer, token, account
 * label, optional tag chip, HOTP counter and per-card countdown ring. A long press opens the
 * card's options; in manage mode (see [bindSelection]) a tap selects the card instead.
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
    private val selectCheck: MaterialCheckBox = v.findViewById(R.id.selectCheck)
    private val dragHandle: View = v.findViewById(R.id.dragHandle)
    private val value: TextView = v.findViewById(R.id.valueText)
    private val valuePrev: TextView = v.findViewById(R.id.valueTextPrev)
    private val valueNext: TextView = v.findViewById(R.id.valueTextNext)
    private val otherTokensRow: View = v.findViewById(R.id.otherTokensRow)
    private val issuer: TextView
    private val label: TextView
    private val counter: TextView
    private val tags: TextView
    private val countdown: CountdownRingView

    private val defaultValueColor: Int

    /** Manage mode, see [bindSelection]. */
    private var selectionMode = false
    /** The copy of the card heading its options sheet, see [showAsPreview]. */
    private var isPreview = false
    /** What the card is called to screen readers: its issuer, or its label without one. */
    private var accessibleName: String? = ""
    private var tapCopies = false

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

        defaultValueColor = value.currentTextColor
        card.setStrokeColor(ColorStateList.valueOf(Tools.getThemeColor(context, androidx.appcompat.R.attr.colorPrimary)))

        copyButton.setOnClickListener {
            val position = currentPosition() ?: return@setOnClickListener
            val cb = callback
            if (cb != null && value.tag != null)
                cb.onCopyButtonClicked(value.tag.toString(), position)
        }

        counterLayout.setOnClickListener {
            val position = currentPosition() ?: return@setOnClickListener
            callback?.onCounterClicked(position)
        }

        counterLayout.setOnLongClickListener {
            val position = currentPosition() ?: return@setOnLongClickListener false
            callback?.onCounterLongPressed(position)

            false
        }

        // Without a double-tap action a single tap acts at once, rather than after the 300 ms a
        // second tap would have had. With tap-to-copy that wait was the lag of every copy. In
        // manage mode a tap only selects, so it never waits.
        card.setOnClickListener(object : SimpleDoubleClickListener({ !selectionMode && Settings(context).tapDouble != Constants.TapMode.NOTHING }) {
            // value.tag is null while no token has been worked out. There is nothing to copy,
            // reveal or send yet, so the tap is ignored, as the copy button already does.
            override fun onSingleClick(v: View) {
                val token = value.tag ?: return
                val position = currentPosition() ?: return
                callback?.onCardSingleClicked(position, token.toString())
            }

            override fun onDoubleClick(v: View) {
                val token = value.tag ?: return
                val position = currentPosition() ?: return
                callback?.onCardDoubleClicked(position, token.toString())
            }
        })

        card.setOnLongClickListener {
            val position = currentPosition() ?: return@setOnLongClickListener false
            callback?.onCardLongClicked(position)
            true
        }

        dragHandle.setOnTouchListener(DragHandleTouch())

        setTapToReveal(tapToReveal)
    }

    /**
     * Starts dragging the card as soon as its handle is touched. The handle is for dragging only;
     * screen readers move the card with the "Move up" and "Move down" actions instead (see
     * [bindSelection]), so it needs no click.
     */
    @SuppressLint("ClickableViewAccessibility")
    private inner class DragHandleTouch : View.OnTouchListener {
        override fun onTouch(v: View, event: MotionEvent): Boolean {
            if (event.actionMasked == MotionEvent.ACTION_DOWN)
                callback?.onStartDrag(this@EntryViewHolder)
            return false
        }
    }

    /**
     * The card's position in the adapter, or null while it has none. Right after
     * notifyDataSetChanged(), which the token updater calls every second, the position stays
     * unknown (NO_POSITION) until the next layout pass; a tap in that window is ignored rather
     * than looked up at index -1.
     */
    private fun currentPosition(): Int? {
        val position = bindingAdapterPosition
        return if (position == RecyclerView.NO_POSITION) null else position
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

        accessibleName = contentHint
        copyButton.contentDescription = context.getString(R.string.button_card_copy_format, contentHint)

        // A tap on the card copies by default, which leaves the copy button with nothing to add;
        // it stays for anyone who has tapping set to something else. Screen readers announce the
        // card as copying the token (bindSelection).
        tapCopies = settings.tapSingle == Constants.TapMode.COPY || settings.tapSingle == Constants.TapMode.COPY_BACKGROUND
        copyButton.visibility = if (tapCopies || isPreview) View.GONE else View.VISIBLE

        // A token should be ready by the time the card is bound. If one is not, the line shows a
        // mask rather than emptying out, so the card keeps its shape instead of collapsing and
        // springing back a moment later.
        val hasToken = !TextUtils.isEmpty(tokenFormatted)
        value.text = if (hasToken) tokenFormatted else context.getString(R.string.bullet_placeholder)
        value.alpha = if (hasToken) 1f else 0.4f
        // save the unformatted token to the tag of this TextView for copy/paste
        value.tag = entry.currentOTP

        // The previous token, and the next one while the current one is about to expire (the
        // colour turns red then, whether or not the red is shown), share the line under the
        // token, so neither takes room from it. The next token's pill keeps its room for the
        // whole period, so the line, and with it the card, does not appear and go each period
        // when the previous token is not shown. Screen readers say which token is which.
        val tokenPrev = entry.prevOTP
        val showPrev = settings.showPrevToken && !tokenPrev.isNullOrEmpty()
        if (showPrev) {
            val prevFormatted = Tools.formatToken(tokenPrev, settings.tokenSplitGroupSize)
            valuePrev.text = prevFormatted
            valuePrev.contentDescription = context.getString(R.string.label_prev_token_format, prevFormatted)
            valuePrev.visibility = View.VISIBLE
        } else {
            valuePrev.visibility = View.GONE
        }

        val tokenNext = entry.nextOTP
        val showNext = settings.showNextToken && entry.isTimeBased && !tokenNext.isNullOrEmpty()
        if (showNext) {
            val nextFormatted = Tools.formatToken(tokenNext, settings.tokenSplitGroupSize)
            valueNext.text = nextFormatted
            valueNext.contentDescription = context.getString(R.string.label_next_token_format, nextFormatted)
            valueNext.visibility = if (entry.color == Entry.COLOR_RED) View.VISIBLE else View.INVISIBLE
        } else {
            valueNext.visibility = View.GONE
        }
        otherTokensRow.visibility = if (showPrev || showNext) View.VISIBLE else View.GONE

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

    /**
     * Manage mode ([selecting]): a tap selects the card instead of copying, a check shows whether
     * it is [selected], and a handle drags it while the list can be reordered ([canDrag]). The
     * copy button, countdown and counter make way for them. Outside manage mode a long press
     * opens the card's options. Runs after [updateValues], which shows what this hides.
     */
    fun bindSelection(selecting: Boolean, selected: Boolean, canDrag: Boolean) {
        selectionMode = selecting
        selectCheck.isChecked = selected
        selectCheck.visibility = if (selecting) View.VISIBLE else View.GONE
        dragHandle.visibility = if (selecting && canDrag) View.VISIBLE else View.GONE
        card.strokeWidth = if (selecting && selected)
            context.resources.getDimensionPixelSize(R.dimen.card_selected_stroke) else 0
        if (selecting) {
            copyButton.visibility = View.GONE
            counterLayout.visibility = View.GONE
            countdown.stop()
            countdown.visibility = View.GONE
        }

        // Starts from no custom actions, as the mode or the setting may have changed since.
        ViewCompat.setAccessibilityDelegate(card, null)
        if (selecting) {
            ViewCompat.setStateDescription(card,
                context.getString(if (selected) R.string.state_selected else R.string.state_not_selected))
            ViewCompat.replaceAccessibilityAction(card, AccessibilityActionCompat.ACTION_CLICK,
                context.getString(if (selected) R.string.action_deselect else R.string.action_select), null)
            ViewCompat.replaceAccessibilityAction(card, AccessibilityActionCompat.ACTION_LONG_CLICK, null, null)
            if (canDrag) {
                ViewCompat.addAccessibilityAction(card, context.getString(R.string.action_move_up)) { _, _ ->
                    val position = currentPosition()
                    if (position != null) callback?.onMoveRequested(position, position - 1)
                    true
                }
                ViewCompat.addAccessibilityAction(card, context.getString(R.string.action_move_down)) { _, _ ->
                    val position = currentPosition()
                    if (position != null) callback?.onMoveRequested(position, position + 1)
                    true
                }
            }
        } else {
            ViewCompat.setStateDescription(card, null)
            if (tapCopies)
                ViewCompat.replaceAccessibilityAction(card, AccessibilityActionCompat.ACTION_CLICK,
                    copyButton.contentDescription, null)
            ViewCompat.replaceAccessibilityAction(card, AccessibilityActionCompat.ACTION_LONG_CLICK,
                context.getString(R.string.button_card_options_format, accessibleName), null)
        }
    }

    /**
     * Makes this the copy of the card heading its options sheet: it shows the entry and keeps
     * ticking, but touching it does nothing.
     */
    fun showAsPreview() {
        isPreview = true
        callback = null
        card.isClickable = false
        card.isLongClickable = false
        card.isFocusable = false
        counterLayout.isClickable = false
        counterLayout.isLongClickable = false
        copyButton.visibility = View.GONE
        ViewCompat.setAccessibilityDelegate(card, null)
    }

    fun setLabelSize(size: Int) {
        // The design uses a fixed type scale; the label size setting scales the token instead so
        // the user preference still has a visible effect. The token auto-sizes to fit its line,
        // so the preference sets the ceiling rather than the size, which setTextSize cannot do
        // on an auto-sizing view. The token is the card's largest text: 30sp at the default size.
        // The auto-size ceiling has to stay above the floor, or the configuration is rejected.
        val max = Math.max(MIN_TOKEN_TEXT_SIZE_SP + 2, size + TOKEN_SIZE_ABOVE_LABEL_SP)
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                value, MIN_TOKEN_TEXT_SIZE_SP, max, 1, TypedValue.COMPLEX_UNIT_SP)

        // The line grows with the ceiling, or the text would shrink to the line instead: a fixed
        // 40dp line kept Large tokens at the default size, and cramped large system font sizes.
        val lineHeight = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, max * TOKEN_LINE_PER_SP,
                context.resources.displayMetrics).toInt()
        if (value.layoutParams.height != lineHeight) {
            value.layoutParams.height = lineHeight
            value.requestLayout()
        }
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

        fun onCopyButtonClicked(text: String, position: Int)
        fun onCardLongClicked(position: Int)

        /** Manage mode: the card's handle was touched. */
        fun onStartDrag(holder: EntryViewHolder)
        /** Manage mode: a screen reader asked to move the card from one position to another. */
        fun onMoveRequested(from: Int, to: Int)

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
        /** How much larger than the label size setting the token may grow. */
        private const val TOKEN_SIZE_ABOVE_LABEL_SP = 12
        /** The token's line height for each sp of its largest size; 40dp at the default 30sp. */
        private const val TOKEN_LINE_PER_SP = 4f / 3
    }
}
