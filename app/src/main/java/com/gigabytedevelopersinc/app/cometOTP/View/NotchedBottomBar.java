package com.gigabytedevelopersinc.app.cometOTP.View;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.widget.FrameLayout;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.gigabytedevelopersinc.app.cometOTP.R;

/**
 * The docked bottom action bar from the redesign.
 *
 * <p>The bar is flush with the bottom of the window and extends behind the navigation bar, so the
 * OTP list can scroll underneath it. A circular notch is punched out of the top edge for the add
 * button, which is centred on that edge, and the 1dp top hairline follows the notch around.</p>
 */
public class NotchedBottomBar extends FrameLayout {

    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path fillPath = new Path();
    private final Path notchPath = new Path();
    private final Path linePath = new Path();
    private final RectF notchRect = new RectF();

    private float notchRadius;
    private float lineWidth;

    public NotchedBottomBar(@NonNull Context context) {
        this(context, null);
    }

    public NotchedBottomBar(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public NotchedBottomBar(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setWillNotDraw(false);

        float density = getResources().getDisplayMetrics().density;
        int barColor = 0xFFFFFFFF;
        int lineColor = 0x1F000000;
        notchRadius = 38f * density;
        lineWidth = density;

        if(attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.NotchedBottomBar, defStyleAttr, 0);
            barColor = a.getColor(R.styleable.NotchedBottomBar_barColor, barColor);
            lineColor = a.getColor(R.styleable.NotchedBottomBar_barLineColor, lineColor);
            notchRadius = a.getDimension(R.styleable.NotchedBottomBar_barNotchRadius, notchRadius);
            lineWidth = a.getDimension(R.styleable.NotchedBottomBar_barLineWidth, lineWidth);
            a.recycle();
        }

        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setColor(barColor);

        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setColor(lineColor);
        linePaint.setStrokeWidth(lineWidth);
    }

    /** The radius of the circle cut out of the top edge, including the gap around the button. */
    public float getNotchRadius() {
        return notchRadius;
    }

    public void setNotchRadius(float radius) {
        notchRadius = radius;
        invalidate();
    }

    public void setBarColor(@ColorInt int color) {
        fillPaint.setColor(color);
        invalidate();
    }

    public void setBarLineColor(@ColorInt int color) {
        linePaint.setColor(color);
        invalidate();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        if(w <= 0 || h <= 0) return;

        float cx = w / 2f;
        // Inset by half the stroke so the hairline sits fully inside the view.
        float top = lineWidth / 2f;

        fillPath.reset();
        fillPath.addRect(0f, top, w, h, Path.Direction.CW);
        notchPath.reset();
        notchPath.addCircle(cx, top, notchRadius, Path.Direction.CW);
        fillPath.op(notchPath, Path.Op.DIFFERENCE);
        canvas.drawPath(fillPath, fillPaint);

        notchRect.set(cx - notchRadius, top - notchRadius, cx + notchRadius, top + notchRadius);
        linePath.reset();
        linePath.moveTo(0f, top);
        linePath.lineTo(cx - notchRadius, top);
        // Sweep from the left of the notch, down through its lowest point, back up to the right.
        linePath.arcTo(notchRect, 180f, -180f, false);
        linePath.lineTo(w, top);
        canvas.drawPath(linePath, linePaint);

        super.onDraw(canvas);
    }
}
