package com.pocketledger.v2;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Draggable reorderable list in an AlertDialog.
 * Tap row → onItemTap. Press handle → drag to reorder.
 * Migrated from V1 — logic unchanged, package updated.
 */
final class ReorderableListDialog {

    interface ItemTapCallback { void onTap(String item); }
    interface AddCallback     { void onAdd(); }
    interface OrderCallback   { void onOrderChanged(List<String> newOrder); }

    static AlertDialog show(Context ctx, String title, List<String> items,
                            ItemTapCallback onItemTap, AddCallback onAdd, OrderCallback onOrder) {

        final List<String> live = new ArrayList<>(items);
        RecyclerView rv = new RecyclerView(ctx);
        rv.setLayoutManager(new LinearLayoutManager(ctx));
        rv.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        int maxH = (int)(ctx.getResources().getDisplayMetrics().density * 400);
        rv.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, maxH));
        rv.setBackgroundColor(Design.surface(ctx));

        final ItemTouchHelper[] helperRef = {null};
        RowAdapter adapter = new RowAdapter(ctx, live, onItemTap, () -> helperRef[0], onOrder);
        rv.setAdapter(adapter);

        ItemTouchHelper.SimpleCallback drag = new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override public boolean onMove(RecyclerView rv2, RecyclerView.ViewHolder from, RecyclerView.ViewHolder to) {
                int f = from.getAdapterPosition(); int t = to.getAdapterPosition();
                if (f == RecyclerView.NO_POSITION || t == RecyclerView.NO_POSITION) return false;
                Collections.swap(live, f, t); adapter.notifyItemMoved(f, t);
                onOrder.onOrderChanged(new ArrayList<>(live)); return true;
            }
            @Override public void onSwiped(RecyclerView.ViewHolder vh, int dir) { }
            @Override public boolean isLongPressDragEnabled() { return false; }
        };

        helperRef[0] = new ItemTouchHelper(drag);
        helperRef[0].attachToRecyclerView(rv);

        AlertDialog dialog = new AlertDialog.Builder(ctx)
                .setTitle(title).setView(rv)
                .setPositiveButton("Add", (d, w) -> onAdd.onAdd())
                .setNegativeButton("Done", null).create();
        dialog.show();
        return dialog;
    }

    static final class RowAdapter extends RecyclerView.Adapter<RowAdapter.VH> {
        interface HelperSupplier { ItemTouchHelper get(); }
        private final Context ctx; private final List<String> items;
        private final ItemTapCallback onItemTap; private final HelperSupplier helperSupplier; private final OrderCallback onOrder;
        RowAdapter(Context ctx, List<String> items, ItemTapCallback onItemTap, HelperSupplier hs, OrderCallback onOrder) {
            this.ctx = ctx; this.items = items; this.onItemTap = onItemTap; this.helperSupplier = hs; this.onOrder = onOrder;
        }
        @Override public VH onCreateViewHolder(ViewGroup parent, int viewType) { return new VH(buildRow(ctx)); }
        @Override public void onBindViewHolder(VH vh, int position) {
            String item = items.get(position);
            vh.label.setText(item);
            vh.label.setTextColor(Design.text(ctx));
            vh.handle.setTextColor(Design.muted(ctx));
            vh.itemView.setBackgroundColor(Design.surface(ctx));
            vh.itemView.setOnClickListener(v -> {
                int pos = vh.getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) onItemTap.onTap(items.get(pos));
            });
            vh.handle.setOnTouchListener((v, e) -> {
                if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    ItemTouchHelper helper = helperSupplier.get();
                    if (helper != null) helper.startDrag(vh);
                    return true;
                }
                return false;
            });
        }
        @Override public int getItemCount() { return items.size(); }

        static final class VH extends RecyclerView.ViewHolder {
            final TextView handle; final TextView label;
            VH(LinearLayout row) { super(row); handle = (TextView) row.getChildAt(0); label = (TextView) row.getChildAt(1); }
        }

        private static LinearLayout buildRow(Context ctx) {
            float dp = ctx.getResources().getDisplayMetrics().density;
            int h48 = (int)(dp*48); int h52 = (int)(dp*52); int p16 = (int)(dp*16);
            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL); row.setMinimumHeight(h52); row.setPadding(0,0,p16,0);
            TextView handle = new TextView(ctx); handle.setText("≡"); handle.setTextSize(22); handle.setGravity(Gravity.CENTER); handle.setTypeface(Typeface.DEFAULT, Typeface.NORMAL); handle.setContentDescription("Drag to reorder"); handle.setTextColor(Design.muted(ctx)); row.addView(handle, new LinearLayout.LayoutParams(h48, h52));
            TextView label = new TextView(ctx); label.setTextSize(16); label.setTypeface(Typeface.DEFAULT, Typeface.NORMAL); label.setPadding(p16/2,0,0,0); row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            return row;
        }
    }
}
