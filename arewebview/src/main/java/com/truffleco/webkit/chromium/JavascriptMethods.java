package com.truffleco.webkit.chromium;

public class JavascriptMethods {
    public static final String INJECTOR_JS =
            "(function(){\n" +
                    "  if (window.__vm) return; // already installed\n" +
                    "  const clamp=(v,min,max)=>v<min?min:(v>max?max:v);\n" +
                    "  const getTarget=(x,y)=>document.elementFromPoint(x,y)||document.body;\n" +
                    "  const vm={\n" +
                    "    // Visible/client coords (clamped to viewport)\n" +
                    "    x: innerWidth/2,\n" +
                    "    y: innerHeight/2,\n" +
                    "    // Unbounded virtual coords for delta math\n" +
                    "    rawX: innerWidth/2,\n" +
                    "    rawY: innerHeight/2,\n" +
                    "    lastRawX: null,\n" +
                    "    lastRawY: null,\n" +
                    "    cursorHidden: false,\n" +
                    "    // Update visible coords by clamping raw\n" +
                    "    _syncVisibleFromRaw: function(){\n" +
                    "      this.x = clamp(this.rawX, 0, innerWidth - 1);\n" +
                    "      this.y = clamp(this.rawY, 0, innerHeight - 1);\n" +
                    "    },\n" +
                    "    // Dispatch a bubbling mouse event at current (x,y), with explicit movement\n" +
                    "    _dispatchMouse: function(type, init, mdx, mdy){\n" +
                    "      const t = getTarget(this.x, this.y);\n" +
                    "      const ev = new MouseEvent(type, Object.assign({\n" +
                    "        bubbles:true, cancelable:true, view:window,\n" +
                    "        clientX:this.x, clientY:this.y,\n" +
                    "        screenX:this.x, screenY:this.y\n" +
                    "      }, init||{}));\n" +
                    "      // Force movementX/Y to reflect raw deltas even if clientX/Y are clamped\n" +
                    "      try{ Object.defineProperty(ev,'movementX',{value: mdx||0}); }catch(e){}\n" +
                    "      try{ Object.defineProperty(ev,'movementY',{value: mdy||0}); }catch(e){}\n" +
                    "      // Optional debug\n" +
                    "      console.log('[vm] move dx=%o dy=%o raw=(%o,%o) vis=(%o,%o)', mdx||0, mdy||0, this.rawX, this.rawY, this.x, this.y);\n" +
                    "      t.dispatchEvent(ev);\n" +
                    "    },\n" +
                    "    // Public move: advance RAW by (dx,dy), clamp visible, emit deltas from RAW\n" +
                    "    move: function(dx, dy, buttons){\n" +
                    "      this.rawX += dx; this.rawY += dy;\n" +
                    "      const mdx = (this.lastRawX==null ? 0 : this.rawX - this.lastRawX);\n" +
                    "      const mdy = (this.lastRawY==null ? 0 : this.rawY - this.lastRawY);\n" +
                    "      this.lastRawX = this.rawX; this.lastRawY = this.rawY;\n" +
                    "      this._syncVisibleFromRaw();\n" +
                    "      this._dispatchMouse('mousemove', { buttons: (buttons|0) }, mdx, mdy);\n" +
                    "    },\n" +
                    "    // Lock/unlock just hide/show the cursor; you can keep your Android-side overlay\n" +
                    "    lock: function(){ if (this.cursorHidden) return; document.documentElement.style.cursor='none'; this.cursorHidden=true; },\n" +
                    "    unlock: function(){ if (!this.cursorHidden) return; document.documentElement.style.cursor=''; this.cursorHidden=false; }\n" +
                    "  };\n" +
                    "  window.__vm = vm; // expose for Android calls\n" +
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

}
