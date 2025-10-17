// File: com/tlab/webkit/chromium/UnityConnect.java
package com.tlab.webkit.chromium;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Message;
import android.util.Log;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.PointerIcon;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.webkit.ConsoleMessage;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.HttpAuthHandler;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.SslErrorHandler;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import com.tlab.webkit.Common;
import com.tlab.webkit.CursorCapture;
import com.tlab.webkit.IBrowser;
import com.tlab.webkit.Common.*;
import com.tlab.widget.AlertDialog;
import com.unity3d.player.UnityPlayer;

import java.nio.ByteBuffer;
import java.util.Hashtable;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Pattern;

public class UnityConnect extends OffscreenBrowser implements IBrowser {
    private WebView mWebView;
    private View mVideoView;
    private final Map<String, ByteBuffer> mJSPrivateBuffer = new Hashtable<>();
    private final Map<String, ByteBuffer> mJSPublicBuffer = new Hashtable<>();
    private static final String TAG = "AreDesk (Chromium)";
    private void runOnActivityThread(String failureMessage, Consumer<Activity> task) {
        Activity activity = UnityPlayer.currentActivity;
        if (activity == null) {
            Log.w(TAG, failureMessage);
            return;
        }
        activity.runOnUiThread(() -> task.accept(activity));
    }
    private void runOnActivityThread(Consumer<Activity> task) {
        runOnActivityThread("Unity activity is not available; ignoring request", task);
    }
    private void withWebView(Consumer<WebView> task) {
        runOnActivityThread(activity -> {
            WebView webView = mWebView;
            if (webView == null) {
                Log.w(TAG, "WebView is not initialized; ignoring request");
                return;
            }
            task.accept(webView);
        });
    }
    private void disposeOnUiThread() {
        WebView webView = mWebView;
        if (webView == null) {
            mDisposed = true;
            return;
        }

        abortCaptureThread();

        webView.stopLoading();
        if (mVideoView != null) {
            webView.removeView(mVideoView);
        }
        mVideoView = null;
        webView.destroy();
        mWebView = null;
        mView = null;

        if (mCaptureLayout != null) {
            mCaptureLayout.removeAllViews();
        }
        if (mRootLayout != null) {
            ViewParent parent = mRootLayout.getParent();
            if (parent instanceof ViewGroup) {
                ((ViewGroup) parent).removeView(mRootLayout);
            }
            mRootLayout = null;
        }
        mCaptureLayout = null;
        mGlSurfaceView = null;

        mDisposed = true;
    }
    private void disposeWithoutActivity() {
        if (mCaptureLayout != null) {
            mCaptureLayout.removeAllViews();
        }
        mWebView = null;
        mView = null;
        mRootLayout = null;
        mCaptureLayout = null;
        mGlSurfaceView = null;
        mVideoView = null;
        mDisposed = true;
    }
    public class JSInterface {
        @JavascriptInterface
        public void postResult(final int id, final String result) {
            mAsyncResult.post(new AsyncResult(id, result), AsyncResult.Status.COMPLETE);
        }
        @JavascriptInterface
        public void postResult(final int id, final int result) {
            mAsyncResult.post(new AsyncResult(id, result), AsyncResult.Status.COMPLETE);
        }
        @JavascriptInterface
        public void postResult(final int id, final double result) {
            mAsyncResult.post(new AsyncResult(id, result), AsyncResult.Status.COMPLETE);
        }
        @JavascriptInterface
        public void postResult(final int id, final boolean result) {
            mAsyncResult.post(new AsyncResult(id, result), AsyncResult.Status.COMPLETE);
        }

        @JavascriptInterface
        public void unitySendMessage(String go, String method, String message) {
            UnityPlayer.UnitySendMessage(go, method, message);
        }

        @JavascriptInterface
        public void unityPostMessage(String message) {
            mUnityPostMessageQueue.add(new EventCallback.Message(EventCallback.Type.Raw, message));
        }

        @JavascriptInterface
        public boolean malloc(String key, int bufferSize) {
            if (!mJSPublicBuffer.containsKey(key)) {
                mJSPublicBuffer.put(key, ByteBuffer.allocate(bufferSize));
                return true;
            }
            return false;
        }

        @JavascriptInterface
        public void free(String key) { mJSPublicBuffer.remove(key); }

