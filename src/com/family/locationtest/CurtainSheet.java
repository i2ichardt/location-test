package com.family.locationtest;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.LinearLayout;
import android.widget.ScrollView;

public class CurtainSheet extends LinearLayout {
    public interface Pull {
        void onDown(float rawY);
        void onMove(float rawY);
        void onUp(float rawY, boolean tap);
    }

    private Pull pull;
    private float downX;
    private float downY;
    private boolean tracking;
    private boolean dragging;
    private final int slop;

    public CurtainSheet(Context context, AttributeSet attrs) {
        super(context, attrs);
        slop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    public void setPull(Pull pull) {
        this.pull = pull;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        if (pull == null) return false;
        int action = event.getAction();
        if (action == MotionEvent.ACTION_DOWN) {
            downX = event.getRawX();
            downY = event.getRawY();
            tracking = true;
            dragging = false;
            pull.onDown(downY);
            return false;
        }
        if (action == MotionEvent.ACTION_MOVE && tracking && !dragging) {
            float dx = event.getRawX() - downX;
            float dy = event.getRawY() - downY;
            if (Math.abs(dy) > slop && Math.abs(dy) > Math.abs(dx) && shouldDrag(dy)) {
                dragging = true;
                return true;
            }
        }
        if (action == MotionEvent.ACTION_UP && tracking && !dragging && onHandle(downX, downY)) {
            float dx = event.getRawX() - downX;
            float dy = event.getRawY() - downY;
            if (Math.abs(dx) < slop && Math.abs(dy) < slop) {
                pull.onUp(event.getRawY(), true);
                tracking = false;
                return true;
            }
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) tracking = false;
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!dragging || pull == null) return super.onTouchEvent(event);
        int action = event.getAction();
        if (action == MotionEvent.ACTION_MOVE) {
            pull.onMove(event.getRawY());
            return true;
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            pull.onUp(event.getRawY(), false);
            dragging = false;
            tracking = false;
            return true;
        }
        return true;
    }

    private boolean onHandle(float x, float y) {
        View handle = findViewById(R.id.peopleTab);
        if (handle == null) return false;
        int[] loc = new int[2];
        handle.getLocationOnScreen(loc);
        return x >= loc[0] && x < loc[0] + handle.getWidth() && y >= loc[1] && y < loc[1] + handle.getHeight();
    }

    private boolean shouldDrag(float dy) {
        if (onHandle(downX, downY)) return true;
        if (getTranslationY() > dp(8)) return true;
        ScrollView scroll = findViewById(R.id.peopleScroll);
        if (scroll == null || scroll.getChildCount() == 0) return true;
        View child = scroll.getChildAt(0);
        boolean canScroll = child.getHeight() > scroll.getHeight() + 2;
        if (!canScroll) return true;
        if (dy > 0 && scroll.getScrollY() <= 0) return true;
        if (dy < 0 && scroll.getScrollY() + scroll.getHeight() >= child.getHeight() - 2) return true;
        return false;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
