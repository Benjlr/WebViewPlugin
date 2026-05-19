package com.truffleco.webkit.chromium;

public class JavascriptMethods {

    public static final String INJECTOR_JS =
            "(function(){\n" +
                    "  if (window.__vm) return;\n" +
                    "  var cachedTarget = document.querySelector('video') || document.querySelector('canvas') || document.documentElement;\n" +
                    "  const getTarget = () => {\n" +
                    "    if (cachedTarget.isConnected) return cachedTarget;\n" +
                    "    cachedTarget = document.querySelector('video') || document.querySelector('canvas') || document.documentElement;\n" +
                    "    return cachedTarget;\n" +
                    "  };\n" +
                    "  const clamp = (v, min, max) => v < min ? min : (v > max ? max : v);\n" +
                    "  const vm = {\n" +
                    "    x: innerWidth / 2, y: innerHeight / 2,\n" +
                    "    rawX: innerWidth / 2, rawY: innerHeight / 2,\n" +
                    "    cursorHidden: false,\n" +
                    "    _dispatchMouse: function(type, buttons, dx, dy) {\n" +
                    "      const t = getTarget();\n" +
                    "      t.dispatchEvent(new MouseEvent(type, {\n" +
                    "        bubbles: true, cancelable: true, view: window,\n" +
                    "        clientX: this.x, clientY: this.y,\n" +
                    "        screenX: this.x, screenY: this.y,\n" +
                    "        movementX: dx, movementY: dy,\n" +
                    "        buttons: buttons\n" +
                    "      }));\n" +
                    "    },\n" +
                    "    move: function(dx, dy, buttons) {\n" +
                    "      this.rawX += dx; this.rawY += dy;\n" +
                    "      this.x = clamp(this.rawX, 0, innerWidth - 1);\n" +
                    "      this.y = clamp(this.rawY, 0, innerHeight - 1);\n" +
                    "      this._dispatchMouse('mousemove', buttons, dx, dy);\n" +
                    "    },\n" +
                    "    lock: function() {\n" +
                    "      if (this.cursorHidden) return;\n" +
                    "      document.documentElement.style.cursor = 'none';\n" +
                    "      this.cursorHidden = true;\n" +
                    "    },\n" +
                    "    unlock: function() {\n" +
                    "      if (!this.cursorHidden) return;\n" +
                    "      document.documentElement.style.cursor = '';\n" +
                    "      this.cursorHidden = false;\n" +
                    "    }\n" +
                    "  };\n" +
                    "  window.__vm = vm;\n" +
                    "})();";
    public static final String VIEWPORT_STATIC =
            "(function(){\n" +
                    "  if (window.__viewportStaticInstalled) return; window.__viewportStaticInstalled = true;\n" +
                    "  // Remove any existing viewport tags\n" +
                    "  document.querySelectorAll('meta[name=\"viewport\"]').forEach(function(el){ el.remove(); });\n" +
                    "  var m = document.createElement('meta');\n" +
                    "  m.name = 'viewport';\n" +
                    "  m.content = 'width=device-width, initial-scale=1, minimum-scale=1, maximum-scale=1, user-scalable=no, viewport-fit=cover';\n" +
                    "  (document.head || document.documentElement).appendChild(m);\n" +
                    "})();";
    // Precomputed: injected on every page finish. Avoids string allocation per page load.
    public static final String INIT_JS = INJECTOR_JS + "\n" + VIEWPORT_STATIC;
}
