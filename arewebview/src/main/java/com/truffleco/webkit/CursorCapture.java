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

public class CursorCapture implements  Application.ActivityLifecycleCallbacks {
    @SuppressLint("StaticFieldLeak")
    public static View browserView; // LEAKS
    private static final String TAG = "PointerCaptureHelper";
    @SuppressLint("StaticFieldLeak")
    private static final CursorCapture INSTANCE = new CursorCapture(); // LEAKS
    public static void SetBrowserView(View theView) {
        browserView = theView;
        Log.d(TAG, "BROWSER SET! IS IT NULL?? " + (browserView == null));
    }
    private static volatile float lastDx = 0, lastDy = 0;
    private static volatile int lastButtonState = 0;
    private static volatile int lastActionButton = 0;
    private static volatile float lastVerticalScrollDelta = 0;
    private static volatile float lastHorizontalScrollDelta = 0;
    private static volatile boolean captureRequested = false;
    private static volatile boolean hasCaptureConfirmed = false;

    private final Handler mainThreadHandler = new Handler(Looper.getMainLooper());
    private static boolean initialized = false;
    private static boolean captureAttached = false;
    private OnCapturedPointerListener capturedPointerListener = null;
    private CursorCapture() {
        capturedPointerListener = (@NonNull View view, @NonNull MotionEvent event) -> {
            if (captureRequested && !hasCaptureConfirmed) {
                Log.d(TAG, "onCapturedPointer: Capture confirmed by first event.");
                hasCaptureConfirmed = true;
            }
            if (!hasCaptureConfirmed)
                return false;

            int action = event.getAction();
            if (action == MotionEvent.ACTION_MOVE || action == MotionEvent.ACTION_HOVER_MOVE) {
                lastDx = event.getX();
                lastDy = event.getY();
//                Log.d(TAG, "Captured Relative Move: dx=" + lastDx + ", dy=" + lastDy);
            }

            lastButtonState = event.getButtonState();
            if (action == MotionEvent.ACTION_BUTTON_PRESS || action == MotionEvent.ACTION_BUTTON_RELEASE) {
                lastActionButton = event.getActionButton();
//                Log.d(TAG, "Captured Button State Change: action=" + action +
//                        ", button=" + lastActionButton + ", state=" + lastButtonState);
            }

            if (action == MotionEvent.ACTION_SCROLL) {
                if (event.getAxisValue(MotionEvent.AXIS_VSCROLL) != 0)
                    lastVerticalScrollDelta = event.getAxisValue(MotionEvent.AXIS_VSCROLL);
                if (event.getAxisValue(MotionEvent.AXIS_HSCROLL) != 0)
                    lastHorizontalScrollDelta = event.getAxisValue(MotionEvent.AXIS_HSCROLL);
            }

            // MAYBE THIS SHOULD BE TRUE ?? SEE IF MOTION EVENTS ARE 'DOUBLE HANDLED'
            return false;
        };
    }

    public static void initialize(@NonNull Context context) {
        if (initialized)
            return;

        Application app = (Application) context.getApplicationContext();
        Activity currentActivity = UnityPlayer.currentActivity;
        if (app != null && currentActivity != null) {
            app.registerActivityLifecycleCallbacks(INSTANCE);
            initialized = true;
            Log.d(TAG, "ActivityLifecycleCallbacks registered.");
        } else
            Log.e(TAG, "Initialization failed: Application context or current activity is null.");
    }

    // --- ActivityLifecycleCallbacks Implementation ---

