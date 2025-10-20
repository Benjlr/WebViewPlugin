package com.truffleco.webkit;

import com.truffleco.util.Common.*;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Queue;

public class Common {
    public static class AsyncResult extends JSONSerialisable {
        public static class Manager {
            private final HashMap<Integer, AsyncResult> mResults = new HashMap<>();
            private final Queue<Integer> mIdAvails = new ArrayDeque<>();

            public Manager() {
                for (int i = 0; i < 10; i++) mIdAvails.add(i);
            }

            public int request() {
                if (mIdAvails.isEmpty()) return -1;
                Integer id = mIdAvails.poll();
                if (id != null) return id;
                return -1;
            }

            public void post(AsyncResult result, int status) {
                int id = result.id;
                if (mIdAvails.contains(id)) return;  // This result id is not valid.

                if (!mResults.containsKey(id)) {
                    result.status = status;
                    mIdAvails.add(id);
                    mResults.put(id, result);
                }
            }

            public AsyncResult get(int id) {
                if (!mResults.containsKey(id)) return null;
                AsyncResult result = mResults.get(id);
                mResults.remove(id);
                return result;
            }
        }

        public static class Status {
            public static final int WAITING = 0;
            public static final int FAILED = 1;
            public static final int CANCEL = 2;
            public static final int COMPLETE = 3;
        }

        public static final String KEY_ID = "id";
        public static final String KEY_STATUS = "status";
        public static final String KEY_INT_VALUE = "i";
        public static final String KEY_BOOL_VALUE = "b";
        public static final String KEY_DOUBLE_VALUE = "d";
        public static final String KEY_STRING_VALUE = "s";

        public int id;
        public int status = Status.WAITING;

        public int i;
        public double d;
        public boolean b;
        public String s = "";

        public AsyncResult() {
        }

        public AsyncResult(int id, int i) {
            this.id = id;
            this.i = i;
        }

        public AsyncResult(int id, double d) {
            this.id = id;
            this.d = d;
        }

        public AsyncResult(int id, boolean b) {
            this.id = id;
            this.b = b;
        }

        public AsyncResult(int id, String s) {
            this.id = id;
            this.s = s == null ? this.s : s;
        }

        @Override
        public JSONObject toJSON() {
            JSONObject jo = new JSONObject();
            try {
                jo.put(KEY_ID, id);
                jo.put(KEY_STATUS, status);
                jo.put(KEY_INT_VALUE, i);
                jo.put(KEY_BOOL_VALUE, b);
                jo.put(KEY_DOUBLE_VALUE, d);
                jo.put(KEY_STRING_VALUE, s);
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
            return jo;
        }

        @Override
        public void overwriteJSON(JSONObject jo) {
            try {
                this.id = jo.getInt(KEY_ID);
                this.status = jo.getInt(KEY_STATUS);
                this.i = jo.getInt(KEY_INT_VALUE);
                this.b = jo.getBoolean(KEY_BOOL_VALUE);
                this.d = jo.getDouble(KEY_DOUBLE_VALUE);
                this.s = jo.getString(KEY_STRING_VALUE);
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static class ResolutionState {
        public Vector2Int view = new Vector2Int();
        public Vector2Int tex = new Vector2Int();
        public Vector2Int screen = new Vector2Int();
    }

    public static class JSUtil {
        public static String toVariable(String name, String value) {
            return "var " + name + " = " + "'" + value + "';\n";
        }

        public static String toVariable(String name, int value) {
            return "var " + name + " = " + value + ";\n";
        }

        public static String toVariable(String name, float value) {
            return "var " + name + " = " + value + ";\n";
        }
    }

    public static class PageGoState {
        public boolean canGoBack = false;
        public boolean canGoForward = false;

        public void update(boolean canGoBack, boolean canGoForward) {
            this.canGoBack = canGoBack;
            this.canGoForward = canGoForward;
        }
    }

    public static class SessionState {
        public String loadUrl;
        public String actualUrl;
        public String userAgent;
    }

    public static class Vector2Int {
        public int x;
        public int y;

        public void update(int x, int y) {
            this.x = x;
            this.y = y;
        }

        public Vector2Int() {
        }

        public Vector2Int(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }

    public static class EventCallback {
        public enum Type {
            Raw, OnPageStart, OnPageFinish, OnDownload, OnDownloadStart, OnDownloadError, OnDownloadFinish, OnDialog,
        }

        public static class Message extends JSONSerialisable {
            public static final String KEY_TYPE = "type";
            public static final String KEY_PAYLOAD = "payload";

            public Type type;
            public String payload;

            public Message(Type type, String payload) {
                this.type = type;
                this.payload = payload;
            }

            public Message() {
            }

            // https://stackoverflow.com/a/24322913/22575350
            @Override
            public JSONObject toJSON() {
                JSONObject jo = new JSONObject();
                try {
                    jo.put(KEY_TYPE, type.ordinal());
                    jo.put(KEY_PAYLOAD, payload);
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
                return jo;
            }

            @Override
            public void overwriteJSON(JSONObject jo) {
                try {
                    this.type = Type.values()[jo.getInt(KEY_TYPE)];
                    this.payload = jo.getString(KEY_PAYLOAD);
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }
}
