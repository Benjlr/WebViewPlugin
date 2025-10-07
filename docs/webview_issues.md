# WebView Stability and Usability Concerns

## Cross-thread data structures
- `BaseOffscreenBrowser` pushes WebView event callbacks into `mUnityPostMessageQueue`, which is a plain `ArrayDeque` shared with Unity-side polling via `DispatchMessageQueue()`. Access occurs on different threads without synchronization, so queue metadata can be corrupted or `poll()` can race with `size()` and return null, leading to crashes or lost messages. 【F:libTLabWebView/src/main/java/com/tlab/webkit/BaseOffscreenBrowser.java†L31-L51】
- The `Common.AsyncResult.Manager` backing async JS replies uses unsynchronized `HashMap`/`ArrayDeque` mutations; Android's main thread posts results while Unity reads them from native code. Under contention, IDs can be dropped or map state corrupted, producing hung JS callbacks or runtime exceptions. 【F:libTLabWebView/src/main/java/com/tlab/webkit/Common.java†L44-L75】

## JavaScript bridge memory pressure
- Pages can call the exposed `tlab.malloc`/`_malloc` helpers with any size, allocating heap `ByteBuffer`s that stick around until JavaScript explicitly frees them. A malicious or buggy page can repeatedly allocate without freeing and exhaust memory. 【F:libTLabWebView/src/main/java/com/tlab/webkit/chromium/UnityConnect.java†L139-L205】
- Subsequent `write` calls blindly `put()` the incoming bytes. If the caller writes more than the reserved capacity or reuses the buffer without resetting its position, the WebView throws `BufferOverflowException`, crashing the host. 【F:libTLabWebView/src/main/java/com/tlab/webkit/chromium/UnityConnect.java†L179-L205】

## URL handling usability/stability
- `LoadUrl` rewrites every string that is not already `http://` or `https://` by forcing an `http://` prefix, blocking legitimate schemes such as `about:blank`, `file:`, `data:`, `ws:` or in-app deep links and breaking navigation initiated from Unity. 【F:libTLabWebView/src/main/java/com/tlab/webkit/chromium/UnityConnect.java†L544-L567】
- `shouldOverrideUrlLoading` launches external intents for regex matches without `ActivityNotFoundException` handling; tapping a link whose target app is missing will crash the process. Any other non-HTTP scheme is consumed and dropped, so common actions like `mailto:` or `tel:` silently fail. 【F:libTLabWebView/src/main/java/com/tlab/webkit/chromium/UnityConnect.java†L260-L283】

These issues collectively threaten runtime stability, open the door to memory exhaustion from remote content, and limit how the WebView can be safely embedded in production builds.