    @Override public void onActivityResumed(@NonNull Activity activity) {
        Log.d(TAG, "onActivityResumed: " + activity.getLocalClassName());
        if (activity == UnityPlayer.currentActivity) {
            Log.d(TAG, "Unity Activity Resumed. Trying to attach listener.");
            tryAttachListener( );
        } else {
            // If a different activity is resumed, and we thought we had capture,
            // it means the Unity activity is likely paused or stopped.
            // Reset capture state in this case.
            if (hasCaptureConfirmed || captureRequested) {
                Log.d(TAG, "Different activity resumed, resetting capture state.");
                resetCaptureState();
            }
        }
    }
    @Override public void onActivityPaused(@NonNull Activity activity) {
        Log.d(TAG, "onActivityPaused: " + activity.getLocalClassName());
        // Check if the paused activity is the one we attached to
        tryDetachListener();
        resetCaptureState();
    }
    @Override public void onActivityStopped(@NonNull Activity activity) {
        // If the activity we are managing is stopped, reset the state.
        Log.d(TAG, "Unity Activity Stopped. Resetting state.");
        resetCaptureState();
    }
    @Override public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle bundle) {

    }
    @Override public void onActivityDestroyed(@NonNull Activity activity) {
        Log.d(TAG, "Unity Activity Destroyed. Clearing refs.");
        tryDetachListener(); // Ensure detachment
        resetCaptureState();
    }

    // --- Helper Methods ---

    public void tryAttachListener() {
        mainThreadHandler.post(() -> {
            if (browserView == null)
                return;

            if(captureAttached)
                return;

            Log.d(TAG, "Attaching pointer listener to Unity view: " + browserView.getClass().getName());
            browserView.setFocusable(true);
            browserView.setFocusableInTouchMode(true);
            browserView.requestFocus();
            browserView.setOnCapturedPointerListener(capturedPointerListener);
            captureAttached = true;
        });
    }
    private void tryDetachListener() {
        mainThreadHandler.post(() -> {
            if (browserView == null)
                return;

            Log.d(TAG, "tryDetachListener: Detaching listener from view: " + browserView.getClass().getName());
            browserView.setOnCapturedPointerListener(null);
            hasCaptureConfirmed = false;
            captureAttached = false;
        });
    }
    private void resetCaptureState() {
        if (captureRequested || hasCaptureConfirmed) {
            Log.d(TAG, "Resetting capture state flags and input deltas.");
        }
        captureRequested = false;
        hasCaptureConfirmed = false;
        lastDx = 0;
        lastDy = 0;
        lastButtonState = 0;
        lastActionButton = 0;
        lastVerticalScrollDelta = 0;
        lastHorizontalScrollDelta = 0;
    }

    // --- Static Methods for Unity ---

    public static void beginCapture() {
        Log.d(TAG, "beginCapture: Called from Unity. Requesting capture...");
        captureRequested = true;
        hasCaptureConfirmed = false;
        lastDx = 0;
        lastDy = 0;
        lastButtonState = 0;
        lastActionButton = 0;
        lastVerticalScrollDelta = 0;
        lastHorizontalScrollDelta = 0;

        INSTANCE.tryAttachListener();
        if(!captureAttached)
            return;

        INSTANCE.mainThreadHandler.postDelayed(() -> browserView.requestPointerCapture(), 100);
    }
    public static void endCapture() {
        Log.d(TAG, "endCapture: Called from Unity. Releasing capture...");
        INSTANCE.mainThreadHandler.post(() -> {
            browserView.releasePointerCapture();
            INSTANCE.resetCaptureState();
        });
    }
    public static boolean isPointerCaptured() {
        return hasCaptureConfirmed && browserView != null && browserView.hasPointerCapture();
    }
    public static float getLastDx() {
        float tmp = lastDx;
        lastDx = 0;
        return tmp;
    }
    public static float getLastDy() {
        float tmp = lastDy;
        lastDy = 0; // Consume the delta
        return tmp;
    }
    public static int getLastButtonState() {
        return lastButtonState;
    }
    public static int getLastActionButton() {
        int tmp = lastActionButton;
        lastActionButton = 0; // Consume the action button
        return tmp;
    }
    public static float getLastVerticalScrollDelta() {
        float tmp = lastVerticalScrollDelta;
        lastVerticalScrollDelta = 0; // Consume the delta
        return tmp;
    }
    public static float getLastHorizontalScrollDelta() {
        float tmp = lastHorizontalScrollDelta;
        lastHorizontalScrollDelta = 0; // Consume the delta
        return tmp;
    }




    // --- Unused ActivityLifecycleCallbacks methods ---
    @Override public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {}
    @Override
    public void onActivityStarted(@NonNull Activity activity) {}
}
