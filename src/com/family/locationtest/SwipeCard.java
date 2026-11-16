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
    public boolean dispatchTouchEvent(MotionEvent event) {
        int action = event.getAction();
        if (action == MotionEvent.ACTION_DOWN) {
            downX = event.getRawX();
            downY = event.getRawY();
            tracking = true;
            dragging = false;
            animate().cancel();
        } else if (action == MotionEvent.ACTION_MOVE && tracking) {
            float dx = event.getRawX() - downX;
            float dy = event.getRawY() - downY;
            if (!dragging && Math.abs(dx) > dp(16) && Math.abs(dx) > Math.abs(dy)) {
                dragging = true;
                MotionEvent cancel = MotionEvent.obtain(event);
                cancel.setAction(MotionEvent.ACTION_CANCEL);
                super.dispatchTouchEvent(cancel);
                cancel.recycle();
            }
            if (dragging) {
                setTranslationX(dx);
                return true;
            }
        } else if ((action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) && dragging) {
            float dx = getTranslationX();
            tracking = false;
            dragging = false;
            if (Math.abs(dx) > getWidth() * 0.28f) {
                float away = dx < 0 ? -getWidth() - dp(24) : getWidth() + dp(24);
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
            return true;
        }
        return super.dispatchTouchEvent(event);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
