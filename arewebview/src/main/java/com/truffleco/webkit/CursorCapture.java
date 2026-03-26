package com.truffleco.webkit;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnCapturedPointerListener;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.unity3d.player.UnityPlayer;

import java.util.concurrent.atomic.AtomicInteger;

public class CursorCapture implements Application.ActivityLifecycleCallbacks {
    @SuppressLint("StaticFieldLeak")
    public static View browserView; // LEAKS
    private static final String TAG = "PointerCaptureHelper";
    @SuppressLint("StaticFieldLeak")
    private static final CursorCapture INSTANCE = new CursorCapture(); // LEAKS

    public static void SetBrowserView(View theView) {
        browserView = theView;
    }

    // AtomicInteger stores float bits so that getAndSet(0) is a single atomic op.
    // A plain volatile float has a TOCTOU race: the read and the zero-write are
    // separate instructions, so a concurrent write in between would be lost.
    // lastDx/lastDy are overwritten (latest delta wins); scroll fields accumulate.
    private static final AtomicInteger lastDxBits      = new AtomicInteger(0);
    private static final AtomicInteger lastDyBits      = new AtomicInteger(0);
    private static final AtomicInteger lastVScrollBits = new AtomicInteger(0);
    private static final AtomicInteger lastHScrollBits = new AtomicInteger(0);
    private static volatile int     lastButtonState    = 0;
    private static final AtomicInteger lastActionButton = new AtomicInteger(0);
    private static volatile boolean captureRequested   = false;
    private static volatile boolean hasCaptureConfirmed = false;

    private final Handler mainThreadHandler = new Handler(Looper.getMainLooper());
    private static boolean initialized     = false;
    private static boolean captureAttached = false;

    private final OnCapturedPointerListener capturedPointerListener;

    private CursorCapture() {
        capturedPointerListener = (@NonNull View view, @NonNull MotionEvent event) -> {
            if (captureRequested && !hasCaptureConfirmed) {
                hasCaptureConfirmed = true;
            }
            if (!hasCaptureConfirmed) return false;

            int action = event.getAction();
            if (action == MotionEvent.ACTION_MOVE || action == MotionEvent.ACTION_HOVER_MOVE) {
                // Latest delta wins for cursor movement (no accumulation needed).
                lastDxBits.set(Float.floatToRawIntBits(event.getX()));
                lastDyBits.set(Float.floatToRawIntBits(event.getY()));
            }

            lastButtonState = event.getButtonState();
            if (action == MotionEvent.ACTION_BUTTON_PRESS || action == MotionEvent.ACTION_BUTTON_RELEASE) {
                lastActionButton.set(event.getActionButton());
            }

            if (action == MotionEvent.ACTION_SCROLL) {
                // Accumulate: multiple scroll events between Unity frames must not be lost.
                addFloatAtomic(lastVScrollBits, event.getAxisValue(MotionEvent.AXIS_VSCROLL));
                addFloatAtomic(lastHScrollBits, event.getAxisValue(MotionEvent.AXIS_HSCROLL));
            }

            return false;
        };
    }

    /** CAS-based atomic float addition. Safe for concurrent accumulation. */
    private static void addFloatAtomic(AtomicInteger bits, float delta) {
        int cur, next;
        do {
            cur = bits.get();
            next = Float.floatToRawIntBits(Float.intBitsToFloat(cur) + delta);
        } while (!bits.compareAndSet(cur, next));
    }

    public static void initialize(@NonNull Context context) {
        if (initialized) return;
        Application app = (Application) context.getApplicationContext();
        if (app == null) {
            Log.e(TAG, "Initialization failed: application context is null");
            return;
        }
        app.registerActivityLifecycleCallbacks(INSTANCE);
        initialized = true;
    }

    // --- ActivityLifecycleCallbacks ---

    @Override public void onActivityResumed(@NonNull Activity activity) {
        if (activity == UnityPlayer.currentActivity) {
            tryAttachListener();
        } else if (hasCaptureConfirmed || captureRequested) {
            resetCaptureState();
        }
    }
    @Override public void onActivityPaused(@NonNull Activity activity)  { tryDetachListener(); resetCaptureState(); }
    @Override public void onActivityStopped(@NonNull Activity activity) { resetCaptureState(); }
    @Override public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle bundle) {}
    @Override public void onActivityDestroyed(@NonNull Activity activity) { tryDetachListener(); resetCaptureState(); }
    @Override public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {}
    @Override public void onActivityStarted(@NonNull Activity activity) {}

    // --- Helpers ---

    public void tryAttachListener() {
        mainThreadHandler.post(() -> {
            if (browserView == null || captureAttached) return;
            browserView.setFocusable(true);
            browserView.setFocusableInTouchMode(true);
            browserView.requestFocus();
            browserView.setOnCapturedPointerListener(capturedPointerListener);
            captureAttached = true;
        });
    }

    private void tryDetachListener() {
        mainThreadHandler.post(() -> {
            if (browserView == null) return;
            browserView.setOnCapturedPointerListener(null);
            hasCaptureConfirmed = false;
            captureAttached = false;
        });
    }

    private void resetCaptureState() {
        captureRequested = false;
        hasCaptureConfirmed = false;
        lastDxBits.set(0);
        lastDyBits.set(0);
        lastButtonState = 0;
        lastActionButton.set(0);
        lastVScrollBits.set(0);
        lastHScrollBits.set(0);
    }

    // --- Static API for Unity ---

    public static void beginCapture() {
        INSTANCE.resetCaptureState();
        captureRequested = true;

        INSTANCE.tryAttachListener();
        if (!captureAttached) return;

        INSTANCE.mainThreadHandler.postDelayed(() -> {
            View v = browserView;
            if (v != null) v.requestPointerCapture();
        }, 100);
    }

    public static void endCapture() {
        INSTANCE.mainThreadHandler.post(() -> {
            View v = browserView;
            if (v != null) v.releasePointerCapture();
            INSTANCE.resetCaptureState();
        });
    }

    public static boolean isPointerCaptured() {
        return hasCaptureConfirmed && browserView != null && browserView.hasPointerCapture();
    }

    public static float getLastDx()     { return Float.intBitsToFloat(lastDxBits.getAndSet(0)); }
    public static float getLastDy()     { return Float.intBitsToFloat(lastDyBits.getAndSet(0)); }
    public static int   getLastButtonState()  { return lastButtonState; }
    public static int   getLastActionButton() { return lastActionButton.getAndSet(0); }
    public static float getLastVerticalScrollDelta()   { return Float.intBitsToFloat(lastVScrollBits.getAndSet(0)); }
    public static float getLastHorizontalScrollDelta() { return Float.intBitsToFloat(lastHScrollBits.getAndSet(0)); }
}
