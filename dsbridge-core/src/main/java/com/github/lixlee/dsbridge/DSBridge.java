package com.github.lixlee.dsbridge;

import android.os.Handler;
import android.os.Looper;
import androidx.annotation.CallSuper;
import androidx.annotation.Keep;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.util.Log;
import android.webkit.JavascriptInterface;

import com.github.lixlee.dsbridge.internal.JsApiMethod;
import com.github.lixlee.dsbridge.internal.JsApiTarget;
import com.github.lixlee.dsbridge.internal.ReflectJsApiTarget;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import wendu.dsbridge.CompletionHandler;
import wendu.dsbridge.OnReturnValue;

/*
 * Created by lixlee on 2022/7/30
 */
@SuppressWarnings("unused")
public class DSBridge implements DSBridgeFacade {
    public static final String BRIDGE_NAME = "_dsbridge";
    private static final String LOG_TAG = "dsBridge";
    private static boolean isDebug = false;
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private final NamespaceObjects mNamespaceObjects = new NamespaceObjects(this);
    private final JsHandlerCalls mJsHandlerCalls = new JsHandlerCalls(this);
    private final JavascriptInterfaceObject mJavascriptInterfaceObject = new JavascriptInterfaceObject(this);
    @Nullable
    private JavascriptEvaluator mJavascriptEvaluator;
    private Internals mInternals;

    public DSBridge(@Nullable JavascriptEvaluator javascriptEvaluator) {
        mJavascriptEvaluator = javascriptEvaluator;
    }

    public static boolean isDebug() {
        return isDebug;
    }

    public static void setDebug(boolean debug) {
        isDebug = debug;
    }

    public static String[] parseNamespace(String method) {
        int pos = method.lastIndexOf('.');
        String namespace = "";
        if (pos != -1) {
            namespace = method.substring(0, pos);
            method = method.substring(pos + 1);
        }
        return new String[]{namespace, method};
    }

    public Handler getMainHandler() {
        return mMainHandler;
    }

    @NonNull
    public JavascriptInterfaceObject getJavascriptInterfaceObject() {
        return mJavascriptInterfaceObject;
    }

    public JavascriptEvaluator getJavascriptEvaluator() {
        return mJavascriptEvaluator;
    }

    public void setJavascriptEvaluator(JavascriptEvaluator javascriptEvaluator) {
        mJavascriptEvaluator = javascriptEvaluator;
    }

    @NonNull
    public Internals getInternals() {
        if (mInternals == null) {
            mInternals = new Internals();
        }
        return mInternals;
    }

    public void init() {
        addJavascriptObject(new InternalJavascriptObject(this), InternalJavascriptObject.NAMESPACE_DSB);
    }

    @CallSuper
    public void destroy() {
        mMainHandler.removeCallbacksAndMessages(null);
        setJavascriptEvaluator(null);
    }

    protected void runOnMainThread(Runnable runnable) {
        if (Looper.getMainLooper() == Looper.myLooper()) {
            runnable.run();
            return;
        }
        mMainHandler.post(runnable);
    }

    @Override
    public void evaluateJavascript(String script) {
        runOnMainThread(() -> {
            if (mJavascriptEvaluator != null) {
                mJavascriptEvaluator.evaluateJavascript(script);
            }
        });
    }

    @Override
    public <T> void callHandler(String method, @Nullable Object[] args, @Nullable OnReturnValue<T> handler) {
        mJsHandlerCalls.callHandler(method, args, handler);
    }

    @Override
    public void callHandler(String method, @Nullable Object[] args) {
        callHandler(method, args, null);
    }

    @Override
    public <T> void callHandler(String method, @Nullable OnReturnValue<T> handler) {
        callHandler(method, null, handler);
    }

    @Override
    public void hasJavascriptMethod(String handlerName, OnReturnValue<Boolean> existCallback) {
        callHandler("_hasJavascriptMethod", new Object[]{handlerName}, existCallback);
    }

    @Override
    public void addJavascriptObject(Object object, @Nullable String namespace) {
        if (object != null) {
            mNamespaceObjects.put(namespace != null ? namespace : "", object);
        }
    }

    @Override
    public void removeJavascriptObject(@Nullable String namespace) {
        mNamespaceObjects.remove(namespace != null ? namespace : "");
    }

    public static class NamespaceObjects {
        private final Map<String, JsApiTarget> mObjects = new HashMap<>();
        private final JavascriptEvaluator mJavascriptEvaluator;

