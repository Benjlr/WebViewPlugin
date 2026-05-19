package com.truffleco.webkit;

import android.annotation.SuppressLint;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;

public final class VirtualMouse {
    public static String getVersion() { return "virtualMouse-v6"; }
    @SuppressLint("StaticFieldLeak")
    public static BaseOffscreenBrowser sBrowser; // LEAKS
    private static long gestureStartTime = 0;
    private static int  combinedButtonStates = 0;
    private static boolean  cursorLocked = false;
    // Pre-allocated to avoid per-event heap pressure on constrained hardware.
    private static final long[] sEventTimes = new long[2];
    private static final MotionEvent.PointerCoords sPC = new MotionEvent.PointerCoords();
    private static final MotionEvent.PointerCoords[] sPCArray = new MotionEvent.PointerCoords[]{ sPC };
    private static long[] eventTime(boolean startGesture){
        final long t = SystemClock.uptimeMillis();
        if(startGesture) gestureStartTime = t;
        sEventTimes[0] = (gestureStartTime != 0 ? gestureStartTime : t);
        sEventTimes[1] = t;
        return sEventTimes;
    }
    private static final MotionEvent.PointerProperties[] PPROPS;
    static {
        MotionEvent.PointerProperties pp = new MotionEvent.PointerProperties();
        pp.id = 0; pp.toolType = MotionEvent.TOOL_TYPE_MOUSE;
        PPROPS = new MotionEvent.PointerProperties[]{ pp };
    }
    private static int getMouseButtonFromUnity(int unityButton) {
        switch (unityButton) {
            case 1: return MotionEvent.BUTTON_PRIMARY;
            case 2: return MotionEvent.BUTTON_SECONDARY;
            case 4: return MotionEvent.BUTTON_TERTIARY;
            default: return unityButton;
        }
    }
    private static void dispatch(MotionEvent ev) {
        if (sBrowser != null) {
            sBrowser.onMouseMotionEvent(ev);
        } else {
            ev.recycle();
        }
    }
    public static void mouseMove(int pixelX, int pixelY, float deltaX, float deltaY) {
        if(cursorLocked) {
            if (sBrowser != null) sBrowser.vmMove(deltaX, -deltaY, combinedButtonStates);
            return;
        }

        long[] eventTimes = eventTime(false);
        sPC.clear();
        sPC.setAxisValue(MotionEvent.AXIS_RELATIVE_X, deltaX);
        sPC.setAxisValue(MotionEvent.AXIS_RELATIVE_Y, deltaY);
        sPC.setAxisValue(MotionEvent.AXIS_X, pixelX);
        sPC.setAxisValue(MotionEvent.AXIS_Y, pixelY);
        dispatch(MotionEvent.obtain(
                eventTimes[0],
                eventTimes[1],
                combinedButtonStates == 0 ? MotionEvent.ACTION_HOVER_MOVE : MotionEvent.ACTION_MOVE,
                1,
                PPROPS,
                sPCArray,
                /*metaState*/0,
                /*buttonState*/combinedButtonStates,
                /*xPrecision*/1f,
                /*yPrecision*/1f,
                /*deviceId*/0,
                /*edgeFlags*/0,
                InputDevice.SOURCE_MOUSE,
                /*flags*/0
        ));
    }
    public static void mouseScroll(int pixelX, int pixelY, float deltaX, float deltaY) {
        long[] eventTimes = eventTime(false);
        sPC.clear();
        sPC.setAxisValue(MotionEvent.AXIS_X, pixelX);
        sPC.setAxisValue(MotionEvent.AXIS_Y, pixelY);
        sPC.setAxisValue(MotionEvent.AXIS_HSCROLL, deltaX);
        sPC.setAxisValue(MotionEvent.AXIS_VSCROLL, deltaY);
        dispatch(MotionEvent.obtain(
                eventTimes[0],
                eventTimes[1],
                MotionEvent.ACTION_SCROLL,
                1,
                PPROPS,
                sPCArray,
                /*metaState*/0,
                /*buttonState*/combinedButtonStates,
                /*xPrecision*/1f,
                /*yPrecision*/1f,
                /*deviceId*/0,
                /*edgeFlags*/0,
                InputDevice.SOURCE_MOUSE,
                /*flags*/0
        ));
    }
    public static void mouseButton(int pixelX, int pixelY, int button, boolean pressed) {

        if(pressed) combinedButtonStates |= getMouseButtonFromUnity(button);

        long[]  eventTimes = eventTime(pressed && gestureStartTime == 0);
        sPC.clear();
        sPC.setAxisValue(MotionEvent.AXIS_X, pixelX);
        sPC.setAxisValue(MotionEvent.AXIS_Y, pixelY);

        int action = pressed ? MotionEvent.ACTION_BUTTON_PRESS : MotionEvent.ACTION_BUTTON_RELEASE;

        dispatch(MotionEvent.obtain(
                eventTimes[0],
                eventTimes[1],
                action,
                1,
                PPROPS,
                sPCArray,
                /*metaState*/0,
                /*buttonState*/combinedButtonStates,
                /*xPrecision*/1f,
                /*yPrecision*/1f,
                /*deviceId*/0,
                /*edgeFlags*/0,
                InputDevice.SOURCE_MOUSE,
                /*flags*/0
        ));

        if(!pressed) combinedButtonStates &= ~(getMouseButtonFromUnity(button));
        if(combinedButtonStates == 0) gestureStartTime = 0;
    }
    public static void resetButtonState(){
        combinedButtonStates = 0;
        gestureStartTime = 0;
    }
    public static void lockCursor(boolean lock){
        if (cursorLocked == lock) return;
        resetButtonState();

        if (sBrowser != null) {
            if(lock) sBrowser.LockCursorOnWebView();
            else sBrowser.UnlockCursorOnWebView();
        }

        cursorLocked = lock;
    }
}
