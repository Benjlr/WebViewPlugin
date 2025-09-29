// File: com/tlab/webkit/IOffscreen.java
package com.tlab.webkit;

import android.view.MotionEvent;

public interface IOffscreen {
    /**
     * Get content's scroll position x
     *
     * @return Page content's current scroll position x
     */
    int GetScrollX();

    /**
     * Get content's scroll position y
     *
     * @return Page content's current scroll position y
     */
    int GetScrollY();

    /**
     * Set content's scroll position.
     *
     * @param x Scroll position x of the destination
     * @param y Scroll position y of the destination
     */
    void ScrollTo(int x, int y);

    /**
     * Move the scrolled position of the content view.
     *
     * @param x The amount of pixels to scroll by horizontally
     * @param y The amount of pixels to scroll by vertically
     */
    void ScrollBy(int x, int y);

    /**
     * Dispatch a fully-formed mouse MotionEvent (SOURCE_MOUSE, buttonState set, etc.).
     * Callers (e.g., BrowserMouseBridge) are responsible for setting:
     *   - event source = SOURCE_MOUSE
     *   - buttonState (running bitfield)
     *   - action (DOWN/UP/MOVE/HOVER_ /SCROLL/BUTTON_PRESS/BUTTON_RELEASE)
            * This method will route it to the appropriate View dispatcher.
            *
            * NOTE: The caller may recycle its event AFTER this returns. If we need to
     *       hop to UI thread, we will clone the event first with MotionEvent.obtain(ev).
            */
    void onMouseMotionEvent(MotionEvent ev);

    /**
     * Dispatch a basic key event. (Optional convenience for WebView shortcuts, etc.)
     */
    void KeyEvent(char key);

    void KeyEvent(int keyCode);
}
