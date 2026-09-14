package com.pocketledger.v2;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Custom line/area chart view for daily spending trends.
 *
 * Tap a data point to highlight it and view the day and amount in a small pill
 * at the top of the chart. Tapping another point switches the selection;
 * tapping the same point again (or tapping anywhere else) clears it. A small
 * movement threshold keeps vertical page scrolling working when the touch
 * starts on the chart.
 */
public class DailyTrendView extends View {

    private final List<DataPoint> dataPoints = new ArrayList<>();
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path linePath = new Path();
    private final Path fillPath = new Path();

    private final Paint guidePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint haloPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pillLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pillValuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private double maxValue = 0;
    private String currency = "₹";
    private int selectedIndex = -1;

    private boolean trackingDown;
    private float downX, downY;

    public DailyTrendView(Context context) {
        super(context);

        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(Design.dp(context, 3));
        linePaint.setColor(Design.primary(context));
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setStrokeJoin(Paint.Join.ROUND);

        fillPaint.setStyle(Paint.Style.FILL);
        int primaryColor = Design.primary(context);
        fillPaint.setColor((primaryColor & 0x00FFFFFF) | 0x20000000); // 12% opacity

        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(Design.dp(context, 1));
        gridPaint.setColor(Design.border(context));

        textPaint.setTextSize(Design.dp(context, 11));
        textPaint.setColor(Design.muted(context));

        guidePaint.setStyle(Paint.Style.STROKE);
        guidePaint.setStrokeWidth(Design.dp(context, 1));
        guidePaint.setColor(Design.secondary(context));

        haloPaint.setStyle(Paint.Style.FILL);
        haloPaint.setColor(Design.surface(context));

        pointPaint.setStyle(Paint.Style.FILL);
        pointPaint.setColor(Design.secondary(context));

        pillPaint.setStyle(Paint.Style.FILL);
        pillPaint.setColor(Design.surfaceAlt(context));

        pillLabelPaint.setTextAlign(Paint.Align.CENTER);
        pillLabelPaint.setColor(Design.text(context));
        pillLabelPaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);

