package com.truffleco.viewtobuffer;

import android.hardware.HardwareBuffer;
import android.opengl.GLES11Ext;
import android.opengl.GLES30;
import android.util.Log;

import com.robot9.shared.SharedTexture;

public class ViewToHWBRenderer extends ViewToBufferRenderer {

    private HardwareBuffer mSharedBuffer;

    private SharedTexture mSharedTexture;

    /**
     * FBO (pixel data on GPU) for EGL_IMAGE_KHR (Hardware Buffer)
     */

    private int[] mHwbFboID;
    private int[] mHwbFboTexID;

    @Override
    protected void destroyBuffer() {
        if (mHwbFboTexID != null) {
            GLES30.glDeleteTextures(mHwbFboTexID.length, mHwbFboTexID, 0);
            mHwbFboTexID = null;
        }

        if (mHwbFboID != null) {
            GLES30.glDeleteFramebuffers(mHwbFboID.length, mHwbFboID, 0);
            mHwbFboID = null;
        }

        if (mSharedTexture != null) {
            mSharedTexture.release();
            mSharedTexture = null;
        }
    }

    /**
     *
     */
    @Override
    protected void initBuffer() {
        mHwbFboID = new int[1];
        mHwbFboTexID = new int[1];

        mSharedTexture = new SharedTexture(mTexSize.x, mTexSize.y, false);
        mSharedBuffer = mSharedTexture.getHardwareBuffer();

        if (mSharedBuffer == null) {
            Log.e(TAG, "initBuffer failed: HardwareBuffer allocation returned null");
            return;
        }

        // Plugin returns long variable, but in OpenGL, texture id can be used by int, so cast here.
        mHwbFboTexID[0] = (int) mSharedTexture.getPlatformTexture();

        GLES30.glGenFramebuffers(1, mHwbFboID, 0);
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, mHwbFboID[0]);
        GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, mHwbFboTexID[0], 0);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0);
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0);
    }

    @Override
    public void CopySurfaceTextureToBuffer() {
        if (!mInitialized || mHwbFboID == null) return;

        GLES30.glUseProgram(mGlSamplerProgram);

        GLES30.glViewport(0, 0, mTexSize.x, mTexSize.y);
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, mSurfaceTextureID[0]);

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, mGlCubeID[0]);
        GLES30.glVertexAttribPointer(mGlSamplerPositionID, 2, GLES30.GL_FLOAT, false, 4 * 2, 0);
        GLES30.glEnableVertexAttribArray(mGlSamplerPositionID);

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, mGlTexCoordID[0]);
        GLES30.glVertexAttribPointer(mGlSamplerTexCoordID, 2, GLES30.GL_FLOAT, false, 4 * 2, 0);
        GLES30.glEnableVertexAttribArray(mGlSamplerTexCoordID);

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0);

        GLES30.glUniform1i(mGlSamplerTexID, 0);
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, mHwbFboID[0]);
        GLES30.glDisable(GLES30.GL_CULL_FACE);
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4);

        // glFenceSync + glClientWaitSync instead of glFinish().
        // glFinish() stalls the entire GPU pipeline — it blocks the CPU until every pending
        // GPU command across all contexts completes. A fence sync is scoped to this specific
        // draw, so the driver has more freedom to schedule other work in parallel.
        // GL_SYNC_FLUSH_COMMANDS_BIT ensures the sync is submitted even if the command
        // buffer hasn't been flushed yet (equivalent to calling glFlush first).
        // 2-second timeout is a hard upper bound; in practice this completes in <1 frame.
        long sync = GLES30.glFenceSync(GLES30.GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
        int waitResult = GLES30.glClientWaitSync(sync, GLES30.GL_SYNC_FLUSH_COMMANDS_BIT, 2_000_000_000L);
        GLES30.glDeleteSync(sync);
        if (waitResult == GLES30.GL_TIMEOUT_EXPIRED || waitResult == GLES30.GL_WAIT_FAILED) {
            Log.w(TAG, "glClientWaitSync: unexpected result " + waitResult + " — frame may be incomplete");
        }

        GLES30.glDisableVertexAttribArray(mGlSamplerPositionID);
        GLES30.glDisableVertexAttribArray(mGlSamplerTexCoordID);
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0);
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0);

        //Log.i(TAG, "[VHWBR] [CopySurfaceTextureToBuffer]");
    }

    public HardwareBuffer getHardwareBuffer() {
        return mSharedBuffer;
    }
}