        public NamespaceObjects(JavascriptEvaluator javascriptEvaluator) {
            mJavascriptEvaluator = javascriptEvaluator;
        }

        public void put(@Nullable String namespace, Object object) {
            mObjects.put(namespace != null ? namespace : "", ReflectJsApiTarget.wrap(object));
        }

        public void remove(@Nullable String namespace) {
            mObjects.remove(namespace != null ? namespace : "");
        }

        @Nullable
        public JsApiTarget get(@Nullable String namespace) {
            return mObjects.get(namespace != null ? namespace : "");
        }

        public boolean hasNativeMethod(Object args) throws JSONException {
            JSONObject jsonObject = (JSONObject) args;
            String methodName = jsonObject.getString("name").trim();
            String type = jsonObject.getString("type").trim();
            String[] ns = parseNamespace(methodName);
            JsApiTarget jsb = get(ns[0]);
            if (jsb == null) {
                return false;
            }
            JsApiMethod jsApiMethod = jsb.findMethod(ns[1]);
            if (jsApiMethod == null) {
                return false;
            }
            boolean asyn = jsApiMethod.isAsync();
            return "all".equals(type) || (asyn && "asyn".equals(type) || (!asyn && "syn".equals(type)));
        }

        private void PrintDebugInfo(String error) {
            Log.d(LOG_TAG, error);
            if (isDebug()) {
                evaluateJavascript(String.format("alert('%s')", "DEBUG ERR MSG:\\n" + error.replaceAll("\\'", "\\\\'")));
            }
        }

        public String callNative(String methodName, String argStr) {
            String error = "Js bridge  called, but can't find a corresponded " +
                    "JavascriptInterface object , please check your code!";
            String[] nameStr = parseNamespace(methodName.trim());
            methodName = nameStr[1];
            JsApiMethod method = null;
            JsApiTarget jsb = get(nameStr[0]);

            JSONObject ret = new JSONObject();
            try {
                ret.put("code", -1);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            if (jsb == null) {
                PrintDebugInfo(error);
                return ret.toString();
            }
            method = jsb.findMethod(nameStr[1]);
            if (method == null) {
                error = "Not find method \"" + methodName + "\" implementation! please check if the signature or namespace of the method is right ";
                PrintDebugInfo(error);
                return ret.toString();
            }
            Object arg = null;
            String callback = null;

            try {
                JSONObject args = new JSONObject(argStr);
                if (args.has("_dscbstub")) {
                    callback = args.getString("_dscbstub");
                }
                if (args.has("data")) {
                    arg = args.get("data");
                }
            } catch (JSONException e) {
                error = String.format("The argument of \"%s\" must be a JSON object string!", methodName);
                PrintDebugInfo(error);
                e.printStackTrace();
                return ret.toString();
            }

            Object retData;
            try {
                if (method.isAsync()) {
                    method.invoke(arg, new MyCompletionHandler(callback, mJavascriptEvaluator));
                } else {
                    retData = method.invoke(arg, null);
                    ret.put("code", 0);
                    ret.put("data", retData);
                    return ret.toString();
                }
            } catch (Exception e) {
                e.printStackTrace();
                error = String.format("Call failed：The parameter of \"%s\" in Java is invalid.", methodName);
                PrintDebugInfo(error);
                return ret.toString();
            }
            return ret.toString();
        }

        private void evaluateJavascript(String script) {
            if (mJavascriptEvaluator != null) {
                mJavascriptEvaluator.evaluateJavascript(script);
            }
        }
    }

    public static class JsHandlerCalls {
        private final JavascriptEvaluator mJavascriptEvaluator;
        private final Map<Integer, OnReturnValue<?>> handlerMap = new HashMap<>();
        private ArrayList<CallInfo> callInfoList;
        private int callID = 0;

        public JsHandlerCalls(JavascriptEvaluator javascriptEvaluator) {
            mJavascriptEvaluator = javascriptEvaluator;
        }

        public void resetCalls() {
            callInfoList = new ArrayList<>();
        }

        public <T> void callHandler(String method, @Nullable Object[] args, @Nullable OnReturnValue<T> handler) {
            CallInfo callInfo = new CallInfo(method, ++callID, args);
            if (handler != null) {
                handlerMap.put(callInfo.callbackId, handler);
            }

            if (callInfoList != null) {
                callInfoList.add(callInfo);
            } else {
                dispatchJavascriptCall(callInfo);
            }
        }

