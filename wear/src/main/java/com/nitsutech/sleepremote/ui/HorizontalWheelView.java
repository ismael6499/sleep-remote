package com.nitsutech.sleepremote.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.nitsutech.sleepremote.R;

import java.util.Locale;
import java.util.function.IntConsumer;

/**
 * Horizontal wrap-around number wheel for Wear OS so horizontal slides adjust time
 * while vertical drags anywhere over the control still scroll the parent page cleanly.
 */
public class HorizontalWheelView extends View {

    private int minValue = 0;
    private int maxValue = 23;
    private int currentValue = 0;

    private float dragOffsetPx = 0f;
    private float downX = 0f;
    private float downY = 0f;
    private float lastX = 0f;
    private boolean decidedHorizontal = false;
    private boolean decidedVertical = false;
    private final int touchSlop;

    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint centerPillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint centerBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint centerTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sideTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint chevronPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF outerRect = new RectF();
    private final RectF centerRect = new RectF();

    private IntConsumer onValueChangedListener;

    public HorizontalWheelView(Context context) {
        this(context, null);
    }

    public HorizontalWheelView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public HorizontalWheelView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        float density = context.getResources().getDisplayMetrics().density;
        float scaledDensity = context.getResources().getDisplayMetrics().scaledDensity;

        bgPaint.setColor(ContextCompat.getColor(context, R.color.wear_card));
        bgPaint.setStyle(Paint.Style.FILL);

        borderPaint.setColor(0xFF243152);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(1f * density);

        centerPillPaint.setColor(ContextCompat.getColor(context, R.color.wear_card_active));
        centerPillPaint.setStyle(Paint.Style.FILL);

        centerBorderPaint.setColor(ContextCompat.getColor(context, R.color.wear_teal));
        centerBorderPaint.setStyle(Paint.Style.STROKE);
        centerBorderPaint.setStrokeWidth(1.5f * density);

        centerTextPaint.setColor(ContextCompat.getColor(context, R.color.wear_text));
        centerTextPaint.setTextSize(21f * scaledDensity);
        centerTextPaint.setTextAlign(Paint.Align.CENTER);
        centerTextPaint.setFakeBoldText(true);

        sideTextPaint.setColor(ContextCompat.getColor(context, R.color.wear_text_muted));
        sideTextPaint.setTextSize(14f * scaledDensity);
        sideTextPaint.setTextAlign(Paint.Align.CENTER);

