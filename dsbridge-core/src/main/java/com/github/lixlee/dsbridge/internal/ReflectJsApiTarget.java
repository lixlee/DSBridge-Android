package com.github.lixlee.dsbridge.internal;

import android.os.Build;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.util.Consumer;

import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;

import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import wendu.dsbridge.CompletionHandler;

/*
 * Created by lixlee on 2022/8/10
 */
@SuppressWarnings("unused")
public final class ReflectJsApiTarget implements JsApiTarget {
    private final Object mTarget;
    private final Map<String, JsApiMethod> mMethodCache = new ConcurrentHashMap<>();

    public ReflectJsApiTarget(Object target) {
        mTarget = target;
    }

    public static JsApiTarget wrap(Object object) {
        if (object instanceof ReflectJsApiTarget
                || object instanceof IterableJsApiTarget) {
            return (JsApiTarget) object;
        } else if (object instanceof Iterable) {
            return new IterableJsApiTarget(object);
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
        JsApiMethod method = ReflectJsApiMethod.resolve(mTarget, name);
        if (method != null) {
            mMethodCache.put(name, method);
        }
        if (method == null && mTarget instanceof JsApiTarget) {
            method = ((JsApiTarget) mTarget).findMethod(name);
        }
        return method;
    }

    public final static class ReflectJsApiMethod implements JsApiMethod {
        private static List<Class<?>[]> sMethodSpecs = null;
        private static List<Class<?>> sCallbackTypes = null;
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

        private static List<Class<?>> getCallbackTypes() {
            if (sCallbackTypes != null) {
                return Collections.unmodifiableList(sCallbackTypes);
            }
            List<Class<?>> classes = new ArrayList<>(8);
            classes.add(CompletionHandler.class);
            classes.add(Consumer.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                classes.add(java.util.function.Consumer.class);
            }
            classes.add(ValueCallback.class);

            sCallbackTypes = classes;
            return Collections.unmodifiableList(classes);
        }

        private static List<Class<?>[]> getMethodSpecs() {
            if (sMethodSpecs != null) {
                return sMethodSpecs;
            }
            List<Class<?>> callbackTypes = getCallbackTypes();
            List<Class<?>[]> specs = new ArrayList<>();
            for (Class<?> type : callbackTypes) {
                specs.add(new Class[]{JSONObject.class, type});
            }
            specs.add(new Class[]{JSONObject.class});

            for (Class<?> type : callbackTypes) {
                specs.add(new Class[]{Object.class, type});
            }
            specs.add(new Class[]{Object.class});

            for (Class<?> type : callbackTypes) {
                specs.add(new Class[]{String.class, type});
            }
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

    public final static class IterableJsApiTarget implements JsApiTarget {
        private final ReflectJsApiTarget mOrigin;
        private final Object mTarget;
        private final Map<Object, JsApiTarget> mTargets = new ConcurrentHashMap<>();

        public IterableJsApiTarget(Object target) {
            mOrigin = new ReflectJsApiTarget(target);
            mTarget = target;
        }

        @Nullable
        @Override
        public JsApiMethod findMethod(String name) {
            JsApiMethod method = null;
            if (mTarget instanceof Iterable) {
                for (Object obj : (Iterable) mTarget) {
                    if (obj == null) {
                        continue;
                    }
                    ObjectKey objectKey = ObjectKey.of(obj);
                    JsApiTarget target = mTargets.get(objectKey);
                    if (target == null) {
                        target = ReflectJsApiTarget.wrap(obj);
                        mTargets.put(objectKey, target);
                    }
                    method = target.findMethod(name);
                    if (method != null) {
                        break;
                    }
                }
            }
            if (method != null) {
                return method;
            }
            return mOrigin.findMethod(name);
        }
    }

    static class CompletionHandlerType implements
            CompletionHandler<Object>,
            Consumer<Object>,
            ValueCallback<Object> {
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

        @Override
        public void onReceiveValue(Object value) {
            complete(value);
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

    static class ObjectKey {
        private Object mObject;

        public ObjectKey(Object o) {
            this.mObject = o;
        }

        static ObjectKey of(Object o) {
            return new ObjectKey(o);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ObjectKey objectKey = (ObjectKey) o;
            return mObject == objectKey.mObject;
        }

        @Override
        public int hashCode() {
            return Objects.hash(mObject);
        }
    }
}
