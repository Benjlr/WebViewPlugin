package com.tlab.webkit;

import android.view.MotionEvent;

public interface IBrowserCommon {
    String[] DispatchMessageQueue();

    String GetAsyncResult(int id);

    void CancelAsyncResult(int id);

    /**
     * Register url patterns to treat as deep links
     *
     * @param intentFilters Url patterns that are treated as deep links (regular expression)
     */
    void SetIntentFilters(String[] intentFilters);

    void PostDialogResult(int result, String json);

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
    void onMouseMotionEvent(MotionEvent ev);
    void KeyEvent(char key);

    void KeyEvent(int keyCode);
}
