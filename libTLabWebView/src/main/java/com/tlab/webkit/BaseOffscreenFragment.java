package com.tlab.webkit;

import android.app.Activity;
import android.hardware.HardwareBuffer;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;

import com.robot9.shared.SharedTexture;
import com.tlab.viewtobuffer.ViewToBufferRenderer;
import com.tlab.viewtobuffer.ViewToHWBRenderer;
import com.tlab.viewtobuffer.ViewToPBORenderer;
import com.unity3d.player.UnityPlayer;

import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicReference;

public abstract class BaseOffscreenFragment {

    private static final String TAG = "BaseOffscreenFragment";
    public enum CaptureMode {
        HardwareBuffer, ByteBuffer, Surface
    }
    protected CaptureMode mCaptureMode = CaptureMode.HardwareBuffer;
    protected final Common.ResolutionState mResState = new Common.ResolutionState();
    protected ViewToBufferRenderer mViewToBufferRenderer;
    protected RelativeLayout mRootLayout;
    protected long[] mHwbTexID;
    protected boolean mIsVulkan;
    protected SharedTexture mSharedTexture;
    protected HardwareBuffer mSharedBuffer;
    protected boolean mCaptureThreadKeepAlive = false;
    protected final Object mCaptureThreadMutex = new Object();
    protected int mFps = 30;
    public boolean mInitialized = false;
    public boolean mDisposed = false;
    protected boolean mIsSharedBufferExchanged = true;

    private final Handler mFrameHandler = new Handler(Looper.getMainLooper());
    private final AtomicReference<WeakReference<View>> mFrameTarget = new AtomicReference<>(new WeakReference<>(null));
    private final Runnable mFrameInvalidationRunnable = new Runnable() {
        @Override
        public void run() {
            if (!mCaptureThreadKeepAlive) {
                mFrameTarget.set(new WeakReference<>(null));
                return;
            }

            View target = mFrameTarget.get().get();
            if (target == null || !target.isShown()) {
                mFrameHandler.postDelayed(this, frameDelayMillis());
                return;
            }

            target.postInvalidateOnAnimation();
            mFrameHandler.postDelayed(this, frameDelayMillis());
        }
    };

    public void initParam(int viewWidth, int viewHeight, int texWidth, int texHeight, int screenWidth, int screenHeight, boolean isVulkan, CaptureMode captureMode) {
        mResState.view.update(viewWidth, viewHeight);
        mResState.tex.update(texWidth, texHeight);
        mResState.screen.update(screenWidth, screenHeight);
        mIsVulkan = isVulkan;
        mCaptureMode = captureMode;
    }

    public void abortCaptureThread() {
        stopFrameInvalidation();
    }

    public void ReleaseSharedTexture() {
        //Log.i(TAG, "release (start)");
        mHwbTexID = null;
        if (mSharedTexture != null) {
            mSharedTexture.release();
            mSharedTexture = null;
        }
        if (mSharedBuffer != null) {
            mSharedBuffer.close();
            mSharedBuffer = null;
        }
        //Log.i(TAG, "release (end)");
    }

    public void UpdateSharedTexture() {
        if (mViewToBufferRenderer instanceof ViewToHWBRenderer) {
            HardwareBuffer sharedBuffer = ((ViewToHWBRenderer) mViewToBufferRenderer).getHardwareBuffer();

            if (sharedBuffer == null) return;

            if (mSharedBuffer == sharedBuffer) {
                mSharedTexture.updateUnityTexture();
                return;
            }

            ReleaseSharedTexture();

            SharedTexture sharedTexture = new SharedTexture(sharedBuffer, mIsVulkan);

            mHwbTexID = new long[1];
            mHwbTexID[0] = sharedTexture.getPlatformTexture();

            mSharedTexture = sharedTexture;
            mSharedBuffer = sharedBuffer;

            mIsSharedBufferExchanged = false;
        }
    }

    public boolean ContentExists() {
        return mViewToBufferRenderer.contentExists();
    }

    public byte[] GetFrameBuffer() {
        if (mViewToBufferRenderer instanceof ViewToPBORenderer)
            return ((ViewToPBORenderer) mViewToBufferRenderer).getPixelBuffer();
        return new byte[0];
    }

    public void SetFps(int fps) {
        mFps = fps;
        restartFrameInvalidationIfNeeded();
    }

    /**
     * Return the texture pointer of the WebView frame
     * (NOTE: In Vulkan, the VkImage pointer returned by this function could not be used for UpdateExternalTexture. This issue has not been fixed).
     *
     * @return texture pointer of the WebView frame (Vulkan: VkImage, OpenGLES: TexID)
     */
    public long GetPlatformTextureID() {
        if (mHwbTexID == null) return 0;
        return mHwbTexID[0];
    }

