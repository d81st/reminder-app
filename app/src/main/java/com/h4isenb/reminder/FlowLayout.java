package com.h4isenb.reminder;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

/** Простой контейнер, который переносит дочерние элементы на новую строку (для «чипов» времени). */
class FlowLayout extends ViewGroup {
    private final int hGap;
    private final int vGap;

    FlowLayout(Context c, int hGap, int vGap) {
        super(c);
        this.hGap = hGap;
        this.vGap = vGap;
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int maxW = MeasureSpec.getSize(widthSpec) - getPaddingLeft() - getPaddingRight();
        int x = 0, y = 0, rowH = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View ch = getChildAt(i);
            if (ch.getVisibility() == GONE) continue;
            ch.measure(MeasureSpec.makeMeasureSpec(maxW, MeasureSpec.AT_MOST),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            int cw = ch.getMeasuredWidth();
            int chh = ch.getMeasuredHeight();
            if (x > 0 && x + cw > maxW) {
                x = 0;
                y += rowH + vGap;
                rowH = 0;
            }
            x += cw + hGap;
            rowH = Math.max(rowH, chh);
        }
        int h = y + rowH + getPaddingTop() + getPaddingBottom();
        setMeasuredDimension(MeasureSpec.getSize(widthSpec), resolveSize(h, heightSpec));
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int pl = getPaddingLeft();
        int maxW = r - l - pl - getPaddingRight();
        int x = 0, y = getPaddingTop(), rowH = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View ch = getChildAt(i);
            if (ch.getVisibility() == GONE) continue;
            int cw = ch.getMeasuredWidth();
            int chh = ch.getMeasuredHeight();
            if (x > 0 && x + cw > maxW) {
                x = 0;
                y += rowH + vGap;
                rowH = 0;
            }
            ch.layout(pl + x, y, pl + x + cw, y + chh);
            x += cw + hGap;
            rowH = Math.max(rowH, chh);
        }
    }
}
