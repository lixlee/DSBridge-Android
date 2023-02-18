package org.cocos2dx.javascript.jsb;

import androidx.annotation.CallSuper;
import androidx.annotation.Keep;

import com.github.lixlee.dsbridge.DSBridge;
import com.github.lixlee.dsbridge.JavascriptEvaluator;

/*
 * Created by lixlee on 2022/8/11
 */
@Keep
@SuppressWarnings("unused")
public abstract class JSBDSBridge implements JavascriptEvaluator {
    private static JSBDSBridge sImpl = null;
    protected final DSBridge mBridge = new DSBridge(this);

    public static void setImpl(JSBDSBridge impl) {
        sImpl = impl;
    }

    /**
     * This method will call by dsBridge.js from cocos2dx-js engine script runtime.
     */
    @Keep
    public static String call(String method, String argument) {
        String ret = "";
        if (sImpl != null && sImpl.getBridge() != null) {
            ret = sImpl.getBridge().getJavascriptInterfaceObject().call(method, argument);
        }
        return ret != null ? ret : "";
    }

    public DSBridge getBridge() {
        return mBridge;
    }

    @Override
    public abstract void evaluateJavascript(String script);

    @CallSuper
    public void onCreate() {
        setImpl(this);
        mBridge.init();
        mBridge.setJavascriptEvaluator(this);
    }

    @CallSuper
    public void onDestroy() {
        setImpl(null);
        mBridge.destroy();
    }
}
