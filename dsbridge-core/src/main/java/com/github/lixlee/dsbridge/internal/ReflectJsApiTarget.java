package com.github.lixlee.dsbridge.internal;

import android.os.Build;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.support.v4.util.Consumer;
import android.webkit.JavascriptInterface;

import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
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
        private static List<Class<?>[]> sMethodSpecs = null;
        private final Object mTarget;
        private final Method mMethod;
        private final List<Class<?>> mParametersTypes;

        public ReflectJsApiMethod(Object target, Method method, Class<?>... parametersTypes) {
            mTarget = target;
            mMethod = method;
            mParametersTypes = Arrays.asList(parametersTypes);
        }

        @Nullable
        public static ReflectJsApiMethod resolve(Object target, String name) {
            Class<?> cls = target.getClass();
            List<Class<?>[]> specs = getMethodSpecs();
            Method method;
            ReflectJsApiMethod result = null;
            for (Class<?>[] types : specs) {
                try {
                    method = cls.getMethod(name, types);
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
            Class<?> clazz = mParametersTypes.size() > 0 ? mParametersTypes.get(0) : null;
            Object arg = castArgumentType(argument, clazz);
            if (isAsync()) {
                return mMethod.invoke(mTarget, arg, CompletionHandlerType.wrap(completionHandler));
            }
            return mMethod.invoke(mTarget, arg);
        }

        private static List<Class<?>[]> getMethodSpecs() {
            if (sMethodSpecs != null) {
                return sMethodSpecs;
            }
            List<Class<?>[]> specs = new ArrayList<>();
            specs.add(new Class[]{JSONObject.class, CompletionHandler.class});
            specs.add(new Class[]{JSONObject.class, Consumer.class});
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                specs.add(new Class[]{JSONObject.class, java.util.function.Consumer.class});
            }
            specs.add(new Class[]{JSONObject.class});

            specs.add(new Class[]{Object.class, CompletionHandler.class});
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                specs.add(new Class[]{Object.class, java.util.function.Consumer.class});
            }
            specs.add(new Class[]{Object.class, Consumer.class});
            specs.add(new Class[]{Object.class});

            specs.add(new Class[]{String.class, CompletionHandler.class});
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                specs.add(new Class[]{String.class, java.util.function.Consumer.class});
            }
            specs.add(new Class[]{String.class, Consumer.class});
            specs.add(new Class[]{String.class});

            sMethodSpecs = specs;
            return sMethodSpecs;
        }

        private static Object castArgumentType(Object argument, Class<?> type) {
            if (type == Object.class || argument == null) {
                return argument;
            }
            Object result = argument;
            if (type == JSONObject.class) {
                if (JSONObject.NULL == argument) {
                    return null;
                }
                result = null;
                if (argument instanceof JSONObject) {
                    result = argument;
                } else {
                    try {
                        result = new JSONObject(argument.toString());
                    } catch (JSONException ignored) {
                    }
                }
            } else if (type == String.class) {
                if (JSONObject.NULL == argument) {
                    result = "";
                } else {
                    result = String.valueOf(argument);
                }
            }
            return result;
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

    static class CompletionHandlerType implements CompletionHandler<Object>, Consumer<Object> {
        private final CompletionHandler<Object> mCompletionHandler;

        static CompletionHandlerType wrap(CompletionHandler<Object> completionHandler) {
            if (completionHandler == null) {
                return null;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                return new CompletionHandlerType24(completionHandler);
            }
            return new CompletionHandlerType(completionHandler);
        }

        public CompletionHandlerType(CompletionHandler<Object> completionHandler) {
            mCompletionHandler = completionHandler;
        }

        @Override
        public void complete(Object retValue) {
            if (mCompletionHandler != null) {
                mCompletionHandler.complete(retValue);
            }
        }

        @Override
        public void complete() {
            if (mCompletionHandler != null) {
                mCompletionHandler.complete();
            }
        }

        @Override
        public void setProgressData(Object value) {
            if (mCompletionHandler != null) {
                mCompletionHandler.setProgressData(value);
            }
        }

        @Override
        public void accept(Object retValue) {
            complete(retValue);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    static class CompletionHandlerType24 extends CompletionHandlerType implements java.util.function.Consumer<Object> {

        public CompletionHandlerType24(CompletionHandler<Object> completionHandler) {
            super(completionHandler);
        }

        @Override
        public void accept(Object retValue) {
            complete(retValue);
        }
    }
}
