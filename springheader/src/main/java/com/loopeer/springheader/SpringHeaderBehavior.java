package com.loopeer.springheader;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.support.design.widget.CoordinatorLayout;
import android.support.v4.view.ViewCompat;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

public class SpringHeaderBehavior extends ViewOffsetBehavior<View> {

    public static final int STATE_COLLAPSED = 1;
    public static final int STATE_HOVERING = 2;
    public static final int STATE_DRAGGING = 3;
    public static final int STATE_SETTLING = 4;

    private int mState = STATE_COLLAPSED;

    private SpringHeaderCallback mCallback;

    private float mTotalUnconsumed;

    private int mDemandHoveringOffset = 0;
    private int mDemandMaxOffset = Integer.MAX_VALUE;
    private int mHoveringOffset;
    private int mMaxOffset;

    private ValueAnimator mAnimator;
    private EndListener mEndListener;

    public SpringHeaderBehavior() {
    }

    public SpringHeaderBehavior(Context context, AttributeSet attrs) {
        super(context, attrs);

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.SpringHeaderBehavior_Params);
        setHoveringOffset(a.getDimensionPixelSize(
                R.styleable.SpringHeaderBehavior_Params_behavior_hoveringOffset, 0));
        setMaxOffset(a.getDimensionPixelSize(
                R.styleable.SpringHeaderBehavior_Params_behavior_maxOffset, Integer.MAX_VALUE));
        a.recycle();
    }

    public void setHoveringOffset(int hoveringOffset) {
        mDemandHoveringOffset = hoveringOffset;
    }

    public void setMaxOffset(int maxOffset) {
        mDemandMaxOffset = maxOffset;
    }

    @Override
    public boolean onLayoutChild(CoordinatorLayout parent, View child, int layoutDirection) {
        boolean handled = super.onLayoutChild(parent, child, layoutDirection);
        mMaxOffset = Math.min(mDemandMaxOffset, parent.getHeight());
        mHoveringOffset = mDemandHoveringOffset > 0 && mDemandHoveringOffset < mMaxOffset
                ? mDemandHoveringOffset : child.getHeight();
        return handled;
    }

    @Override
    public boolean onStartNestedScroll(CoordinatorLayout coordinatorLayout, View child,
                                       View directTargetChild, View target, int nestedScrollAxes) {
        boolean started = (nestedScrollAxes & ViewCompat.SCROLL_AXIS_VERTICAL) != 0
                && mState != STATE_HOVERING;
        if (started && mAnimator != null && mAnimator.isRunning()) {
            mAnimator.cancel();
        }
        return started;
    }

    @Override
    public void onNestedScrollAccepted(CoordinatorLayout coordinatorLayout, View child,
                                       View directTargetChild, View target, int nestedScrollAxes) {
        mTotalUnconsumed = calculateScrollUnconsumed();
    }

    @Override
    public void onNestedPreScroll(CoordinatorLayout coordinatorLayout, View child, View target,
                                  int dx, int dy, int[] consumed) {
        if (dy > 0 && mTotalUnconsumed > 0) {
            if (dy > mTotalUnconsumed) {
                consumed[1] = dy - (int) mTotalUnconsumed;
                mTotalUnconsumed = 0;
            } else {
                mTotalUnconsumed -= dy;
                consumed[1] = dy;
            }
            setTopAndBottomOffset(calculateScrollOffset());
            setStateInternal(STATE_DRAGGING);
        }
    }

    @Override
    public void onNestedScroll(CoordinatorLayout coordinatorLayout, View child, View target,
                               int dxConsumed, int dyConsumed, int dxUnconsumed, int dyUnconsumed) {
        if (dyUnconsumed < 0) {
            mTotalUnconsumed -= dyUnconsumed;
            setTopAndBottomOffset(calculateScrollOffset());
            setStateInternal(STATE_DRAGGING);
        }
    }

    @Override
    public void onStopNestedScroll(CoordinatorLayout coordinatorLayout, View child, View target) {
        animateOffsetToState(getTopAndBottomOffset() >= mHoveringOffset
                ? STATE_HOVERING : STATE_COLLAPSED);
    }

    private void animateOffsetToState(int endState) {
        int from = getTopAndBottomOffset();
        int to = endState == STATE_HOVERING ? mHoveringOffset : 0;
        if (from == to) {
            setStateInternal(endState);
            return;
        } else {
            setStateInternal(STATE_SETTLING);
        }

        if (mAnimator == null) {
            mAnimator = new ValueAnimator();
            mAnimator.setDuration(200);
            mAnimator.setInterpolator(new DecelerateInterpolator());
            mAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    setTopAndBottomOffset((int) animation.getAnimatedValue());
                }
            });
            mEndListener = new EndListener(endState);
            mAnimator.addListener(mEndListener);
        } else {
            if (mAnimator.isRunning()) {
                mAnimator.cancel();
            }
            mEndListener.setEndState(endState);
        }
        mAnimator.setIntValues(from, to);
        mAnimator.start();
    }

    @Override
    public boolean setTopAndBottomOffset(int offset) {
        if (mCallback != null) {
            mCallback.onScroll(offset, (float) offset / mHoveringOffset);
        }
        return super.setTopAndBottomOffset(offset);
    }

    private void setStateInternal(int state) {
        if (state == mState) {
            return;
        }
        mState = state;
        if (mCallback != null) {
            mCallback.onStateChanged(state);
        }
    }

    public void setState(int state) {
        if (state != STATE_COLLAPSED && state != STATE_HOVERING) {
            throw new IllegalArgumentException("Illegal state argument: " + state);
        } else if (state != mState) {
            animateOffsetToState(state);
        }
    }

    private int calculateScrollOffset() {
        return (int) (mMaxOffset * (1 - Math.exp(-(mTotalUnconsumed / mMaxOffset / 2))));
    }

    private int calculateScrollUnconsumed() {
        return (int) (-Math.log(1 - (float) getTopAndBottomOffset() / mMaxOffset) * mMaxOffset * 2);
    }

    public void setSpringHeaderCallback(SpringHeaderCallback callback) {
        mCallback = callback;
    }

    public interface SpringHeaderCallback {
        void onScroll(int offset, float fraction);

        void onStateChanged(int newState);
    }

    private class EndListener extends AnimatorListenerAdapter {

        private int mEndState;
        private boolean mCanceling;

        public EndListener(int endState) {
            mEndState = endState;
        }

        public void setEndState(int finalState) {
            mEndState = finalState;
        }

        @Override
        public void onAnimationStart(Animator animation) {
            mCanceling = false;
        }

        @Override
        public void onAnimationCancel(Animator animation) {
            mCanceling = true;
        }

        @Override
        public void onAnimationEnd(Animator animation) {
            if (!mCanceling) {
                setStateInternal(mEndState);
            }
        }
    }
}
