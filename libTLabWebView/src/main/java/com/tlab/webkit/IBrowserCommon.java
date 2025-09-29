package com.tlab.webkit;

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
}
