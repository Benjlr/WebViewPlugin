package com.tlab.webkit;

public interface IBrowser {

    /**
     * Run javascript on the current web page.
     *
     * @param js javascript
     */
    void EvaluateJS(String js);

    /**
     * Update userAgent with the given userAgent string.
     *
     * @param ua     UserAgent string
     * @param reload If true, reload web page when userAgent is updated.
     */
    void SetUserAgent(final String ua, final boolean reload);

    /**
     * Capture current userAgent async.
     */
    int GetUserAgent();

    /**
     * Get current url that the WebView instance is loading
     *
     * @return Current url that the WebView instance is loading
     */
    String GetUrl();

    /**
     * Loads the given URL.
     *
     * @param url The URL of the resource to load.
     */
    void LoadUrl(String url);

    /**
     * Refreshes the current page.
     *
     */
    void RefreshPage();

    /**
     * Goes back in the history of this WebView.
     */
    void GoBack();

    /**
     * Goes forward in the history of this WebView.
     */
    void GoForward();
}
