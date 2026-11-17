package com.family.locationtest;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.LinearLayout;
import android.widget.ScrollView;

/**
 * The people sheet both follows a finger and snaps fully open or closed on a tap.
 * A tap on the handle or the title toggles it. A drag on the handle, the title,
 * or a person row (when the list is not scrolling) moves it with the finger.
 */
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
        setClickable(true);
    }

    public void setPull(Pull pull) {
        this.pull = pull;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        if (pull == null) return false;
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            downX = event.getRawX();
            downY = event.getRawY();
            tracking = true;
            dragging = false;
            pull.onDown(downY);
            // Own the handle and title immediately. Otherwise a tap falls through
            // the map and the sheet never hears the lift.
            return onChrome(downX, downY);
        }
        if (action == MotionEvent.ACTION_MOVE && tracking && !dragging) {
            float dx = event.getRawX() - downX;
            float dy = event.getRawY() - downY;
            if (Math.abs(dy) > slop && Math.abs(dy) > Math.abs(dx) && shouldDrag(dy)) {
                dragging = true;
                return true;
            }
        }
        if (action == MotionEvent.ACTION_CANCEL) {
            tracking = false;
            dragging = false;
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (pull == null) return super.onTouchEvent(event);
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            downX = event.getRawX();
            downY = event.getRawY();
            tracking = true;
            dragging = false;
            pull.onDown(downY);
            return true;
        }
        if (!tracking && !dragging) return super.onTouchEvent(event);
        if (action == MotionEvent.ACTION_MOVE) {
            float dx = event.getRawX() - downX;
            float dy = event.getRawY() - downY;
            if (!dragging && Math.abs(dy) > slop && Math.abs(dy) > Math.abs(dx)) dragging = true;
            if (dragging) pull.onMove(event.getRawY());
            return true;
        }
        if (action == MotionEvent.ACTION_UP) {
            boolean tap = !dragging;
            pull.onUp(event.getRawY(), tap);
            tracking = false;
            dragging = false;
            return true;
        }
        if (action == MotionEvent.ACTION_CANCEL) {
            if (dragging) pull.onUp(event.getRawY(), false);
            tracking = false;
            dragging = false;
            return true;
        }
        return true;
    }

    private boolean onChrome(float x, float y) {
        return hit(R.id.peopleTab, x, y) || hit(R.id.peopleTitle, x, y);
    }

    private boolean hit(int id, float x, float y) {
        View view = findViewById(id);
        if (view == null || view.getWidth() <= 0) return false;
        int[] loc = new int[2];
        view.getLocationOnScreen(loc);
        return x >= loc[0] && x < loc[0] + view.getWidth() && y >= loc[1] && y < loc[1] + view.getHeight();
    }

    private boolean shouldDrag(float dy) {
        if (onChrome(downX, downY)) return true;
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
