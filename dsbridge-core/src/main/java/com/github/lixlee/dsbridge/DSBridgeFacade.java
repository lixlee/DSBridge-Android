package com.github.lixlee.dsbridge;

import android.support.annotation.Nullable;

import wendu.dsbridge.OnReturnValue;

/*
 * Created by lixlee on 2022/7/30
 */
@SuppressWarnings("unused")
public interface DSBridgeFacade extends JavascriptEvaluator {
    /**
     * This method can be called in any thread, and if it is not called in the main thread,
     * it will be automatically distributed to the main thread.
     *
     * @param script Javascript code
     */
    @Override
    void evaluateJavascript(String script);

    /**
     * Call the javascript API. If a handler is given, the javascript handler can respond.
     * the handlerName can contain the namespace. The handler will be called in main thread.
     */
    <T> void callHandler(String method, @Nullable Object[] args, @Nullable OnReturnValue<T> handler);

    void callHandler(String method, @Nullable Object[] args);

    <T> void callHandler(String method, @Nullable OnReturnValue<T> handler);

    /**
     * Test whether the handler exist in javascript
     *
     * @param handlerName
     * @param existCallback
     */
    void hasJavascriptMethod(String handlerName, OnReturnValue<Boolean> existCallback);

    /**
     * Add a java object which implemented the javascript interfaces to dsBridge with namespace.
     * Remove the object using {@link #removeJavascriptObject(String) removeJavascriptObject(String)}
     *
     * @param object    Native JsApi object
     * @param namespace if empty, the object have no namespace.
     */
    void addJavascriptObject(Object object, @Nullable String namespace);

    /**
     * remove the javascript object with supplied namespace.
     *
     * @param namespace
     */
    void removeJavascriptObject(@Nullable String namespace);
}
