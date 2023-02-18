package com.github.lixlee.dsbridge.internal;

import android.support.annotation.Nullable;
import android.webkit.JavascriptInterface;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import wendu.dsbridge.CompletionHandler;

/*
 * Created by lixlee on 2022/8/10
 */
@SuppressWarnings("unused")
public class ReflectJsApiTarget implements JsApiTarget {
    private final Object mTarget;
    private final Map<String, JsApiMethod> mMethodCache = new ConcurrentHashMap<>();

    public ReflectJsApiTarget(Object target) {
        mTarget = target;
    }

    public static JsApiTarget wrap(Object object) {
        if (object instanceof JsApiTarget) {
            return (JsApiTarget) object;
        }
        return new ReflectJsApiTarget(object);
    }

    @Nullable
    @Override
    public JsApiMethod findMethod(String name) {
        JsApiMethod jsApiMethod = mMethodCache.get(name);
        if (jsApiMethod != null) {
            return jsApiMethod;
        }
        ReflectJsApiMethod method = ReflectJsApiMethod.resolve(mTarget, name);
        if (method != null) {
            mMethodCache.put(name, method);
        }
        return method;
    }

    public static class ReflectJsApiMethod implements JsApiMethod {
        private final Object mTarget;
        private final Method mMethod;
        private final List<Class<?>> mParametersTypes;

        public ReflectJsApiMethod(Object target, Method method, List<Class<?>> parametersTypes) {
            mTarget = target;
            mMethod = method;
            mParametersTypes = parametersTypes;
        }

        @Nullable
        public static ReflectJsApiMethod resolve(Object target, String name) {
            Class<?> cls = target.getClass();
            List<Class<?>>[] specs = new List[]{
                    Arrays.asList(Object.class, CompletionHandler.class),
                    Collections.singletonList(Object.class),
            };
            Method method;
            ReflectJsApiMethod result = null;
            for (List<Class<?>> types : specs) {
                try {
                    method = cls.getMethod(name, types.toArray(new Class[0]));
                    JavascriptInterface annotation = method.getAnnotation(JavascriptInterface.class);
                    if (annotation != null) {
                        result = new ReflectJsApiMethod(target, method, types);
                        break;
                    }
                } catch (NoSuchMethodException ignored) {
                }
            }
            return result;
        }

        @Override
        public boolean isAsync() {
            return mParametersTypes != null && mParametersTypes.size() > 1;
        }

        @Override
        public Object invoke(Object argument, @Nullable CompletionHandler<Object> completionHandler) throws InvocationTargetException, IllegalAccessException {
            mMethod.setAccessible(true);
            if (isAsync()) {
                return mMethod.invoke(mTarget, argument, completionHandler);
            }
            return mMethod.invoke(mTarget, argument);
        }
    }

    public static class MergedJsApiTarget implements JsApiTarget {
        private final List<JsApiTarget> mTargets = new CopyOnWriteArrayList<>();

        public static JsApiTarget wrap(Object object) {
            return ReflectJsApiTarget.wrap(object);
        }

        public static MergedJsApiTarget merge(Object... objects) {
            MergedJsApiTarget merged = new MergedJsApiTarget();
            for (Object object : objects) {
                merged.add(object);
            }
            return merged;
        }

        public boolean add(Object object) {
            if (object == null) {
                return false;
            }
            return mTargets.add(wrap(object));
        }

        public void add(int index, Object object) {
            if (object == null) {
                return;
            }
            mTargets.add(index, wrap(object));
        }

        @Nullable
        @Override
        public JsApiMethod findMethod(String name) {
            for (JsApiTarget target : mTargets) {
                if (target == null) {
                    continue;
                }
                JsApiMethod method = target.findMethod(name);
                if (method != null) {
                    return method;
                }
            }
            return null;
        }
    }
}
