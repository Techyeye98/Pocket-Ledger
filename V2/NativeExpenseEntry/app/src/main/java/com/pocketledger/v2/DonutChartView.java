package com.pocketledger.v2;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Custom donut chart view for spending distribution.
 *
 * Tap a segment to highlight it and see the category name, amount and share of
 * the total in the center. Tapping another segment switches the selection;
 * tapping the same segment again (or tapping anywhere outside the ring) clears
 * it and restores the center total. A small movement threshold keeps vertical
 * page scrolling working when the touch starts on the chart.
 */
public class DonutChartView extends View {

    private final List<Segment> segments = new ArrayList<>();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bounds = new RectF();

    private double total = 0;
    private String currency = "₹";
    private String centerText = "";
    private int selectedIndex = -1;

    private boolean trackingDown;
    private float downX, downY;

    public DonutChartView(Context context) {
        super(context);
        textPaint.setTextAlign(Paint.Align.CENTER);
    }

    public void setData(List<CategoryData> data, double total, String currency) {
        this.total = total;
        this.currency = currency;
        this.selectedIndex = -1;
        segments.clear();

        if (data.isEmpty() || total <= 0) {
            centerText = currency + "0.00";
            invalidate();
            return;
        }

        // Generate colors for categories
        int[] colors = {
            Design.primary(getContext()),
            Design.secondary(getContext()),
            0xFF4CAF50, // Green
            0xFFFF9800, // Orange
            0xFFE91E63, // Pink
            0xFF9C27B0, // Purple
            0xFF00BCD4, // Cyan
            0xFFFFEB3B, // Yellow
            0xFF795548, // Brown
            0xFF607D8B, // Blue Grey
            0xFFFF5722, // Deep Orange
            0xFF3F51B5  // Indigo
        };

        float startAngle = -90f; // Start from top
        for (int i = 0; i < data.size(); i++) {
            CategoryData cat = data.get(i);
            float sweepAngle = (float) (cat.amount / total * 360f);
            int color = colors[i % colors.length];
            segments.add(new Segment(cat.name, cat.amount, startAngle, sweepAngle, color));
            startAngle += sweepAngle;
        }

        centerText = currency + String.format(Locale.US, "%.2f", total);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int strokeWidth = Design.dp(getContext(), 40);

        if (segments.isEmpty()) {
            // Empty state - draw a grey circle
            int cx = getWidth() / 2;
            int cy = getHeight() / 2;
            int radius = Math.min(cx, cy) - Design.dp(getContext(), 20);
            int innerRadius = (int) (radius * 0.6f);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(radius - innerRadius);
            paint.setColor(Design.surfaceAlt(getContext()));
            canvas.drawCircle(cx, cy, (radius + innerRadius) / 2f, paint);

            textPaint.setColor(Design.muted(getContext()));
            textPaint.setTextSize(Design.dp(getContext(), 24));
            textPaint.setFakeBoldText(true);
            canvas.drawText(centerText, cx, cy + Design.dp(getContext(), 8), textPaint);
            return;
        }

        // Calculate bounds
        int padding = Design.dp(getContext(), 20);
        int size = Math.min(getWidth(), getHeight()) - padding * 2;
        int left = (getWidth() - size) / 2;
        int top = (getHeight() - size) / 2;
        bounds.set(left, top, left + size, top + size);

        // Draw donut segments
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(strokeWidth);
        paint.setStrokeCap(Paint.Cap.ROUND);

        for (Segment segment : segments) {
            paint.setColor(segment.color);
            canvas.drawArc(bounds, segment.startAngle, segment.sweepAngle, false, paint);
        }

        // Highlight the selected segment by letting it bulge slightly outward
        if (selectedIndex >= 0 && selectedIndex < segments.size()) {
            Segment sel = segments.get(selectedIndex);
            paint.setStrokeWidth(strokeWidth + Design.dp(getContext(), 6));
            paint.setColor(sel.color);
            canvas.drawArc(bounds, sel.startAngle, sel.sweepAngle, false, paint);
        }

        int cx = getWidth() / 2;
        int cy = getHeight() / 2;
        if (selectedIndex >= 0 && selectedIndex < segments.size()) {
            drawSelectionInfo(canvas, cx, cy);
        } else {
            // Center total
            textPaint.setColor(Design.text(getContext()));
            textPaint.setTextSize(Design.dp(getContext(), 28));
            textPaint.setFakeBoldText(true);
            canvas.drawText(centerText, cx, cy + Design.dp(getContext(), 10), textPaint);
        }
    }