        @JavascriptInterface
        public void write(String key, byte[] bytes) {
            if (mJSPublicBuffer.containsKey(key)) {
                ByteBuffer buf = mJSPublicBuffer.get(key);
                if (buf != null) buf.put(bytes, 0, bytes.length);
            }
        }

        @JavascriptInterface
        public boolean _malloc(String url, int bufferSize) {
            if (!mJSPrivateBuffer.containsKey(url)) {
                mJSPrivateBuffer.put(url, ByteBuffer.allocate(bufferSize));
                return true;
            }
            return false;
        }

        @JavascriptInterface
        public void _free(String key) { mJSPrivateBuffer.remove(key); }

        @JavascriptInterface
        public void _write(String key, byte[] bytes) {
            if (mJSPrivateBuffer.containsKey(key)) {
                ByteBuffer buf = mJSPrivateBuffer.get(key);
                if (buf != null) buf.put(bytes, 0, bytes.length);
            }
        }

    }

    // ===== Init =====
    @SuppressLint("SetJavaScriptEnabled")
    public void InitNativePlugin(int webWidth, int webHeight,
                                 int texWidth, int texHeight,
                                 int screenWidth, int screenHeight,
                                 String url, boolean isVulkan, int captureMode) {
        if (webWidth <= 0 || webHeight <= 0) return;

        mSessionState.loadUrl = url;

        runOnActivityThread("Unity activity is not available; cannot initialise browser", activity -> {
            initParam(webWidth, webHeight, texWidth, texHeight, screenWidth, screenHeight, isVulkan, OffscreenBrowser.CaptureMode.values()[captureMode]);
            init();

            if (mWebView == null) {
                mWebView = new WebView(activity);
                mView = mWebView;
            }

            mWebView.setWebViewClient(new WebViewClient() {

                @Override
                public void onReceivedHttpAuthRequest(WebView view, final HttpAuthHandler handler, final String host, final String realm) {
                    String userName = null, userPass = null;
                    if (handler.useHttpAuthUsernamePassword() && view != null) {
                        String[] haup = view.getHttpAuthUsernamePassword(host, realm);
                        if (haup != null && haup.length == 2) {
                            userName = haup[0];
                            userPass = haup[1];
                        }
                    }
                    if (userName != null && userPass != null) handler.proceed(userName, userPass);
                    else showHttpAuthDialog(handler, host, realm);
                }

                @Override
                public void onPageStarted(WebView view, String url, Bitmap favicon) {
                    if (mWebView != null) mPageGoState.update(mWebView.canGoBack(), mWebView.canGoForward());
                    mUnityPostMessageQueue.add(new EventCallback.Message(EventCallback.Type.OnPageStart, url));
                }

                @Override
                public void onPageFinished(WebView view, String url) {
                    if (mWebView != null) mPageGoState.update(mWebView.canGoBack(), mWebView.canGoForward());
                    mSessionState.actualUrl = url;
                    mUnityPostMessageQueue.add(new EventCallback.Message(EventCallback.Type.OnPageFinish, url));
                }

                @SuppressLint("WebViewClientShouldOverrideUrlLoading")
                @Override
                public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                    Uri uri = request.getUrl();
                    String next = uri.toString();

                    if (mWebView != null) mPageGoState.update(mWebView.canGoBack(), mWebView.canGoForward());

                    if (mIntentFilters != null) {
                        for (String intentFilter : mIntentFilters) {
                            if (Pattern.compile(intentFilter).matcher(next).matches()) {
                                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(next));
                                view.getContext().startActivity(intent);
                                return true;
                            }
                        }
                    }

                    if (next.startsWith("http://") || next.startsWith("https://")
                            || next.startsWith("file://") || next.startsWith("javascript:")) {
                        return false; // let WebView handle it
                    } else if (next.startsWith("unity:")) {
                        // handle custom scheme here if you like
                        return true;
                    }

                    return true;
                }

                @SuppressLint("WebViewClientOnReceivedSslError")
                @Override
                public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                    AlertDialog.Init dialog = new AlertDialog.Init(AlertDialog.Init.Reason.ERROR, "Ssl Error", "Your connection is not private");
                    dialog.setPositive("Enter", selected -> handler.proceed());
                    dialog.setNegative("Back to safety", selected -> handler.cancel());
                    if (mOnDialogResult != null) mOnDialogResult.dismiss();
                    mOnDialogResult = dialog.getOnResultListener();
                    mUnityPostMessageQueue.add(new EventCallback.Message(EventCallback.Type.OnDialog, dialog.marshall()));
                }

                @Override
                public void onReceivedError(WebView view, WebResourceRequest request, android.webkit.WebResourceError error) {
                    super.onReceivedError(view, request, error);
                }

                @Override
                public void onLoadResource(WebView view, String url) {
                    if (mWebView != null) mPageGoState.update(mWebView.canGoBack(), mWebView.canGoForward());
                }
            });

