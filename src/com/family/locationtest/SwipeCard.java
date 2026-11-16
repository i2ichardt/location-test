package com.family.locationtest;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.LinearLayout;

public class SwipeCard extends LinearLayout {
    private float downX;
    private float downY;
    private boolean tracking;
    private boolean dragging;
    private Runnable onDismiss;

    public SwipeCard(Context context) {
        super(context);
    }

    public SwipeCard(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setOnDismiss(Runnable onDismiss) {
        this.onDismiss = onDismiss;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            downX = event.getRawX();
            downY = event.getRawY();
            tracking = true;
            dragging = false;
            return false;
        }
        if (event.getAction() == MotionEvent.ACTION_MOVE && tracking) {
            float dx = event.getRawX() - downX;
            float dy = event.getRawY() - downY;
            if (Math.abs(dx) > dp(18) && Math.abs(dx) > Math.abs(dy)) {
                dragging = true;
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            }
        }
        if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) tracking = false;
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_MOVE && (dragging || tracking)) {
            float dx = event.getRawX() - downX;
            float dy = event.getRawY() - downY;
            if (!dragging && Math.abs(dx) > dp(18) && Math.abs(dx) > Math.abs(dy)) dragging = true;
            if (dragging) {
                setTranslationX(dx);
                return true;
            }
        }
        if ((event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) && dragging) {
            float dx = getTranslationX();
            if (Math.abs(dx) > getWidth() * 0.28f) {
                float away = dx < 0 ? -getWidth() : getWidth();
                animate().translationX(away).setDuration(140).withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        if (onDismiss != null) onDismiss.run();
                        setTranslationX(0);
                    }
                }).start();
            } else {
                animate().translationX(0).setDuration(140).start();
            }
            dragging = false;
            tracking = false;
            return true;
        }
        return super.onTouchEvent(event);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
