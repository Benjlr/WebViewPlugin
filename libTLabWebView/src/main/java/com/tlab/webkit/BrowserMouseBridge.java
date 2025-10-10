package com.tlab.webkit;

import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;

public final class BrowserMouseBridge {
    public static final String MOUSE_BRIDGE_VERSION = "mb-2025-10-Oct"; // <- change on each build
    public static String getVersion() { return MOUSE_BRIDGE_VERSION; }

    private static BaseOffscreenBrowser sBrowser;

    public static boolean hasBrowser() { return sBrowser != null; }
    public static void setBrowser(BaseOffscreenBrowser browser) { sBrowser = browser; }

    // ---- Running mouse state ----
    private static int  sButtonState = 0; // MotionEvent.BUTTON_* bitfield
    private static long sDownTime    = 0; // First down of current gesture (0 if idle)
    private static boolean sHasLastCoords = false;
    private static float sLastX = 0f;
    private static float sLastY = 0f;
    private static final float SCROLL_MULT = 1.9f;   // tune (1.0–3.0)
    private static final boolean INVERT_SCROLL_Y = false;
    private static final boolean HYBRID_SCROLL = false; // fallback ScrollBy for stubborn WebViews
    // ---- Helpers -------------------------------------------------------------

    private static int normalizeMask(int mask) {
        // Unity side currently passes 1,2,4 which already match Android BUTTON_*.
        // Leave a switch to future-proof for index-based inputs.
        switch (mask) {
            case 1: return MotionEvent.BUTTON_PRIMARY;
            case 2: return MotionEvent.BUTTON_SECONDARY;
            case 4: return MotionEvent.BUTTON_TERTIARY;
            default: return mask; // pass through any other Android button flags (e.g., BACK/FORWARD)
        }
    }

    private static float pressureForState(int buttonState) {
        return buttonState == 0 ? 0f : 1f;
    }

    private static float relativeX(float x) {
        return sHasLastCoords ? x - sLastX : 0f;
    }

    private static float relativeY(float y) {
        return sHasLastCoords ? y - sLastY : 0f;
    }

    private static void updateLastCoords(float x, float y) {
        sLastX = x;
        sLastY = y;
        sHasLastCoords = true;
    }

    private static void resetLastCoords() {
        sHasLastCoords = false;
    }

    private static MotionEvent obtainMouseEvent(
            long downTime, long eventTime, int action,
            float x, float y, int buttonState, int actionButton /*0 if unknown*/,
            Float relX /*nullable*/, Float relY /*nullable*/) {

        MotionEvent.PointerProperties[] pps = new MotionEvent.PointerProperties[1];
        MotionEvent.PointerProperties pp = new MotionEvent.PointerProperties();
        pp.id = 0;
        pp.toolType = MotionEvent.TOOL_TYPE_MOUSE;
        pps[0] = pp;

        MotionEvent.PointerCoords[] pcs = new MotionEvent.PointerCoords[1];
        MotionEvent.PointerCoords pc = new MotionEvent.PointerCoords();
        pc.x = x;
        pc.y = y;
        pc.pressure = pressureForState(buttonState);
        if (relX != null) pc.setAxisValue(MotionEvent.AXIS_RELATIVE_X, relX);
        if (relY != null) pc.setAxisValue(MotionEvent.AXIS_RELATIVE_Y, relY);
        pcs[0] = pc;

        MotionEvent ev = MotionEvent.obtain(
                downTime, eventTime, action,
                1, pps, pcs,
                /*metaState*/0, /*buttonState*/buttonState,
                /*xPrecision*/1f, /*yPrecision*/1f,
                /*deviceId*/0, /*edgeFlags*/0,
                InputDevice.SOURCE_MOUSE, /*flags*/0
        );

        // For ACTION_BUTTON_PRESS/RELEASE, set which button changed (API 23+)
        if (actionButton != 0) {
            ev.setAction(actionButton);
        }
        return ev;
    }