            mWebView.setWebChromeClient(new WebChromeClient() {
                @Override public boolean onCreateWindow(WebView view, boolean dialog, boolean userGesture, Message resultMsg) { return false; }
                @Override public boolean onConsoleMessage(ConsoleMessage cm) {
                    Log.d(TAG, cm.message() + " -- From line " + cm.lineNumber() + " of " + cm.sourceId());
                    return true;
                }
                @Override public void onShowCustomView(View view, CustomViewCallback callback) {
                    super.onShowCustomView(view, callback);
                    mVideoView = view;
                    mWebView.addView(mVideoView, new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                }
                @Override public void onHideCustomView() {
                    super.onHideCustomView();
                    if (mVideoView != null) mWebView.removeView(mVideoView);
                    mVideoView = null;
                }
                @Override public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
                    Log.i(TAG, "File chooser requested but disabled for this build; reporting no selection");
                    filePathCallback.onReceiveValue(null);
                    return true;
                }
                @Override public void onPermissionRequest(final PermissionRequest request) {
                    final String[] res = request.getResources();
                    for (String r : res) {
                        if (r.equals(PermissionRequest.RESOURCE_VIDEO_CAPTURE)
                                || r.equals(PermissionRequest.RESOURCE_AUDIO_CAPTURE)
                                || r.equals(PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID)) {
                            request.grant(res);
                            break;
                        }
                    }
                }
            });

            mWebView.setOnScrollChangeListener((v, x, y, oldX, oldY) -> mScrollState.update(x, y));

            // --- Basic WebView config (unchanged) ---
            mWebView.setInitialScale(100);
            mWebView.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
            mWebView.clearCache(true);
            mWebView.setLongClickable(false);
            mWebView.setVisibility(View.VISIBLE);
            mWebView.setVerticalScrollBarEnabled(true);
            mWebView.setBackgroundColor(0x00000000);
            mWebView.addJavascriptInterface(new JSInterface(), "tlab");

