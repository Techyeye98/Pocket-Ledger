package com.pocketledger.v2;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Scrubbable spending trend; dragging reads values without changing the underlying data. */
public final class DailyTrendView extends View {
    public static class DayData {
        String date;
        double amount;
        public DayData(String date, double amount) { this.date = date; this.amount = amount; }
    }

    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path strokePath = new Path();
    private final Path fillPath = new Path();
    private List<DayData> data = new ArrayList<>();
    private String currency = "";
    private int selected = -1;

    public DailyTrendView(Context context) {
        super(context);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setStrokeJoin(Paint.Join.ROUND);
        line.setStrokeWidth(Design.dp(context, 3));
        text.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        text.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 12,
                getResources().getDisplayMetrics()));
    }

    public void setData(List<DayData> values, String currency) {
        data = new ArrayList<>(values);
        this.currency = currency;
        selected = data.isEmpty() ? -1 : data.size() - 1;
        updateDescription();
        invalidate();
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), Design.dp(getContext(), 206));
    }

    @Override protected void onDraw(Canvas canvas) {
        if (data.isEmpty()) return;
        float left = Design.dp(getContext(), 8);
        float right = getWidth() - Design.dp(getContext(), 8);
        float top = Design.dp(getContext(), 32);
        float bottom = getHeight() - Design.dp(getContext(), 30);
        double max = 0;
        for (DayData day : data) max = Math.max(max, day.amount);
        if (max <= 0) max = 1;

        Paint guide = new Paint(Paint.ANTI_ALIAS_FLAG);
        guide.setColor(Design.border(getContext()));
        guide.setStrokeWidth(Design.dp(getContext(), 1));
        for (int i = 0; i < 3; i++) {
            float y = top + (bottom - top) * i / 2f;
            canvas.drawLine(left, y, right, y, guide);
        }

        strokePath.reset();
        fillPath.reset();
        for (int i = 0; i < data.size(); i++) {
            float x = xFor(i, left, right);
            float y = bottom - (float) (data.get(i).amount / max) * (bottom - top);
            if (i == 0) { strokePath.moveTo(x, y); fillPath.moveTo(x, y); }
            else { strokePath.lineTo(x, y); fillPath.lineTo(x, y); }
        }
        fillPath.lineTo(right, bottom);
        fillPath.lineTo(left, bottom);
        fillPath.close();
        int accent = Design.primary(getContext());
        fill.setShader(new LinearGradient(0, top, 0, bottom,
                Design.tint(accent, 0.22f), Design.tint(accent, 0.015f), Shader.TileMode.CLAMP));
        canvas.drawPath(fillPath, fill);
        fill.setShader(null);
        line.setColor(accent);
        canvas.drawPath(strokePath, line);

        int active = Math.max(0, selected);
        float markerX = xFor(active, left, right);
        float markerY = bottom - (float) (data.get(active).amount / max) * (bottom - top);
        guide.setColor(Design.tint(accent, 0.5f));
        canvas.drawLine(markerX, top, markerX, bottom, guide);
        fill.setColor(Design.surface(getContext()));
        canvas.drawCircle(markerX, markerY, Design.dp(getContext(), 7), fill);
        fill.setColor(accent);
        canvas.drawCircle(markerX, markerY, Design.dp(getContext(), 4), fill);

        text.setColor(Design.text(getContext()));
        text.setTextAlign(markerX > (left + right) / 2f ? Paint.Align.RIGHT : Paint.Align.LEFT);
        float labelX = markerX > (left + right) / 2f ? right : left;
        canvas.drawText(currency + String.format(Locale.US, "%.0f", data.get(active).amount),
                labelX, Design.dp(getContext(), 19), text);
        text.setColor(Design.textSoft(getContext()));
        text.setTextAlign(Paint.Align.LEFT);
        canvas.drawText(data.get(0).date, left, getHeight() - Design.dp(getContext(), 6), text);
        text.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(data.get(data.size() - 1).date, right, getHeight() - Design.dp(getContext(), 6), text);
    }

    private float xFor(int index, float left, float right) {
        return data.size() == 1 ? (left + right) / 2f : left + (right - left) * index / (data.size() - 1f);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (data.isEmpty()) return false;
        if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_MOVE) {
            float fraction = Math.max(0, Math.min(1, event.getX() / Math.max(1, getWidth())));
            int next = Math.round(fraction * (data.size() - 1));
            if (next != selected) {
                selected = next;
                updateDescription();
                invalidate();
            }
            return true;
        }
        if (event.getAction() == MotionEvent.ACTION_UP) {
            super.performClick();
            return true;
        }
        return true;
    }

    @Override public boolean performClick() {
        super.performClick();
        if (!data.isEmpty()) {
            selected = (selected + 1) % data.size();
            updateDescription();
            invalidate();
        }
        return true;
    }

    private void updateDescription() {
        if (selected < 0) setContentDescription("Daily spending graph, no data");
        else setContentDescription("Daily spending graph. " + data.get(selected).date + ", " + currency
                + String.format(Locale.US, "%.2f", data.get(selected).amount)
                + ". Drag to read another day, or activate for the next day.");
    }
}
