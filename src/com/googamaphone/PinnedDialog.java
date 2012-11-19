
package com.googamaphone;

import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnKeyListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.widget.ImageView;

import com.googamaphone.typeandspeak.R;

public class PinnedDialog {
    private final Rect mPinnedRect = new Rect();
    private final Rect mBoundsRect = new Rect();
    private final Rect mScreenRect = new Rect();

    private final Context mContext;
    private final WindowManager mWindowManager;
    private final ViewGroup mWindowView;
    private final ViewGroup mContentView;
    private final ImageView mTickAbove;
    private final ImageView mTickBelow;
    private final View mTickAbovePadding;
    private final View mTickBelowPadding;
    private final LayoutParams mParams;

    private View mPinnedView;

    private boolean mVisible = false;

    /**
     * Creates a new simple overlay.
     *
     * @param context The parent context.
     */
    public PinnedDialog(Context context) {
        mContext = context;
        mWindowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);

        mWindowView = (ViewGroup) LayoutInflater.from(context)
                .inflate(R.layout.pinned_dialog, null);
        mWindowView.setOnTouchListener(mOnTouchListener);
        mWindowView.setOnKeyListener(mOnKeyListener);

        mContentView = (ViewGroup) mWindowView.findViewById(R.id.content);

        mTickBelow = (ImageView) mWindowView.findViewById(R.id.tick_below);

        mTickAbove = (ImageView) mWindowView.findViewById(R.id.tick_above);
        mTickAbove.setVisibility(View.GONE);

        mTickAbovePadding = mWindowView.findViewById(R.id.tick_above_padding);
        mTickBelowPadding = mWindowView.findViewById(R.id.tick_below_padding);

        mParams = new WindowManager.LayoutParams();
        mParams.type = WindowManager.LayoutParams.TYPE_APPLICATION_PANEL;
        mParams.format = PixelFormat.TRANSLUCENT;
        mParams.flags |= WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
        mParams.width = LayoutParams.WRAP_CONTENT;
        mParams.height = LayoutParams.WRAP_CONTENT;
        mParams.windowAnimations = R.style.fade_dialog;

