package com.gigabytedevelopersinc.app.cometOTP.View;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.view.animation.LinearInterpolator;

import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;

import com.gigabytedevelopersinc.app.cometOTP.R;
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Tools;

import java.util.Locale;

/**
 * Circular countdown used in the app bar (global TOTP period) and on every time based card.
 * <p>
 * The ring shows the remaining fraction of the period as an arc and the remaining seconds as a
 * label in the centre. The ring turns to the "expiring" colour during the last seconds so that the
 * user knows the token is about to change.
 */
public class CountdownRingView extends View {
    private static final int DEFAULT_EXPIRING_THRESHOLD_SECONDS = 8;
    private static final long ANIMATION_DURATION_MS = 1000;

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arcBounds = new RectF();

    private int ringColor;
    private int expiringColor;
    private int trackColor;
    private int textColor;
    private float strokeWidth;
    private boolean showText = true;
    private boolean highlightExpiring = true;
    private int expiringThreshold = DEFAULT_EXPIRING_THRESHOLD_SECONDS;

    private int period = 30;
    /** Remaining fraction of the period, 1 = full, 0 = expired. */
    private float progress = 1f;
    private int remainingSeconds = period;

    private ObjectAnimator animator;

    public CountdownRingView(Context context) {
        this(context, null);
    }

    public CountdownRingView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CountdownRingView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        ringColor = Tools.getThemeColor(context, androidx.appcompat.R.attr.colorPrimary);
        expiringColor = Tools.getThemeColor(context, R.attr.colorExpiring);
        trackColor = Tools.getThemeColor(context, com.google.android.material.R.attr.colorOutlineVariant);
        textColor = Tools.getThemeColor(context, com.google.android.material.R.attr.colorOnSurface);
        strokeWidth = getResources().getDimension(R.dimen.countdown_stroke);
        float textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 9, getResources().getDisplayMetrics());

        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.CountdownRingView, defStyleAttr, 0);
            ringColor = a.getColor(R.styleable.CountdownRingView_ringColor, ringColor);
            expiringColor = a.getColor(R.styleable.CountdownRingView_ringExpiringColor, expiringColor);
            trackColor = a.getColor(R.styleable.CountdownRingView_ringTrackColor, trackColor);
            textColor = a.getColor(R.styleable.CountdownRingView_ringTextColor, textColor);
            strokeWidth = a.getDimension(R.styleable.CountdownRingView_ringStrokeWidth, strokeWidth);
            textSize = a.getDimension(R.styleable.CountdownRingView_ringTextSize, textSize);
            showText = a.getBoolean(R.styleable.CountdownRingView_ringShowText, showText);
            highlightExpiring = a.getBoolean(R.styleable.CountdownRingView_ringHighlightExpiring, highlightExpiring);
            expiringThreshold = a.getInt(R.styleable.CountdownRingView_ringExpiringThreshold, expiringThreshold);
            a.recycle();
        }

        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeWidth(strokeWidth);
        trackPaint.setColor(trackColor);

        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(strokeWidth);
        ringPaint.setStrokeCap(Paint.Cap.ROUND);
        ringPaint.setColor(ringColor);

        textPaint.setColor(textColor);
        textPaint.setTextSize(textSize);
        textPaint.setTextAlign(Paint.Align.CENTER);
        Typeface typeface = isInEditMode() ? Typeface.DEFAULT_BOLD : ResourcesCompat.getFont(context, R.font.inter_bold);
        textPaint.setTypeface(typeface != null ? typeface : Typeface.DEFAULT_BOLD);
    }

    /** Recomputes the remaining time for the given period and animates towards the next second. */
    public void update(int newPeriod) {
        if (newPeriod <= 0)
            newPeriod = 30;
        period = newPeriod;

        long periodMillis = period * 1000L;
        long elapsedMillis = System.currentTimeMillis() % periodMillis;
        long remainingMillis = periodMillis - elapsedMillis;

        float from = remainingMillis / (float) periodMillis;
        float to = Math.max(0f, (remainingMillis - ANIMATION_DURATION_MS) / (float) periodMillis);

        remainingSeconds = (int) Math.ceil(remainingMillis / 1000d);

        if (animator != null)
            animator.cancel();

        setProgress(from);
        animator = ObjectAnimator.ofFloat(this, "progress", from, to);
        animator.setDuration(ANIMATION_DURATION_MS);
        animator.setInterpolator(new LinearInterpolator());
        animator.start();
    }

    /** Sets the ring without animation, e.g. for a static preview. */
    public void setStatic(int newPeriod, int remaining) {
        period = newPeriod;
        remainingSeconds = remaining;
        if (animator != null)
            animator.cancel();
        setProgress(remaining / (float) newPeriod);
    }

    public void stop() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
    }

    @SuppressWarnings("unused") // used by ObjectAnimator
    public void setProgress(float value) {
        progress = Math.max(0f, Math.min(1f, value));
        invalidate();
    }

    @SuppressWarnings("unused")
    public float getProgress() {
        return progress;
    }

    public void setShowText(boolean show) {
        showText = show;
        invalidate();
    }

    public void setHighlightExpiring(boolean highlight) {
        highlightExpiring = highlight;
        invalidate();
    }

    public void setRingColor(int color) {
        ringColor = color;
        invalidate();
    }

    public boolean isExpiring() {
        return highlightExpiring && remainingSeconds <= expiringThreshold;
    }

    @Override
    protected void onDetachedFromWindow() {
        stop();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float inset = strokeWidth / 2f + 1f;
        float size = Math.min(getWidth() - getPaddingLeft() - getPaddingRight(),
                getHeight() - getPaddingTop() - getPaddingBottom());
        float left = getPaddingLeft() + (getWidth() - getPaddingLeft() - getPaddingRight() - size) / 2f;
        float top = getPaddingTop() + (getHeight() - getPaddingTop() - getPaddingBottom() - size) / 2f;
        arcBounds.set(left + inset, top + inset, left + size - inset, top + size - inset);

        canvas.drawOval(arcBounds, trackPaint);

        ringPaint.setColor(isExpiring() ? expiringColor : ringColor);
        canvas.drawArc(arcBounds, -90f, -360f * progress, false, ringPaint);

        if (showText) {
            textPaint.setColor(isExpiring() ? expiringColor : textColor);
            String label = String.format(Locale.getDefault(), "%ds", remainingSeconds);
            float baseline = arcBounds.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f;
            canvas.drawText(label, arcBounds.centerX(), baseline, textPaint);
        }
    }
}
