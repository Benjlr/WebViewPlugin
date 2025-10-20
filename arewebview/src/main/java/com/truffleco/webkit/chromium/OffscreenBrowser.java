package com.truffleco.webkit.chromium;

import android.app.Activity;
import android.graphics.Color;
import android.view.Gravity;
import android.view.Surface;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import com.truffleco.viewtobuffer.CustomGLSurfaceView;
import com.truffleco.viewtobuffer.ViewToBufferLayout;
import com.truffleco.viewtobuffer.ViewToHWBRenderer;
import com.truffleco.viewtobuffer.ViewToPBORenderer;
import com.truffleco.viewtobuffer.ViewToSurfaceLayout;
import com.truffleco.webkit.BaseOffscreenBrowser;
import com.truffleco.webkit.CursorCapture;
import com.truffleco.webkit.VirtualMouse;
import com.unity3d.player.UnityPlayer;

public class OffscreenBrowser extends BaseOffscreenBrowser {
    protected CustomGLSurfaceView mGlSurfaceView;
    protected LinearLayout mCaptureLayout;
    private final static String TAG = "OffscreenBrowser (Chromium)";

    public void init() {
        Activity a = UnityPlayer.currentActivity;
        mRootLayout = new RelativeLayout(a);
        mRootLayout.setGravity(Gravity.TOP);
        mRootLayout.setX(mResState.screen.x);
        mRootLayout.setY(mResState.screen.y);
        mRootLayout.setBackgroundColor(0xFFFFFFFF);

        if ((mCaptureMode == CaptureMode.HardwareBuffer) || (mCaptureMode == CaptureMode.ByteBuffer)) {
            switch (mCaptureMode) {
                case HardwareBuffer:
                    mViewToBufferRenderer = new ViewToHWBRenderer();
                    break;
                case ByteBuffer:
                    mViewToBufferRenderer = new ViewToPBORenderer();
                    break;
            }
            mViewToBufferRenderer.setTextureResolution(mResState.tex.x, mResState.tex.y);

            mGlSurfaceView = new CustomGLSurfaceView(a);
            mGlSurfaceView.setEGLContextClientVersion(3);
            mGlSurfaceView.setEGLConfigChooser(8, 8, 8, 8, 0, 0);
            mGlSurfaceView.setPreserveEGLContextOnPause(true);
            mGlSurfaceView.setRenderer(mViewToBufferRenderer);
            mGlSurfaceView.setBackgroundColor(0x00000000);

            mCaptureLayout = new ViewToBufferLayout(a, mViewToBufferRenderer);
        } else if (mCaptureMode == CaptureMode.Surface) {
            mCaptureLayout = new ViewToSurfaceLayout(a);
        }

        mCaptureLayout.setOrientation(ViewToBufferLayout.VERTICAL);
        mCaptureLayout.setGravity(Gravity.START);
        mCaptureLayout.setBackgroundColor(Color.WHITE);

        a.addContentView(mRootLayout, new RelativeLayout.LayoutParams(mResState.view.x, mResState.view.y));
        if ((mCaptureMode == CaptureMode.HardwareBuffer) || (mCaptureMode == CaptureMode.ByteBuffer)) {
            mRootLayout.addView(mGlSurfaceView, new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT));
            mRootLayout.addView(mCaptureLayout, new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT));
        } else if (mCaptureMode == CaptureMode.Surface) {
            mRootLayout.addView(mCaptureLayout, new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT));
        }
        VirtualMouse.sBrowser = this;
        startFrameInvalidation(mCaptureLayout);
    }

    public void SetSurface(Object surfaceObj, int width, int height) {
        Surface surface = (Surface) surfaceObj;
        if (mCaptureLayout instanceof ViewToSurfaceLayout)
            ((ViewToSurfaceLayout) mCaptureLayout).setSurface(surface);
    }

    public void RemoveSurface() {
        if (mCaptureLayout instanceof ViewToSurfaceLayout)
            ((ViewToSurfaceLayout) mCaptureLayout).removeSurface();
    }

    @Override
    public void Dispose() {
        stopFrameInvalidation();
        VirtualMouse.sBrowser = null;
        CursorCapture.browserView = null;
    }

    @Override
    public String[] DispatchMessageQueue() {
        return new String[0];
    }

    @Override
    public String GetAsyncResult(int id) {
        return "";
    }

    @Override
    public void PostDialogResult(int result, String json) {

    }
}
