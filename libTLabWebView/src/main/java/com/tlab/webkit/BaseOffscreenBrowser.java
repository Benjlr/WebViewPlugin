// File: com/tlab/webkit/BaseOffscreenBrowser.java
package com.tlab.webkit;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

import com.tlab.widget.AlertDialog;
import com.unity3d.player.UnityPlayer;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Objects;
import java.util.Queue;

public abstract class BaseOffscreenBrowser extends BaseOffscreenFragment
        implements IOffscreen, IBrowserCommon {

    protected final Common.Vector2Int mScrollState = new Common.Vector2Int();
    protected final Common.PageGoState mPageGoState = new Common.PageGoState();
    protected int  mMouseButtonState = 0; // MotionEvent.BUTTON_* bitfield
    protected long mMouseDownTime    = 0L;

    protected final Queue<Common.EventCallback.Message> mUnityPostMessageQueue = new ArrayDeque<>();
    protected final Common.AsyncResult.Manager mAsyncResult = new Common.AsyncResult.Manager();
    protected final Common.SessionState mSessionState = new Common.SessionState();
    protected AlertDialog.Callback mOnDialogResult;
    protected String[] mIntentFilters;
    protected View mView;

    public void SetIntentFilters(String[] intentFilters) {
        mIntentFilters = intentFilters;
    }
    @Override
    public int GetScrollX() { return mScrollState.x; }

    @Override
    public int GetScrollY() { return mScrollState.y; }

    public String[] DispatchMessageQueue() {
        String[] messages = new String[mUnityPostMessageQueue.size()];
        for (int i = 0; i < messages.length; i++)
            messages[i] = Objects.requireNonNull(mUnityPostMessageQueue.poll()).marshall();
        return messages;
    }
    public String GetAsyncResult(int id) {
        Common.AsyncResult result = mAsyncResult.get(id);
        return (result == null) ? "" : result.marshall();
    }
    public void CancelAsyncResult(int id) {
        mAsyncResult.post(new Common.AsyncResult(), Common.AsyncResult.Status.CANCEL);
    }
    public void PostDialogResult(int result, String json) {
        if (mOnDialogResult == null) return;

        UnityPlayer.currentActivity.runOnUiThread(() -> {
            try {
                mOnDialogResult.onResult(result, json.isEmpty() ? new JSONObject() : new JSONObject(json));
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
            mOnDialogResult = null;
        });
    }
    @Override
    public void ScrollTo(int x, int y) {
        final Activity a = UnityPlayer.currentActivity;
        a.runOnUiThread(() -> {
            if (mView == null) return;
            mView.scrollTo(x, y);
            mScrollState.x = mView.getScrollX();
            mScrollState.y = mView.getScrollY();
        });
    }

    @Override
    public void ScrollBy(int x, int y) {
        final Activity a = UnityPlayer.currentActivity;
        a.runOnUiThread(() -> {
            if (mView == null) return;
            mView.scrollBy(x, y);
            mScrollState.x = mView.getScrollX();
            mScrollState.y = mView.getScrollY();
        });
    }

    @Override
    public void KeyEvent(char key) {
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

    @Override
    public void KeyEvent(int keyCode) {
        final Activity a = UnityPlayer.currentActivity;
        a.runOnUiThread(() -> {
            if (mView == null) return;
            mView.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, keyCode));
            mView.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, keyCode));
        });
    }

    /**
     * Receives fully-formed mouse MotionEvents from BrowserMouseBridge.
     * If not on UI thread, clones and posts to avoid lifecycle/recycle issues.
     */
    @Override
    public void onMouseMotionEvent(MotionEvent ev) {
        if (mView == null) return;

        // (Optional) track for debugging
        mMouseButtonState = ev.getButtonState();
        final int actionMasked = ev.getActionMasked();

        final Activity activity = UnityPlayer.currentActivity;
        if (activity == null || activity.getMainLooper().isCurrentThread()) {
            routeEventDirect(ev, actionMasked);
        } else {
            final MotionEvent copy = MotionEvent.obtain(ev);
            activity.runOnUiThread(() -> routeEventDirect(copy, copy.getActionMasked()));
        }
    }

    private void routeEventDirect(MotionEvent ev, int actionMasked) {
        if (mView != null) {
            mView.dispatchGenericMotionEvent(ev);
        }
        // Force generic for all mouse events
        ev.recycle();
    }

    protected MotionEvent obtainMouseGeneric(
            long downTime, long eventTime, int action,
            float x, float y, int buttonState,
            Float hScroll, Float vScroll) {

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
}