    private static MotionEvent obtainMouseGeneric(
            long downTime, long eventTime, int action,
            float x, float y, int buttonState,
            Float hScroll /*nullable*/, Float vScroll /*nullable*/,
            Float relX /*nullable*/, Float relY /*nullable*/) {

        MotionEvent.PointerProperties[] pps = new MotionEvent.PointerProperties[1];
        MotionEvent.PointerProperties pp = new MotionEvent.PointerProperties();
        pp.id = 0;
        pp.toolType = MotionEvent.TOOL_TYPE_MOUSE;
        pps[0] = pp;

        MotionEvent.PointerCoords[] pcs = new MotionEvent.PointerCoords[1];
        MotionEvent.PointerCoords pc = new MotionEvent.PointerCoords();
        pc.x = x;
        pc.y = y;
        pc.pressure = pressureForState(buttonState);
        if (hScroll != null) pc.setAxisValue(MotionEvent.AXIS_HSCROLL, hScroll);
        if (vScroll != null) pc.setAxisValue(MotionEvent.AXIS_VSCROLL, vScroll);
        if (relX != null) pc.setAxisValue(MotionEvent.AXIS_RELATIVE_X, relX);
        if (relY != null) pc.setAxisValue(MotionEvent.AXIS_RELATIVE_Y, relY);
        pcs[0] = pc;

        return MotionEvent.obtain(
                downTime, eventTime, action,
                1, pps, pcs,
                /*metaState*/0, /*buttonState*/buttonState,
                /*xPrecision*/1f, /*yPrecision*/1f,
                /*deviceId*/0, /*edgeFlags*/0,
                InputDevice.SOURCE_MOUSE, /*flags*/0
        );
    }


    private static final String TAG = "MouseBridge";

    private static void logEv(String label, MotionEvent ev) {
        float hv = ev.getAxisValue(MotionEvent.AXIS_HSCROLL);
        float vv = ev.getAxisValue(MotionEvent.AXIS_VSCROLL);
        android.util.Log.d("MouseBridge",
                label + " act=" + ev.getActionMasked()
                        + " btnState=" + ev.getButtonState()
                        + " src=0x" + Integer.toHexString(ev.getSource())
                        + " x=" + ev.getX() + " y=" + ev.getY()
                        + " h=" + hv + " v=" + vv);
    }

    private static void dispatch(MotionEvent ev) {

        if (sBrowser != null) {
            sBrowser.onMouseMotionEvent(ev);
        } else {
            ev.recycle();
        }
    }

    // ---- Public API (called from Unity C# via AndroidJavaClass) -------------

    public static long mouseButtonDown(int x, int y, int buttonMask) {
        final long t = SystemClock.uptimeMillis();
        final int btn = normalizeMask(buttonMask);
        final boolean wasPrimaryDown = (sButtonState & MotionEvent.BUTTON_PRIMARY) != 0;
        if (sButtonState == 0) sDownTime = t;
        sButtonState |= btn;

        final boolean isPrimaryPress = (btn & MotionEvent.BUTTON_PRIMARY) != 0 && !wasPrimaryDown;
        final int action = isPrimaryPress ? MotionEvent.ACTION_DOWN : MotionEvent.ACTION_BUTTON_PRESS;
        final float relX = relativeX(x);
        final float relY = relativeY(y);

        MotionEvent ev = obtainMouseEvent(sDownTime, t,
                action, x, y, sButtonState, btn,
                Float.valueOf(relX), Float.valueOf(relY));
        dispatch(ev);
        updateLastCoords(x, y);
        return sDownTime;
    }

