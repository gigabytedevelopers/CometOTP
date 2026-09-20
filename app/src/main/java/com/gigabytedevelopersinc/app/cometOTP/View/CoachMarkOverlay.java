package com.gigabytedevelopersinc.app.cometOTP.View;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.gigabytedevelopersinc.app.cometOTP.R;
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Tools;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

/**
 * The onboarding coach marks: a dimmed overlay that spotlights one control at a time and explains
 * it with a tooltip. Added on top of the activity's content view and removed when the user
 * finishes the tour.
 */
public class CoachMarkOverlay extends FrameLayout {

    /** One step of the tour: the control to spotlight and the copy that explains it. */
    public static class Step {
        final View target;
        final String title;
        final String body;

        public Step(@NonNull View target, @NonNull String title, @NonNull String body) {
            this.target = target;
            this.title = title;
            this.body = body;
        }
    }

    private static final int SCRIM_COLOR = 0xB3000000;
    private static final float HOLE_PADDING_DP = 8f;

    private final Paint scrimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint holePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF hole = new RectF();
    private final int[] location = new int[2];

    private final List<Step> steps = new ArrayList<>();
    private int current = 0;

    private View tooltip;
    private TextView title;
    private TextView body;
    private LinearLayout dots;
    private MaterialButton next;
    private ImageView caretTop;
    private ImageView caretBottom;

    @Nullable
    private Runnable onFinished;

    public CoachMarkOverlay(Context context) {
        super(context);
        init();
    }

    private void init() {
        setWillNotDraw(false);
        // The hole is punched out of the scrim, which needs a software layer
        setLayerType(LAYER_TYPE_SOFTWARE, null);
        setClickable(true);
        setFocusable(true);

        scrimPaint.setColor(SCRIM_COLOR);
        holePaint.setColor(Color.TRANSPARENT);
        holePaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));

        LayoutInflater.from(getContext()).inflate(R.layout.view_coach_mark, this, true);
        tooltip = findViewById(R.id.coach_container);
        title = findViewById(R.id.coach_title);
        body = findViewById(R.id.coach_body);
        dots = findViewById(R.id.coach_dots);
        next = findViewById(R.id.coach_next);
        caretTop = findViewById(R.id.coach_caret_top);
        caretBottom = findViewById(R.id.coach_caret_bottom);

        next.setOnClickListener(v -> advance());
    }

    /** Starts the tour inside {@code root}; the overlay removes itself when it finishes. */
    public static CoachMarkOverlay show(@NonNull ViewGroup root, @NonNull List<Step> steps,
                                        @Nullable Runnable onFinished) {
        CoachMarkOverlay overlay = new CoachMarkOverlay(root.getContext());
        overlay.steps.addAll(steps);
        overlay.onFinished = onFinished;
        overlay.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        root.addView(overlay);
        overlay.buildDots();
        overlay.bindStep();
        return overlay;
    }

    private void buildDots() {
        dots.removeAllViews();
        int size = dp(6);
        int gap = dp(3);
        for (int i = 0; i < steps.size(); i++) {
            View dot = new View(getContext());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.setMarginEnd(gap);
            dot.setLayoutParams(lp);
            dot.setBackgroundResource(R.drawable.bg_coach_dot);
            dots.addView(dot);
        }
    }

    private void updateDots() {
        for (int i = 0; i < dots.getChildCount(); i++) {
            View dot = dots.getChildAt(i);
            boolean active = i == current;
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) dot.getLayoutParams();
            lp.width = active ? dp(16) : dp(6);
            dot.setLayoutParams(lp);
            dot.setBackgroundResource(active ? R.drawable.bg_coach_dot_active : R.drawable.bg_coach_dot);
        }
    }

    private void bindStep() {
        Step step = steps.get(current);
        title.setText(step.title);
        body.setText(step.body);
        next.setText(current == steps.size() - 1 ? R.string.coach_finish : R.string.coach_next);
        updateDots();

        // The target may not be laid out yet on the first pass
        post(this::positionTooltip);
        invalidate();
    }

    private void advance() {
        if (current < steps.size() - 1) {
            current++;
            bindStep();
        } else {
            finish();
        }
    }

    private void finish() {
        ViewGroup parent = (ViewGroup) getParent();
        if (parent != null)
            parent.removeView(this);
        if (onFinished != null)
            onFinished.run();
    }

    private void computeHole() {
        Step step = steps.get(current);
        View target = step.target;

        target.getLocationInWindow(location);
        int[] own = new int[2];
        getLocationInWindow(own);

        float padding = dp(HOLE_PADDING_DP);
        float left = location[0] - own[0] - padding;
        float top = location[1] - own[1] - padding;
        hole.set(left, top, left + target.getWidth() + padding * 2, top + target.getHeight() + padding * 2);
    }

    private void positionTooltip() {
        computeHole();

        int gap = dp(10);
        int margin = dp(16);

        tooltip.measure(MeasureSpec.makeMeasureSpec(getWidth(), MeasureSpec.AT_MOST),
                MeasureSpec.makeMeasureSpec(getHeight(), MeasureSpec.AT_MOST));
        int tooltipWidth = tooltip.getMeasuredWidth();
        int tooltipHeight = tooltip.getMeasuredHeight();

        // Prefer placing the tooltip above the spotlight; fall back to below when there is no room
        boolean above = hole.top - tooltipHeight - gap > margin;
        caretTop.setVisibility(above ? View.GONE : View.VISIBLE);
        caretBottom.setVisibility(above ? View.VISIBLE : View.GONE);

        int top = above
                ? (int) (hole.top - tooltipHeight - gap)
                : (int) (hole.bottom + gap);
        top = Math.max(margin, Math.min(top, getHeight() - tooltipHeight - margin));

        int desiredLeft = (int) (hole.centerX() - tooltipWidth / 2f);
        int left = Math.max(margin, Math.min(desiredLeft, getWidth() - tooltipWidth - margin));

        LayoutParams lp = (LayoutParams) tooltip.getLayoutParams();
        lp.gravity = Gravity.START | Gravity.TOP;
        lp.leftMargin = left;
        lp.topMargin = top;
        tooltip.setLayoutParams(lp);

        // Point the caret at the centre of the spotlight
        ImageView caret = above ? caretBottom : caretTop;
        LinearLayout.LayoutParams caretParams = (LinearLayout.LayoutParams) caret.getLayoutParams();
        int caretLeft = (int) (hole.centerX() - left - caret.getLayoutParams().width / 2f);
        caretParams.setMarginStart(Math.max(dp(12), Math.min(caretLeft, tooltipWidth - dp(30))));
        caret.setLayoutParams(caretParams);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        post(this::positionTooltip);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        computeHole();
        canvas.drawRect(0, 0, getWidth(), getHeight(), scrimPaint);

        float radius = Math.max(hole.width(), hole.height()) / 2f;
        canvas.drawRoundRect(hole, radius, radius, holePaint);
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @SuppressWarnings("unused")
    private int themeColor(int attr) {
        return Tools.getThemeColor(getContext(), attr);
    }
}