        mVisible = false;
    }

    /**
     * @return The overlay context.
     */
    public Context getContext() {
        return mContext;
    }

    /**
     * Finds and returns the view within the overlay content.
     *
     * @param id The ID of the view to return.
     * @return The view with the specified ID, or {@code null} if not found.
     */
    public View findViewById(int id) {
        return mWindowView.findViewById(id);
    }

    public final void cancel() {
        dismiss();
    }

    /**
     * Shows the overlay. Calls the listener's
     * {@link SimpleOverlayListener#onHide(SimpleOverlay)} if available.
     */
    public final void show(View pinnedView) {
        synchronized (mWindowView) {
            if (mVisible) {
                // This dialog is already showing.
                return;
            }

            mPinnedView = pinnedView;
            mWindowView.getViewTreeObserver().addOnGlobalLayoutListener(mOnGlobalLayoutListener);
            mWindowManager.addView(mWindowView, mParams);
            mVisible = true;
        }
    }

    public View getPinnedView() {
        return mPinnedView;
    }

    /**
     * Hides the overlay. Calls the listener's
     * {@link SimpleOverlayListener#onHide(SimpleOverlay)} if available.
     */
    @SuppressWarnings("deprecation")
    public final void dismiss() {
        synchronized (mWindowView) {
            if (!mVisible) {
                // This dialog is not visible.
                return;
            }

            mPinnedView = null;
            mWindowView.getViewTreeObserver().removeGlobalOnLayoutListener(mOnGlobalLayoutListener);
            mWindowManager.removeViewImmediate(mWindowView);
            mVisible = false;
        }
    }

    /**
     * @return A copy of the current layout parameters.
     */
    public LayoutParams getParams() {
        final LayoutParams copy = new LayoutParams();
        copy.copyFrom(mParams);
        return copy;
    }

    /**
     * Sets the current layout parameters and applies them immediately.
     *
     * @param params The layout parameters to use.
     */
    public void setParams(LayoutParams params) {
        mParams.copyFrom(params);

        synchronized (mWindowView) {
            if (!mVisible) {
                // This dialog is not showing.
                return;
            }

            mWindowManager.updateViewLayout(mWindowView, mParams);
        }
    }

    /**
     * @return {@code true} if this overlay is visible.
     */
    public boolean isVisible() {
        return mVisible;
    }

    /**
     * Inflates the specified resource ID and sets it as the content view.
     *
     * @param layoutResId The layout ID of the view to set as the content view.
     */
    public PinnedDialog setContentView(int layoutResId) {
        final LayoutInflater inflater = LayoutInflater.from(mContext);
        inflater.inflate(layoutResId, mContentView);
        return this;
    }

    public static final int ABOVE = 0x1;
    public static final int BELOW = 0x2;

    private void updatePinningOffsetLocked() {
        final int width = mWindowView.getWidth();
        final int height = mWindowView.getHeight();
        final LayoutParams params = getParams();

        final View rootView = mPinnedView.getRootView();
        final View parentContent = rootView.findViewById(android.R.id.content);
        rootView.getGlobalVisibleRect(mScreenRect);
        parentContent.getGlobalVisibleRect(mBoundsRect);
        mPinnedView.getGlobalVisibleRect(mPinnedRect);

        if ((mPinnedRect.bottom + height) <= mBoundsRect.bottom) {
            // Place below.
            params.y = (mPinnedRect.bottom);
            mTickBelow.setVisibility(View.VISIBLE);
            mTickAbove.setVisibility(View.GONE);
        } else if ((mPinnedRect.top - height) >= mScreenRect.top) {
            // Place above.
            params.y = (mPinnedRect.top - height);
            mTickBelow.setVisibility(View.GONE);
            mTickAbove.setVisibility(View.VISIBLE);
        } else {
            // Center on screen.
            params.y = (mScreenRect.centerY() - (height / 2));
            mTickBelow.setVisibility(View.GONE);
            mTickAbove.setVisibility(View.GONE);
        }

        // First, attempt to center on the pinned view.
        params.x = (mPinnedRect.centerX() - (width / 2));

        if (params.x < mBoundsRect.left) {
            // Align to left of parent.
            params.x = mBoundsRect.left;
        } else if ((params.x + width) > mBoundsRect.right) {
            // Align to right of parent.
            params.x = (mBoundsRect.right - width);
        }

        final int tickLeft = (mPinnedRect.centerX() - params.x) - (mTickAbove.getWidth() / 2);
        mTickAbovePadding.getLayoutParams().width = tickLeft;
        mTickBelowPadding.getLayoutParams().width = tickLeft;

        /*
         * if (xAlign == LEFT_OF) { params.x = (mTempRect.left - width); } else
         * if (xAlign == RIGHT_OF) { params.x = (mTempRect.right); } else if
         * (xAlign == ALIGN_LEFT) { params.x = (mTempRect.left); } else if
         * (xAlign == ALIGN_RIGHT) { params.x = (mTempRect.right - width); }
         * else if (xAlign == CENTER_HORIZONTAL) { params.x =
         * (mTempRect.centerX() - (width / 2)); } if (yAlign == ABOVE) {
         * params.y = (mTempRect.top - height); } else if (yAlign == BELOW) {
         * params.y = (mTempRect.bottom); } else if (yAlign == ALIGN_TOP) {
         * params.y = (mTempRect.top); } else if (yAlign == ALIGN_BOTTOM) {
         * params.y = (mTempRect.bottom - height); } else if (yAlign ==
         * CENTER_VERTICAL) { params.y = (mTempRect.centerY() - (height / 2)); }
         */

        params.gravity = Gravity.LEFT | Gravity.TOP;

        setParams(params);
    }

    private final OnGlobalLayoutListener mOnGlobalLayoutListener = new OnGlobalLayoutListener() {
        @Override
        public void onGlobalLayout() {
            synchronized (mWindowView) {
                if (!mVisible) {
                    // This dialog is not showing.
                    return;
                }

                if (mWindowView.getWindowToken() == null) {
                    mVisible = false;
                    // This dialog was removed by the system.
                    return;
                }

                updatePinningOffsetLocked();
            }
        }
    };

    private final OnKeyListener mOnKeyListener = new OnKeyListener() {
        @Override
        public boolean onKey(View v, int keyCode, KeyEvent event) {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                if (event.getAction() == KeyEvent.ACTION_UP) {
                    cancel();
                }
                return true;
            }

            return false;
        }
    };

    private final OnTouchListener mOnTouchListener = new OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                v.getHitRect(mPinnedRect);

                if (!mPinnedRect.contains((int) event.getX(), (int) event.getY())) {
                    cancel();
                }
            }

            return false;
        }
    };
}
