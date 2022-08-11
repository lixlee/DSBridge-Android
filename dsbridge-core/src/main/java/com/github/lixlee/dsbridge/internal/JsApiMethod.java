package com.github.lixlee.dsbridge.internal;

import android.support.annotation.Nullable;

import java.lang.reflect.InvocationTargetException;

import wendu.dsbridge.CompletionHandler;

/*
 * Created by lixlee on 2022/8/10
 */
@SuppressWarnings("unused")
public interface JsApiMethod {

    boolean isAsync();

    Object invoke(Object argument, @Nullable CompletionHandler<Object> completionHandler) throws InvocationTargetException, IllegalAccessException;
}
