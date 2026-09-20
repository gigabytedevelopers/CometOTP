package com.gigabytedevelopersinc.app.cometOTP.View;

import android.content.Context;
import android.text.TextUtils;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.gigabytedevelopersinc.app.cometOTP.Database.Entry;
import com.gigabytedevelopersinc.app.cometOTP.R;
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Constants;
import com.gigabytedevelopersinc.app.cometOTP.Utilities.EntryThumbnail;
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Settings;
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Tools;
import com.gigabytedevelopersinc.app.cometOTP.View.ItemTouchHelper.ItemTouchHelperViewHolder;
import com.google.android.material.card.MaterialCardView;

import java.util.List;
import java.util.Locale;

/**
 * Binds one {@link Entry} to the redesigned service card: issuer icon, issuer, token, account
 * label, optional tag chip, HOTP counter, per-card countdown ring and the overflow menu.
 */
public class EntryViewHolder extends RecyclerView.ViewHolder implements ItemTouchHelperViewHolder {
    private final Context context;
    private Callback callback;
    private boolean tapToReveal;

    private final MaterialCardView card;
    private final View tagBar;
    private final LinearLayout valueLayout;
    private final LinearLayout coverLayout;
    private final LinearLayout counterLayout;
    private final FrameLayout thumbnailFrame;
    private final ImageView thumbnailImg;
    private final ImageButton menuButton;
    private final TextView value;
    private final TextView valuePrev;
    private final TextView issuer;
    private final TextView label;
    private final TextView counter;
    private final TextView tags;
    private final CountdownRingView countdown;

    private final int defaultValueColor;

    public EntryViewHolder(Context context, final View v, boolean tapToReveal) {
        super(v);
        this.context = context;

        card = v.findViewById(R.id.card_view);
        tagBar = v.findViewById(R.id.tagBar);
        value = v.findViewById(R.id.valueText);
        valuePrev = v.findViewById(R.id.valueTextPrev);
        valueLayout = v.findViewById(R.id.valueLayout);
        thumbnailFrame = v.findViewById(R.id.thumbnailFrame);
        thumbnailImg = v.findViewById(R.id.thumbnailImg);
        coverLayout = v.findViewById(R.id.coverLayout);
        issuer = v.findViewById(R.id.textViewIssuer);
        label = v.findViewById(R.id.textViewLabel);
        tags = v.findViewById(R.id.textViewTags);
        counterLayout = v.findViewById(R.id.counterLayout);
        counter = v.findViewById(R.id.counter);
        countdown = v.findViewById(R.id.cardCountdown);
        menuButton = v.findViewById(R.id.menuButton);

        defaultValueColor = value.getCurrentTextColor();

        menuButton.setOnClickListener(view -> {
            if (callback != null)
                callback.onMenuButtonClicked(itemView, getBindingAdapterPosition());
        });

        counterLayout.setOnClickListener(view -> {
            if (callback != null)
                callback.onCounterClicked(getBindingAdapterPosition());
        });

        counterLayout.setOnLongClickListener(view -> {
            if (callback != null)
                callback.onCounterLongPressed(getBindingAdapterPosition());

            return false;
        });

        card.setOnClickListener(new SimpleDoubleClickListener() {
            @Override
            public void onSingleClick(View v) {
                if (callback != null)
                    callback.onCardSingleClicked(getBindingAdapterPosition(), value.getTag().toString());
            }

            @Override
            public void onDoubleClick(View v) {
                if (callback != null)
                    callback.onCardDoubleClicked(getBindingAdapterPosition(), value.getTag().toString());
            }
        });

        setTapToReveal(tapToReveal);
    }

