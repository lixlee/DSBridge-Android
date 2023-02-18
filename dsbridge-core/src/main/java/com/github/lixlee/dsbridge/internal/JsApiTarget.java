package com.github.lixlee.dsbridge.internal;

import androidx.annotation.Nullable;

/*
 * Created by lixlee on 2022/8/10
 */
@SuppressWarnings("unused")
public interface JsApiTarget {

    @Nullable
    JsApiMethod findMethod(String name);
}
