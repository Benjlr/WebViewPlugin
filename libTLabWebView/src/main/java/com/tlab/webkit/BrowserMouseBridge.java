package com.tlab.webkit;

import android.os.Build;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;

public final class BrowserMouseBridge {
    public static final String MOUSE_BRIDGE_VERSION = "mb-2025-08-Oct-f"; // <- change on each build
    public static String getVersion() { return MOUSE_BRIDGE_VERSION; }

    private static BaseOffscreenBrowser sBrowser;

    public static boolean hasBrowser() { return sBrowser != null; }
    public static void setBrowser(BaseOffscreenBrowser browser) { sBrowser = browser; }

    // ---- Running mouse state ----
    private static int  sButtonState = 0; // MotionEvent.BUTTON_* bitfield
    private static long sDownTime    = 0; // First down of current gesture (0 if idle)
    private static final boolean USE_PRESS_FOR_PRIMARY = true;
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

    private static MotionEvent obtainMouseEvent(
            long downTime, long eventTime, int action,
            float x, float y, int buttonState, int actionButton /*0 if unknown*/) {

        MotionEvent.PointerProperties[] pps = new MotionEvent.PointerProperties[1];
        MotionEvent.PointerProperties pp = new MotionEvent.PointerProperties();
        pp.id = 0;
        pp.toolType = MotionEvent.TOOL_TYPE_MOUSE;
        pps[0] = pp;

        MotionEvent.PointerCoords[] pcs = new MotionEvent.PointerCoords[1];
        MotionEvent.PointerCoords pc = new MotionEvent.PointerCoords();
        pc.x = x;
        pc.y = y;
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
        if ((action == MotionEvent.ACTION_BUTTON_PRESS || action == MotionEvent.ACTION_BUTTON_RELEASE)
                && actionButton != 0 && Build.VERSION.SDK_INT >= 23) {
            try {
                MotionEvent.class.getMethod("setActionButton", int.class).invoke(ev, actionButton);
            } catch (Throwable ignore) {
                // No-op on older builds or if reflection fails
            }
        }
        return ev;
    }

    private static MotionEvent obtainMouseGeneric(
            long downTime, long eventTime, int action,
            float x, float y, int buttonState,
            Float hScroll /*nullable*/, Float vScroll /*nullable*/) {

        MotionEvent.PointerProperties[] pps = new MotionEvent.PointerProperties[1];
        MotionEvent.PointerProperties pp = new MotionEvent.PointerProperties();
        pp.id = 0;
        pp.toolType = MotionEvent.TOOL_TYPE_MOUSE;
        pps[0] = pp;

        MotionEvent.PointerCoords[] pcs = new MotionEvent.PointerCoords[1];
        MotionEvent.PointerCoords pc = new MotionEvent.PointerCoords();
        pc.x = x;
        pc.y = y;
        if (hScroll != null) pc.setAxisValue(MotionEvent.AXIS_HSCROLL, hScroll);
        if (vScroll != null) pc.setAxisValue(MotionEvent.AXIS_VSCROLL, vScroll);
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
        if (sButtonState == 0) sDownTime = t;
        sButtonState |= btn;

        // Always use ACTION_BUTTON_PRESS for mouse (even primary)
        MotionEvent ev = obtainMouseEvent(sDownTime, t,
                MotionEvent.ACTION_BUTTON_PRESS, x, y, sButtonState, btn);
        dispatch(ev);
        return sDownTime;
    }

    public static void mouseButtonUp(int x, int y, int buttonMask) {
        final long t = SystemClock.uptimeMillis();
        final int btn = normalizeMask(buttonMask);
        sButtonState &= ~btn;

        // Always use ACTION_BUTTON_RELEASE for mouse
        MotionEvent ev = obtainMouseEvent((sDownTime != 0 ? sDownTime : t),
                t, MotionEvent.ACTION_BUTTON_RELEASE, x, y, sButtonState, btn);
        dispatch(ev);

        if (sButtonState == 0) sDownTime = 0;
    }

    public static void mouseMove(int x, int y) {
        final long t = SystemClock.uptimeMillis();
        final long down = (sDownTime != 0 ? sDownTime : t);
        // Keep moves as HOVER_MOVE (generic) even while a button is down
        MotionEvent ev = obtainMouseGeneric(down, t,
                MotionEvent.ACTION_HOVER_MOVE, x, y, sButtonState, null, null);
        dispatch(ev);
    }

    public static void mouseHoverEnter(int x, int y) {
        final long t = SystemClock.uptimeMillis();
        MotionEvent ev = obtainMouseGeneric(t, t, MotionEvent.ACTION_HOVER_ENTER, x, y, 0, null, null);
        dispatch(ev);
    }

    public static void mouseHoverExit(int x, int y) {
        final long t = SystemClock.uptimeMillis();
        MotionEvent ev = obtainMouseGeneric(t, t, MotionEvent.ACTION_HOVER_EXIT, x, y, 0, null, null);
        dispatch(ev);
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
                (vAdj == 0f ? null : vAdj)
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
