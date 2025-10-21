package com.truffleco.webkit.chromium;

public class JavascriptMethods {
    public static final String INJECTOR_JS =
            "(function(){\n" +
                    "  if (window.__vm) return; // already installed\n" +
                    "  const clamp=(v,min,max)=>v<min?min:(v>max?max:v);\n" +
                    "  const getTarget=(x,y)=>document.elementFromPoint(x,y)||document.body;\n" +
                    "  const vm={\n" +
                    "    x: innerWidth/2, y: innerHeight/2,\n" +
                    "    lastX: null, lastY: null,\n" +
                    "    lastTarget: null,\n" +
                    "    cursorHidden: false,\n" +
                    "    setPos: function(x,y){ this.x=clamp(x,0,innerWidth-1); this.y=clamp(y,0,innerHeight-1); },\n" +
                    "    // Dispatch a bubbling mouse event at current (x,y)\n" +
                    "    _dispatchMouse: function(type, init){\n" +
                    "      const t = getTarget(this.x, this.y);\n" +
                    "      const ev = new MouseEvent(type, Object.assign({\n" +
                    "        bubbles:true, cancelable:true, view:window,\n" +
                    "        clientX:this.x, clientY:this.y,\n" +
                    "        screenX:this.x, screenY:this.y,\n" +
                    "      }, init||{}));\n" +
                    "      // Best-effort: provide movementX/Y for listeners that read them.\n" +
                    "      const dx=(this.lastX==null?0:this.x-this.lastX);\n" +
                    "      const dy=(this.lastY==null?0:this.y-this.lastY);\n" +
                    "      try{ Object.defineProperty(ev,'movementX',{value:dx}); }catch(e){}\n" +
                    "      try{ Object.defineProperty(ev,'movementY',{value:dy}); }catch(e){}\n" +
                    "      this.lastX=this.x; this.lastY=this.y;\n" +
                    "      t.dispatchEvent(ev);\n" +
                    "    },\n" +
                    "    move: function(dx,dy,buttons){ this.setPos(this.x+dx,this.y+dy); this._dispatchMouse('mousemove',{ buttons: (buttons|0) }); },\n" +
                    "    lock: function(){ if(this.cursorHidden) return; document.documentElement.style.cursor='none'; this.cursorHidden=true; },\n" +
                    "    unlock: function(){ if(!this.cursorHidden) return; document.documentElement.style.cursor=''; this.cursorHidden=false; }\n" +
                    "  };\n" +
                    "  window.__vm = vm; // expose for Android calls\n" +
                    "})();";

}