        chevronPaint.setColor(ContextCompat.getColor(context, R.color.wear_teal));
        chevronPaint.setTextSize(14f * scaledDensity);
        chevronPaint.setTextAlign(Paint.Align.CENTER);
        chevronPaint.setFakeBoldText(true);
    }

    public void setRange(int min, int max) {
        this.minValue = min;
        this.maxValue = Math.max(min, max);
        this.currentValue = wrap(this.currentValue);
        invalidate();
    }

    public void setValue(int value) {
        int wrapped = wrap(value);
        if (this.currentValue != wrapped) {
            this.currentValue = wrapped;
            invalidate();
        }
    }

    public int getValue() {
        return currentValue;
    }

    public void setOnValueChangedListener(IntConsumer listener) {
        this.onValueChangedListener = listener;
    }

    private int wrap(int val) {
        int span = maxValue - minValue + 1;
        return minValue + (((val - minValue) % span + span) % span);
    }

    private void stepValue(int delta) {
        currentValue = wrap(currentValue + delta);
        if (onValueChangedListener != null) {
            onValueChangedListener.accept(currentValue);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) return;

        float density = getResources().getDisplayMetrics().density;
        float pad = 1f * density;
        outerRect.set(pad, pad, w - pad, h - pad);
        float radius = h / 2f;
        canvas.drawRoundRect(outerRect, radius, radius, bgPaint);
        canvas.drawRoundRect(outerRect, radius, radius, borderPaint);

        float slotW = w / 3.4f;
        float cx = w / 2f;
        float cy = h / 2f;

        // Center focus pill
        float pillHalfW = Math.max(24f * density, slotW * 0.48f);
        centerRect.set(cx - pillHalfW, 3f * density, cx + pillHalfW, h - 3f * density);
        canvas.drawRoundRect(centerRect, (h - 6f * density) / 2f, (h - 6f * density) / 2f, centerPillPaint);
        canvas.drawRoundRect(centerRect, (h - 6f * density) / 2f, (h - 6f * density) / 2f, centerBorderPaint);

        canvas.save();
        canvas.clipRect(16f * density, 0f, w - 16f * density, h);

        for (int offset = -2; offset <= 2; offset++) {
            float itemX = cx + offset * slotW + dragOffsetPx;
            if (itemX < -slotW || itemX > w + slotW) continue;

            int val = wrap(currentValue + offset);
            String label = String.format(Locale.US, "%02d", val);

            float distNorm = Math.min(1f, Math.abs(itemX - cx) / slotW);
            Paint paint = distNorm < 0.35f ? centerTextPaint : sideTextPaint;
            int alpha = distNorm < 0.35f ? 255 : Math.max(60, Math.round(190 * (1f - distNorm * 0.45f)));
            paint.setAlpha(alpha);

            Paint.FontMetrics fm = paint.getFontMetrics();
            float textY = cy - (fm.ascent + fm.descent) / 2f;
            canvas.drawText(label, itemX, textY, paint);
        }
        canvas.restore();

        Paint.FontMetrics cfm = chevronPaint.getFontMetrics();
        float chevY = cy - (cfm.ascent + cfm.descent) / 2f;
        canvas.drawText("<", 10f * density, chevY, chevronPaint);
        canvas.drawText(">", w - 10f * density, chevY, chevronPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float slotW = Math.max(40f, getWidth() / 3.4f);

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getX();
                downY = event.getY();
                lastX = downX;
                dragOffsetPx = 0f;
                decidedHorizontal = false;
                decidedVertical = false;
                return true;

            case MotionEvent.ACTION_MOVE:
                if (!decidedHorizontal && !decidedVertical) {
                    float totalDx = Math.abs(event.getX() - downX);
                    float totalDy = Math.abs(event.getY() - downY);
                    if (totalDx > touchSlop && totalDx > totalDy) {
                        decidedHorizontal = true;
                        if (getParent() != null) {
                            getParent().requestDisallowInterceptTouchEvent(true);
                        }
                    } else if (totalDy > touchSlop) {
                        decidedVertical = true;
                        return false;
                    }
                }
                if (decidedHorizontal) {
                    float stepDx = event.getX() - lastX;
                    lastX = event.getX();
                    dragOffsetPx += stepDx;
                    while (dragOffsetPx <= -slotW * 0.65f) {
                        dragOffsetPx += slotW * 0.65f;
                        stepValue(1);
                    }
                    while (dragOffsetPx >= slotW * 0.65f) {
                        dragOffsetPx -= slotW * 0.65f;
                        stepValue(-1);
                    }
                    invalidate();
                    return true;
                }
                return false;

            case MotionEvent.ACTION_UP:
                if (!decidedHorizontal && !decidedVertical) {
                    float tapX = event.getX();
                    if (tapX < getWidth() * 0.34f) {
                        stepValue(-1);
                    } else if (tapX > getWidth() * 0.66f) {
                        stepValue(1);
                    }
                    performClick();
                }
                dragOffsetPx = 0f;
                decidedHorizontal = false;
                decidedVertical = false;
                invalidate();
                return true;

            case MotionEvent.ACTION_CANCEL:
                dragOffsetPx = 0f;
                decidedHorizontal = false;
                decidedVertical = false;
                invalidate();
                return false;

            default:
                return super.onTouchEvent(event);
        }
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }
}