    public void updateValues(Entry entry) {
        Settings settings = new Settings(context);

        if (entry.getType() == Entry.OTPType.HOTP) {
            counterLayout.setVisibility(View.VISIBLE);
            counter.setText(String.format(Locale.ENGLISH, "%d", entry.getCounter()));
        } else {
            counterLayout.setVisibility(View.GONE);
        }

        final String tokenFormatted = Tools.formatToken(entry.getCurrentOTP(), settings.getTokenSplitGroupSize());

        String issuerText = entry.getIssuer();
        String labelText = entry.getLabel();
        boolean showIssuer = !TextUtils.isEmpty(issuerText) && !settings.isHideIssuerEnabled();

        String contentHint = "";
        if (showIssuer) {
            issuer.setText(issuerText);
            issuer.setVisibility(View.VISIBLE);
            contentHint = issuerText;
        } else {
            issuer.setVisibility(View.GONE);
        }

        if (!TextUtils.isEmpty(labelText)) {
            label.setText(labelText);
            label.setVisibility(View.VISIBLE);
            if (!showIssuer)
                contentHint = labelText;
        } else {
            label.setVisibility(View.GONE);
        }

        menuButton.setContentDescription(context.getString(R.string.button_card_options_format, contentHint));

        value.setText(tokenFormatted);
        // save the unformatted token to the tag of this TextView for copy/paste
        value.setTag(entry.getCurrentOTP());

        if (settings.getShowPrevToken()) {
            String tokenPrev = entry.getPrevOTP();

            if (tokenPrev != null && !tokenPrev.isEmpty()) {
                valuePrev.setVisibility(View.VISIBLE);
                valuePrev.setText(Tools.formatToken(tokenPrev, settings.getTokenSplitGroupSize()));
            } else {
                valuePrev.setVisibility(View.GONE);
            }
        } else {
            valuePrev.setVisibility(View.GONE);
        }

        List<String> entryTags = entry.getTags();
        if (entryTags.isEmpty()) {
            tags.setVisibility(View.GONE);
            tagBar.setVisibility(View.GONE);
        } else {
            tags.setText(TextUtils.join(", ", entryTags));
            tags.setVisibility(View.VISIBLE);
            tagBar.setVisibility(View.VISIBLE);
        }

        thumbnailFrame.setVisibility(settings.getThumbnailVisible() ? View.VISIBLE : View.GONE);
        if (settings.getThumbnailVisible()) {
            int thumbnailSize = settings.getThumbnailSize();
            thumbnailImg.setImageBitmap(EntryThumbnail.getThumbnailGraphic(context, issuerText, labelText, thumbnailSize, entry.getThumbnail()));
        }

        boolean showCountdown = entry.isTimeBased()
                && (entry.hasNonDefaultPeriod() || settings.isShowIndividualTimeoutsEnabled());
        if (showCountdown && (!this.tapToReveal || entry.isVisible())) {
            countdown.setVisibility(View.VISIBLE);
            countdown.setHighlightExpiring(settings.isHighlightTokenOptionEnabled());
            countdown.update(entry.getPeriod());
        } else {
            countdown.stop();
            countdown.setVisibility(showCountdown ? View.INVISIBLE : View.GONE);
        }

        if (this.tapToReveal) {
            if (entry.isVisible()) {
                valueLayout.setVisibility(View.VISIBLE);
                coverLayout.setVisibility(View.GONE);
            } else {
                valueLayout.setVisibility(View.GONE);
                coverLayout.setVisibility(View.VISIBLE);
            }
        }
    }

    public void setLabelSize(int size) {
        // The design uses a fixed type scale; the label size setting scales the token instead so
        // the user preference still has a visible effect.
        value.setTextSize(Math.max(16, size + 4));
    }

    public void setThumbnailSize(int size) {
        thumbnailFrame.getLayoutParams().height = size + thumbnailImg.getPaddingTop() + thumbnailImg.getPaddingBottom();
        thumbnailFrame.getLayoutParams().width = size + thumbnailImg.getPaddingLeft() + thumbnailImg.getPaddingRight();
        thumbnailFrame.requestLayout();
    }

    public void setLabelScroll(Constants.LabelDisplay labelDisplay) {
        switch (labelDisplay) {
            case TRUNCATE:
                label.setEllipsize(TextUtils.TruncateAt.END);
                label.setHorizontallyScrolling(false);
                label.setSelected(false);
                label.setMaxLines(1);
                break;
            case SCROLL:
                label.setEllipsize(TextUtils.TruncateAt.MARQUEE);
                label.setHorizontallyScrolling(true);
                label.setSelected(true);
                label.setMaxLines(1);
                break;
            case MULTILINE:
                label.setEllipsize(null);
                label.setHorizontallyScrolling(false);
                label.setSelected(false);
                label.setMaxLines(10);
                break;
        }
    }

    private void setTapToReveal(boolean enabled) {
        tapToReveal = enabled;

        if (enabled) {
            valueLayout.setVisibility(View.GONE);
            coverLayout.setVisibility(View.VISIBLE);
        } else {
            valueLayout.setVisibility(View.VISIBLE);
            coverLayout.setVisibility(View.GONE);
        }
    }

    @Override
    public void onItemSelected() {
        if (callback != null) {
            callback.onMoveEventStart();
        }
        card.setAlpha(0.5f);
    }

    @Override
    public void onItemClear() {
        if (callback != null) {
            callback.onMoveEventStop();
        }
        card.setAlpha(1f);
    }

    public void setCallback(Callback cb) {
        this.callback = cb;
    }

    public interface Callback {
        void onMoveEventStart();
        void onMoveEventStop();

        void onMenuButtonClicked(View parentView, int position);
        void onCopyButtonClicked(String text, int position);

        void onCardSingleClicked(int position, String text);
        void onCardDoubleClicked(int position, String text);

        void onCounterClicked(int position);
        void onCounterLongPressed(int position);
    }

    /**
     * Updates the color of OTP to red (if expiring) or default color (if new OTP)
     *
     * @param color will define if the color needs to be changed to red or default
     * */
    public void updateColor(int color) {
        int textColor;
        if (color == Entry.COLOR_RED) {
            textColor = Tools.getThemeColor(context, R.attr.colorExpiring);
        } else {
            textColor = defaultValueColor;
        }

        value.setTextColor(textColor);
        valuePrev.setTextColor(textColor);
    }
}