    private void drawSelectionInfo(Canvas canvas, int cx, int cy) {
        Segment sel = segments.get(selectedIndex);
        String share = total > 0
                ? String.format(Locale.US, "%.0f%%", sel.amount / total * 100)
                : "0%";

        textPaint.setFakeBoldText(true);
        textPaint.setTextSize(Design.dp(getContext(), 13));
        textPaint.setColor(Design.primary(getContext()));
        canvas.drawText(sel.name, cx, cy - Design.dp(getContext(), 18), textPaint);

        textPaint.setTextSize(Design.dp(getContext(), 20));
        textPaint.setColor(Design.text(getContext()));
        canvas.drawText(currency + String.format(Locale.US, "%.2f", sel.amount),
                cx, cy + Design.dp(getContext(), 3), textPaint);

        textPaint.setFakeBoldText(false);
        textPaint.setTextSize(Design.dp(getContext(), 11));
        textPaint.setColor(Design.muted(getContext()));
        canvas.drawText(share + " of total", cx, cy + Design.dp(getContext(), 19), textPaint);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int size = Design.dp(getContext(), 220);
        setMeasuredDimension(size, size);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                // Consume so we receive the matching UP; the ScrollView still
                // intercepts and scrolls if the gesture becomes a real drag.
                trackingDown = true;
                downX = event.getX();
                downY = event.getY();
                break;
            case MotionEvent.ACTION_MOVE:
                // A move beyond slop is a drag (scroll), not a tap.
                if (trackingDown
                        && (Math.abs(event.getX() - downX) > touchSlop()
                         || Math.abs(event.getY() - downY) > touchSlop())) {
                    trackingDown = false;
                }
                break;
            case MotionEvent.ACTION_UP:
                if (trackingDown) {
                    if (segments.isEmpty()) {
                        selectedIndex = -1;
                        invalidate();
                    } else {
                        int idx = findSegment(event.getX(), event.getY());
                        selectedIndex = idx == -1 ? -1 : (idx == selectedIndex ? -1 : idx);
                        invalidate();
                    }
                    performClick();
                }
                trackingDown = false;
                break;
            case MotionEvent.ACTION_CANCEL:
                trackingDown = false;
                break;
        }
        return true;
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    /** Returns the segment at (x, y), or -1 when outside the donut ring. */
    private int findSegment(float x, float y) {
        int padding = Design.dp(getContext(), 20);
        int size = Math.min(getWidth(), getHeight()) - padding * 2;
        int strokeWidth = Design.dp(getContext(), 40);
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float dx = x - cx;
        float dy = y - cy;
        float dist = (float) Math.hypot(dx, dy);
        float outerR = size / 2f + strokeWidth / 2f;
        float innerR = size / 2f - strokeWidth / 2f;

        if (dist < innerR || dist > outerR) return -1;

        float angle = (float) Math.toDegrees(Math.atan2(dy, dx));
        if (angle < 0) angle += 360f;

        for (int i = 0; i < segments.size(); i++) {
            Segment s = segments.get(i);
            float start = (s.startAngle % 360f + 360f) % 360f;
            if (angle >= start && angle < start + s.sweepAngle) return i;
            if (angle + 360f < start + s.sweepAngle) return i; // segment wraps 0°
        }
        return -1;
    }

    private float touchSlop() {
        return Design.dp(getContext(), 8);
    }

    public static class CategoryData {
        public String name;
        public double amount;

        public CategoryData(String name, double amount) {
            this.name = name;
            this.amount = amount;
        }
    }

    private static class Segment {
        String name;
        double amount;
        float startAngle;
        float sweepAngle;
        int color;

        Segment(String name, double amount, float startAngle, float sweepAngle, int color) {
            this.name = name;
            this.amount = amount;
            this.startAngle = startAngle;
            this.sweepAngle = sweepAngle;
            this.color = color;
        }
    }
}