        pillValuePaint.setTextAlign(Paint.Align.CENTER);
        pillValuePaint.setColor(Design.primary(context));
        pillValuePaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
    }

    public void setData(List<DayData> data, String currency) {
        this.currency = currency;
        dataPoints.clear();
        selectedIndex = -1;
        maxValue = 0;

        if (data.isEmpty()) {
            invalidate();
            return;
        }

        for (DayData day : data) {
            dataPoints.add(new DataPoint(day.label, day.amount));
            if (day.amount > maxValue) {
                maxValue = day.amount;
            }
        }

        // Add some padding to max value
        maxValue = maxValue * 1.2;
        if (maxValue == 0) maxValue = 100;

        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (dataPoints.isEmpty()) {
            // Empty state
            int cx = getWidth() / 2;
            int cy = getHeight() / 2;
            textPaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("No data for this period", cx, cy, textPaint);
            return;
        }

        int padding = Design.dp(getContext(), 40);
        int chartWidth = getWidth() - padding * 2;
        int chartHeight = getHeight() - padding * 2;
        int chartBottom = getHeight() - padding;
        int chartTop = padding;

        // Draw horizontal grid lines (3 lines)
        gridPaint.setColor(Design.border(getContext()));
        for (int i = 0; i <= 2; i++) {
            float y = chartTop + (chartHeight * i / 2f);
            canvas.drawLine(padding, y, getWidth() - padding, y, gridPaint);
        }

        // Calculate points
        linePath.reset();
        fillPath.reset();

        int pointCount = dataPoints.size();
        float xStep = chartWidth / (float) Math.max(1, pointCount - 1);

        boolean firstPoint = true;
        for (int i = 0; i < pointCount; i++) {
            DataPoint point = dataPoints.get(i);
            float x = padding + (i * xStep);
            float y = chartBottom - (float) (point.value / maxValue * chartHeight);

            if (firstPoint) {
                linePath.moveTo(x, y);
                fillPath.moveTo(x, chartBottom);
                fillPath.lineTo(x, y);
                firstPoint = false;
            } else {
                linePath.lineTo(x, y);
                fillPath.lineTo(x, y);
            }
        }

        // Complete fill path
        if (pointCount > 0) {
            float lastX = padding + ((pointCount - 1) * xStep);
            fillPath.lineTo(lastX, chartBottom);
            fillPath.close();
        }

        // Draw filled area
        canvas.drawPath(fillPath, fillPaint);

        // Draw line
        canvas.drawPath(linePath, linePaint);

        // Draw X-axis labels (show first, middle, last)
        textPaint.setTextAlign(Paint.Align.CENTER);
        if (pointCount > 0) {
            canvas.drawText(dataPoints.get(0).label, padding, getHeight() - Design.dp(getContext(), 6), textPaint);
            if (pointCount > 2) {
                int mid = pointCount / 2;
                float midX = padding + (mid * xStep);
                canvas.drawText(dataPoints.get(mid).label, midX, getHeight() - Design.dp(getContext(), 6), textPaint);
            }
            if (pointCount > 1) {
                float lastX = padding + ((pointCount - 1) * xStep);
                canvas.drawText(dataPoints.get(pointCount - 1).label, lastX, getHeight() - Design.dp(getContext(), 6), textPaint);
            }
        }

        // Draw Y-axis labels (max and min)
        textPaint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(currency + String.format(Locale.US, "%.0f", maxValue), padding - Design.dp(getContext(), 8), chartTop + Design.dp(getContext(), 4), textPaint);
        canvas.drawText(currency + "0", padding - Design.dp(getContext(), 8), chartBottom + Design.dp(getContext(), 4), textPaint);

        // Selected day highlight
        if (selectedIndex >= 0 && selectedIndex < pointCount) {
            float x = padding + (selectedIndex * xStep);
            float y = chartBottom - (float) (dataPoints.get(selectedIndex).value / maxValue * chartHeight);

            // Vertical guide line to the point
            canvas.drawLine(x, chartTop, x, y, guidePaint);

            // Halo (knocks out the line behind the point) + filled point
            canvas.drawCircle(x, y, Design.dp(getContext(), 7), haloPaint);
            canvas.drawCircle(x, y, Design.dp(getContext(), Math.round(4.5f)), pointPaint);

            drawSelectionPill(canvas, padding, chartWidth);
        }
    }

    private void drawSelectionPill(Canvas canvas, int padding, int chartWidth) {
        DataPoint p = dataPoints.get(selectedIndex);
        String day = p.label;
        String amount = currency + formatAmount(p.value);

        pillLabelPaint.setTextSize(Design.dp(getContext(), 12));
        float dayWidth = pillLabelPaint.measureText(day);
        pillValuePaint.setTextSize(Design.dp(getContext(), 13));
        float amountWidth = pillValuePaint.measureText(amount);

        float pillW = Math.max(dayWidth, amountWidth) + Design.dp(getContext(), 22);
        float cx = padding + chartWidth / 2f;
        float left = cx - pillW / 2f;
        float right = cx + pillW / 2f;
        float top = Design.dp(getContext(), 5);
        float bottom = Design.dp(getContext(), 36);

        RectF pill = new RectF(left, top, right, bottom);
        canvas.drawRoundRect(pill, Design.dp(getContext(), 12), Design.dp(getContext(), 12), pillPaint);

        canvas.drawText(day, cx, Design.dp(getContext(), 18), pillLabelPaint);
        canvas.drawText(amount, cx, Design.dp(getContext(), 31), pillValuePaint);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = Design.dp(getContext(), 180);
        setMeasuredDimension(width, height);
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
                    handleTap(event.getX());
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

    private void handleTap(float x) {
        int pointCount = dataPoints.size();
        if (pointCount == 0) {
            selectedIndex = -1;
            invalidate();
            return;
        }
        int padding = Design.dp(getContext(), 40);
        int chartWidth = getWidth() - padding * 2;
        float xStep = chartWidth / (float) Math.max(1, pointCount - 1);

        int best = -1;
        float bestDist = touchSlop() * 2;
        for (int i = 0; i < pointCount; i++) {
            float xi = padding + (i * xStep);
            float dist = Math.abs(xi - x);
            if (dist < bestDist) {
                bestDist = dist;
                best = i;
            }
        }

        selectedIndex = best == -1 ? -1 : (best == selectedIndex ? -1 : best);
        invalidate();
    }

    private float touchSlop() {
        return Design.dp(getContext(), 8);
    }

    private String formatAmount(double v) {
        if (v == Math.floor(v)) return String.format(Locale.US, "%.0f", v);
        return String.format(Locale.US, "%.2f", v);
    }

    public static class DayData {
        public String label;
        public double amount;

        public DayData(String label, double amount) {
            this.label = label;
            this.amount = amount;
        }
    }

    private static class DataPoint {
        String label;
        double value;

        DataPoint(String label, double value) {
            this.label = label;
            this.value = value;
        }
    }
}