        @MainThread
        public void returnValue(Object args) {
            JSONObject jsonObject = (JSONObject) args;
            Object data = null;
            try {
                int id = jsonObject.getInt("id");
                boolean isCompleted = jsonObject.getBoolean("complete");
                OnReturnValue handler = handlerMap.get(id);
                if (jsonObject.has("data")) {
                    data = jsonObject.get("data");
                }
                if (handler != null) {
                    handler.onValue(data);
                    if (isCompleted) {
                        handlerMap.remove(id);
                    }
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        synchronized void dispatchStartupQueue() {
            if (callInfoList != null) {
                for (CallInfo info : callInfoList) {
                    dispatchJavascriptCall(info);
                }
                callInfoList = null;
            }
        }

        private void dispatchJavascriptCall(CallInfo info) {
            evaluateJavascript(String.format("window._handleMessageFromNative(%s)", info.toString()));
        }

        private void evaluateJavascript(String script) {
            if (mJavascriptEvaluator != null) {
                mJavascriptEvaluator.evaluateJavascript(script);
            }
        }

        private static class CallInfo {
            private String data;
            private int callbackId;
            private String method;

            CallInfo(String handlerName, int id, Object[] args) {
                if (args == null) args = new Object[0];
                data = new JSONArray(Arrays.asList(args)).toString();
                callbackId = id;
                method = handlerName;
            }

            @Override
            public String toString() {
                JSONObject jo = new JSONObject();
                try {
                    jo.put("method", method);
                    jo.put("callbackId", callbackId);
                    jo.put("data", data);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                return jo.toString();
            }
        }
    }

    static class MyCompletionHandler implements CompletionHandler<Object> {

        private final String jsCallback;
        private final JavascriptEvaluator mJavascriptEvaluator;

        public MyCompletionHandler(String jsCallback, JavascriptEvaluator javascriptEvaluator) {
            this.jsCallback = jsCallback;
            mJavascriptEvaluator = javascriptEvaluator;
        }

        @Override
        public void complete(Object retValue) {
            complete(retValue, true);
        }

        @Override
        public void complete() {
            complete(null, true);
        }

        @Override
        public void setProgressData(Object value) {
            complete(value, false);
        }

        private void complete(Object retValue, boolean complete) {
            String cb = this.jsCallback;
            if (cb == null || cb.equals("") || cb.equals("null") || cb.equals("undefined")) {
                return;
            }
            try {
                JSONObject ret = new JSONObject();
                ret.put("code", 0);
                ret.put("data", retValue);
                String script = String.format("%s(%s.data);", cb, ret.toString());
                if (complete) {
                    script += "delete window." + cb;
                }
                //Log.d(LOG_TAG, "complete " + script);
                evaluateJavascript(script);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        private void evaluateJavascript(String script) {
            if (mJavascriptEvaluator != null) {
                mJavascriptEvaluator.evaluateJavascript(script);
            }
        }
    }

    public class Internals {
        public void onBeforeLoadUrl() {
            mJsHandlerCalls.resetCalls();
        }

        public void dispatchStartupQueue() {
            mJsHandlerCalls.dispatchStartupQueue();
        }

        public void returnValue(final Object args) {
            runOnMainThread(() -> mJsHandlerCalls.returnValue(args));
        }

        public boolean hasNativeMethod(Object args) throws JSONException {
            return mNamespaceObjects.hasNativeMethod(args);
        }

        public String callNative(String methodName, String argStr) {
            return mNamespaceObjects.callNative(methodName, argStr);
        }
    }

    public static class JavascriptInterfaceObject {
        private final DSBridge mDSBridge;

        public JavascriptInterfaceObject(DSBridge bridge) {
            mDSBridge = bridge;
        }

        public String getName() {
            return BRIDGE_NAME;
        }

        @Keep
        @JavascriptInterface
        public String call(String methodName, String argStr) {
            return mDSBridge.getInternals().callNative(methodName, argStr);
        }
    }

    public static class InternalJavascriptObject {
        public static final String NAMESPACE_DSB = "_dsb";
        private final DSBridge mDSBridge;

        public InternalJavascriptObject(DSBridge bridge) {
            mDSBridge = bridge;
        }

        @Keep
        @JavascriptInterface
        public boolean hasNativeMethod(Object args) throws JSONException {
            return mDSBridge.getInternals().hasNativeMethod(args);
        }

        @CallSuper
        @Keep
        @JavascriptInterface
        public void dsinit(Object args) {
            mDSBridge.getInternals().dispatchStartupQueue();
        }

        @Keep
        @JavascriptInterface
        public void returnValue(final Object args) {
            mDSBridge.getInternals().returnValue(args);
        }

    }
}