    public void SetUnityTextureID(long unityTexID) {
        if (mSharedTexture != null) mSharedTexture.setUnityTexture(unityTexID);
    }

    public abstract void SetSurface(Object surfaceObj, int width, int height);

    public abstract void RemoveSurface();

    public abstract void Dispose();

    public void Resize(int texWidth, int texHeight, int viewWidth, int viewHeight) {
        if (mViewToBufferRenderer != null) {
            mResState.tex.update(texWidth, texHeight);
            mViewToBufferRenderer.setTextureResolution(mResState.tex.x, mResState.tex.y);
            mViewToBufferRenderer.requestResizeTex();
            mViewToBufferRenderer.disable();
        }

        Activity a = UnityPlayer.currentActivity;
        if (a == null) return;
        a.runOnUiThread(() -> {
            mResState.view.update(viewWidth, viewHeight);
            updateRootLayoutSize(mResState.view.x, mResState.view.y);
        });
    }
//
    public void ResizeTex(int texWidth, int texHeight) {
        if (mViewToBufferRenderer != null) {
            mResState.tex.update(texWidth, texHeight);
            mViewToBufferRenderer.setTextureResolution(mResState.tex.x, mResState.tex.y);
            mViewToBufferRenderer.requestResizeTex();
        }
    }

    public void ResizeView(int viewWidth, int viewHeight) {
        if (mViewToBufferRenderer != null) mViewToBufferRenderer.disable();

        Activity a = UnityPlayer.currentActivity;
        if (a == null) return;
        a.runOnUiThread(() -> {
            mResState.view.update(viewWidth, viewHeight);
            updateRootLayoutSize(mResState.view.x, mResState.view.y);
        });
    }

    /**
     * Test
     */
    public void RenderContent2TmpSurface() {
        Activity a = UnityPlayer.currentActivity;
        a.runOnUiThread(() -> {
            SurfaceView surfaceView = new SurfaceView(a);
            surfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
                @Override
                public void surfaceCreated(@NonNull SurfaceHolder holder) {
                    Log.e(TAG, "[RenderContent2TmpSurface] surfaceCreated");
                }

                @Override
                public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
                    SetSurface(holder.getSurface(), width, height);
                    Log.e(TAG, "[RenderContent2TmpSurface] surfaceChanged");
                }

                @Override
                public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
                    RemoveSurface();
                    Log.e(TAG, "[RenderContent2TmpSurface] surfaceDestroyed");
                }
            });
            a.addContentView(surfaceView, new RelativeLayout.LayoutParams(mResState.view.x, mResState.view.y));

            Handler handler = new Handler(Looper.getMainLooper());
            Runnable pump = new Runnable() {
                @Override
                public void run() {
                    if (mDisposed || !surfaceView.isAttachedToWindow()) {
                        return;
                    }
                    surfaceView.postInvalidateOnAnimation();
                    handler.postDelayed(this, frameDelayMillis());
                }
            };
            handler.post(pump);
        });
    }

    protected void startFrameInvalidation(@NonNull View target) {
        synchronized (mCaptureThreadMutex) {
            mCaptureThreadKeepAlive = true;
            mFrameTarget.set(new WeakReference<>(target));
            mFrameHandler.removeCallbacks(mFrameInvalidationRunnable);
            mFrameHandler.post(mFrameInvalidationRunnable);
        }
    }

    protected void stopFrameInvalidation() {
        synchronized (mCaptureThreadMutex) {
            mCaptureThreadKeepAlive = false;
            mFrameHandler.removeCallbacks(mFrameInvalidationRunnable);
            mFrameTarget.set(new WeakReference<>(null));
        }
    }

    private long frameDelayMillis() {
        return 1000L / Math.max(1, mFps);
    }

    private void restartFrameInvalidationIfNeeded() {
        if (!mCaptureThreadKeepAlive) {
            return;
        }
        mFrameHandler.removeCallbacks(mFrameInvalidationRunnable);
        mFrameHandler.post(mFrameInvalidationRunnable);
    }

    private void updateRootLayoutSize(int width, int height) {
        if (mRootLayout == null) {
            return;
        }
        ViewGroup.LayoutParams layoutParams = mRootLayout.getLayoutParams();
        if (layoutParams == null) {
            layoutParams = new RelativeLayout.LayoutParams(width, height);
        } else {
            layoutParams.width = width;
            layoutParams.height = height;
        }
        mRootLayout.setLayoutParams(layoutParams);
    }
}