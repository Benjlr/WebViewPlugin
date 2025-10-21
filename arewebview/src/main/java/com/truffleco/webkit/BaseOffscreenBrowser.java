
package com.truffleco.webkit;

import android.app.Activity;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

import com.unity3d.player.UnityPlayer;

import java.util.ArrayDeque;
import java.util.Queue;

public abstract class BaseOffscreenBrowser extends BaseOffscreenFragment implements IBrowserCommon {

    protected final Common.Vector2Int mScrollState = new Common.Vector2Int();
    protected final Common.PageGoState mPageGoState = new Common.PageGoState();
    protected final Queue<Common.EventCallback.Message> mUnityPostMessageQueue = new ArrayDeque<>();
    protected final Common.AsyncResult.Manager mAsyncResult = new Common.AsyncResult.Manager();
    protected final Common.SessionState mSessionState = new Common.SessionState();
    protected String[] mIntentFilters;
    protected View mView;
    public void SetIntentFilters(String[] intentFilters) {
        mIntentFilters = intentFilters;
    }
    @Override public int GetScrollX() { return mScrollState.x; }
    @Override public int GetScrollY() { return mScrollState.y; }

    public void CancelAsyncResult(int id) {
        mAsyncResult.post(new Common.AsyncResult(), Common.AsyncResult.Status.CANCEL);
    }

    @Override public void ScrollTo(int x, int y) {
        final Activity a = UnityPlayer.currentActivity;
        a.runOnUiThread(() -> {
            if (mView == null) return;
            mView.scrollTo(x, y);
            mScrollState.x = mView.getScrollX();
            mScrollState.y = mView.getScrollY();
        });
    }
    @Override public void ScrollBy(int x, int y) {
        final Activity a = UnityPlayer.currentActivity;
        a.runOnUiThread(() -> {
            if (mView == null) return;
            mView.scrollBy(x, y);
            mScrollState.x = mView.getScrollX();
            mScrollState.y = mView.getScrollY();
        });
    }
    @Override  public void KeyEvent(char key) {
        final Activity a = UnityPlayer.currentActivity;
        a.runOnUiThread(() -> {
            if (mView == null) return;
            KeyCharacterMap kcm = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD);
            KeyEvent[] events = kcm.getEvents(new char[]{key});
            if (events != null) {
                for (KeyEvent event : events) mView.dispatchKeyEvent(event);
            }
        });
    }
    @Override public void KeyEvent(int keyCode) {
        final Activity a = UnityPlayer.currentActivity;
        a.runOnUiThread(() -> {
            if (mView == null) return;
            mView.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, keyCode));
            mView.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, keyCode));
        });
    }
    @Override public void onMouseMotionEvent(MotionEvent ev) {
        if (mView == null) return;
        final Activity activity = UnityPlayer.currentActivity;
        activity.runOnUiThread(() -> routeToWebView(ev));
    }
    public boolean routeToWebView(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            // Touch stream
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_MOVE:
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_OUTSIDE:
                return mView.dispatchTouchEvent(ev);

            // Generic motion stream (mouse hover, wheel, button changes)
            case MotionEvent.ACTION_HOVER_ENTER:
            case MotionEvent.ACTION_HOVER_MOVE:
            case MotionEvent.ACTION_HOVER_EXIT:
            case MotionEvent.ACTION_SCROLL:
            case MotionEvent.ACTION_BUTTON_PRESS:
            case MotionEvent.ACTION_BUTTON_RELEASE:
                return mView.dispatchGenericMotionEvent(ev);

            default:
                if (mView.dispatchGenericMotionEvent(ev))
                    return true;
                return mView.dispatchTouchEvent(ev);
        }
    }


}