    public static void mouseButtonUp(int x, int y, int buttonMask) {
        final long t = SystemClock.uptimeMillis();
        final int btn = normalizeMask(buttonMask);
        final boolean wasPrimaryDown = (sButtonState & MotionEvent.BUTTON_PRIMARY) != 0;
        sButtonState &= ~btn;

        final boolean isPrimaryRelease = wasPrimaryDown
                && (sButtonState & MotionEvent.BUTTON_PRIMARY) == 0;
        final int action = isPrimaryRelease ? MotionEvent.ACTION_UP : MotionEvent.ACTION_BUTTON_RELEASE;
        final float relX = relativeX(x);
        final float relY = relativeY(y);

        MotionEvent ev = obtainMouseEvent((sDownTime != 0 ? sDownTime : t),
                t, action, x, y, sButtonState, btn,
                Float.valueOf(relX), Float.valueOf(relY));
        dispatch(ev);
        updateLastCoords(x, y);

        if (sButtonState == 0) sDownTime = 0;
    }

    public static void mouseMove(int x, int y) {
        final long t = SystemClock.uptimeMillis();
        final long down = (sDownTime != 0 ? sDownTime : t);
        final boolean isDragging = sButtonState != 0;
        final int action = isDragging ? MotionEvent.ACTION_MOVE : MotionEvent.ACTION_HOVER_MOVE;
        final float relX = relativeX(x);
        final float relY = relativeY(y);
        MotionEvent ev;
        if (isDragging) {
            ev = obtainMouseEvent(down, t, action, x, y, sButtonState, 0,
                    Float.valueOf(relX), Float.valueOf(relY));
        } else {
            ev = obtainMouseGeneric(down, t,
                    action, x, y, sButtonState, null, null,
                    Float.valueOf(relX), Float.valueOf(relY));
        }
        dispatch(ev);
        updateLastCoords(x, y);
    }

    public static void mouseHoverEnter(int x, int y) {
        final long t = SystemClock.uptimeMillis();
        MotionEvent ev = obtainMouseGeneric(t, t, MotionEvent.ACTION_HOVER_ENTER, x, y, 0, null, null, null, null);
        dispatch(ev);
        updateLastCoords(x, y);
    }

    public static void mouseHoverExit(int x, int y) {
        final long t = SystemClock.uptimeMillis();
        MotionEvent ev = obtainMouseGeneric(t, t, MotionEvent.ACTION_HOVER_EXIT, x, y, 0, null, null, null, null);
        dispatch(ev);
        resetLastCoords();
    }

    public static void mouseScroll(int x, int y, float hScroll, float vScroll) {
        final long t = SystemClock.uptimeMillis();
        final long down = (sDownTime == 0 ? t : sDownTime);

        // Normalize to "notch" steps; coerce tiny values to ±1 so site actually sees movement
        float hAdj = hScroll * SCROLL_MULT;
        float vAdj = (INVERT_SCROLL_Y ? -vScroll : vScroll) * SCROLL_MULT;

        if (Math.abs(hAdj) > 0f && Math.abs(hAdj) < 1f) hAdj = (hAdj > 0 ? 1f : -1f);
        if (Math.abs(vAdj) > 0f && Math.abs(vAdj) < 1f) vAdj = (vAdj > 0 ? 1f : -1f);

        // Use buttonState=0 for scroll; set axes on PointerCoords
        MotionEvent ev = obtainMouseGeneric(
                down, t, MotionEvent.ACTION_SCROLL,
                x, y, /*buttonState*/0,
                (hAdj == 0f ? null : hAdj),
                (vAdj == 0f ? null : vAdj),
                null, null
        );
        dispatch(ev);

        // Hybrid fallback: also nudge the view’s scroll (pixel-based) for OEMs that ignore ACTION_SCROLL
        if (HYBRID_SCROLL && sBrowser != null && (hAdj != 0f || vAdj != 0f)) {
            int pxX = Math.round(-hAdj * 40f); // right = positive
            int pxY = Math.round(-vAdj * 40f); // down  = positive
            sBrowser.ScrollBy(pxX, pxY);
        }
    }
}