            WebSettings webSettings = mWebView.getSettings();
            webSettings.setLoadWithOverviewMode(true);
            webSettings.setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK);
            webSettings.setUseWideViewPort(true);
            webSettings.setSupportZoom(true);
            webSettings.setSupportMultipleWindows(true);
            webSettings.setBuiltInZoomControls(false);
            webSettings.setDisplayZoomControls(true);
            webSettings.setJavaScriptEnabled(true);
            webSettings.setAllowContentAccess(true);
            webSettings.setAllowFileAccess(true);
            webSettings.setMediaPlaybackRequiresUserGesture(false);
            if (mSessionState.userAgent != null && !mSessionState.userAgent.isEmpty()) {
                webSettings.setUserAgentString(mSessionState.userAgent);
            }
            webSettings.setDefaultTextEncodingName("utf-8");
            webSettings.setDatabaseEnabled(true);
            webSettings.setDomStorageEnabled(true);

            mCaptureLayout.addView(mWebView, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            if (mSessionState.loadUrl != null) LoadUrl(mSessionState.loadUrl);

            CursorCapture.browserView = mWebView;
            mInitialized = true;
        });
    }
    private void showHttpAuthDialog(final HttpAuthHandler handler, final String host, final String realm) {
        Activity activity = UnityPlayer.currentActivity;
        if (activity == null) {
            Log.w(TAG, "Unable to show HTTP auth dialog: Unity activity was null");
            handler.cancel();
            return;
        }

        final android.app.AlertDialog.Builder dialog = new android.app.AlertDialog.Builder(activity);
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);

        dialog.setTitle("Enter the password").setCancelable(false);
        final EditText etUserName = new EditText(activity); etUserName.setWidth(100); layout.addView(etUserName);
        final EditText etUserPass = new EditText(activity); etUserPass.setWidth(100); layout.addView(etUserPass);
        dialog.setView(layout);

        dialog.setPositiveButton("OK", (d, w) -> {
            String userName = etUserName.getText().toString();
            String userPass = etUserPass.getText().toString();
            if (mWebView != null) {
                mWebView.setHttpAuthUsernamePassword(host, realm, userName, userPass);
            }
            handler.proceed(userName, userPass);
        });
        dialog.setNegativeButton("Cancel", (d, w) -> handler.cancel());
        dialog.create().show();
    }
    @Override
    public void Dispose() {
        super.Dispose();
        ReleaseSharedTexture();

        Activity activity = UnityPlayer.currentActivity;
        if (activity == null) {
            disposeWithoutActivity();
            return;
        }

        activity.runOnUiThread(this::disposeOnUiThread);
    }
    public void EvaluateJS(String js) {
        withWebView(webView -> {
            if (!webView.getSettings().getJavaScriptEnabled()) {
                Log.w(TAG, "JavaScript execution requested but disabled; ignoring call");
                return;
            }
            webView.evaluateJavascript("(function(){" + js + "})();", null);
        });
    }
    public int EvaluateJSForResult(String varNameOfResultId, String js) {
        int id = mAsyncResult.request();
        if (id == -1) return -1;
        EvaluateJS(Common.JSUtil.toVariable(varNameOfResultId, id) + js);
        return id;
    }
    public void SetUserAgent(final String ua, final boolean reload) {
        withWebView(webView -> {
            try {
                webView.getSettings().setUserAgentString(ua);
                if (reload) {
                    webView.reload();
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to set custom user agent", e);
            }
        });
        mSessionState.userAgent = ua;
    }
    public int GetUserAgent() {
        int id = mAsyncResult.request();
        if (id == -1) return -1;
        runOnActivityThread(activity -> {
            WebView webView = mWebView;
            if (webView == null) {
                return;
            }
            mSessionState.userAgent = webView.getSettings().getUserAgentString();
            mAsyncResult.post(new AsyncResult(id, mSessionState.userAgent), AsyncResult.Status.COMPLETE);
        });
        return id;
    }
    public String GetUrl() { return mSessionState.actualUrl; }
    public void LoadUrl(String url) {
        runOnActivityThread(activity -> {
            WebView webView = mWebView;
            if (webView == null) {
                return;
            }

            if (mIntentFilters != null) {
                for (String intentFilter : mIntentFilters) {
                    if (Pattern.compile(intentFilter).matcher(url).matches()) {
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                        webView.getContext().startActivity(intent);
                        mSessionState.loadUrl = url;
                        return;
                    }
                }
            }

            if (!url.startsWith("http://") && !url.startsWith("https://"))
                mSessionState.loadUrl = "http://" + url;
            else mSessionState.loadUrl = url;

            webView.loadUrl(mSessionState.loadUrl);
        });
    }
    public void RefreshPage() {
        withWebView(WebView::reload);
    }
    public void GoBack() {
        withWebView(webView -> { if (mPageGoState.canGoBack) webView.goBack(); });
    }
    public void GoForward() {
        withWebView(webView -> { if (mPageGoState.canGoForward) webView.goForward(); });
    }
    public byte[] GetJSBuffer(String id) {
        if (mJSPublicBuffer.containsKey(id)) {
            ByteBuffer buf = mJSPublicBuffer.get(id);
            return (buf != null) ? buf.array() : null;
        }
        return null;
    }
    public void LoadHtml(final String html, final String baseURL) {
        withWebView(webView -> webView.loadDataWithBaseURL(baseURL, html, "text/html", "UTF8", null));
    }
    public void PageUp(boolean top) {
        withWebView(webView -> webView.pageUp(top));
    }
    public void PageDown(boolean bottom) {
        withWebView(webView -> webView.pageDown(bottom));
    }
    public void ClearCash(boolean includeDiskFiles) {
        withWebView(webView -> webView.clearCache(includeDiskFiles));
    }
    public void ClearHistory() {
        withWebView(WebView::clearHistory);
    }
    public void ZoomIn() {
        withWebView(webView -> webView.zoomIn());
    }
    public void zoomOut() {
        withWebView(webView -> webView.zoomOut());
    }
    public void ClearCookie() {
        withWebView(webView -> {
            CookieManager cookieManager = CookieManager.getInstance();
            cookieManager.removeAllCookies(null);
            cookieManager.flush();
        });
    }
}
