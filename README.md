# DSBridge-Android

[![](https://www.jitpack.io/v/lixlee/DSBridge-Android.svg)](https://www.jitpack.io/#lixlee/DSBridge-Android)
![language](https://img.shields.io/badge/language-Java-yellow.svg)
![](https://img.shields.io/badge/minSdkVersion-19-yellow.svg)

Fork from https://github.com/wendux/DSBridge-Android


# Installation

**Step 1.** Add the JitPack repository to your build file

Add it in your root build.gradle at the end of repositories:
```gradle
  allprojects {
      repositories {
          ...
          maven { url 'https://www.jitpack.io' }
      }
  }
```

**Step 2.** Add the dependency

```gradle
dependencies {
    // DSBridge with DWebView
    implementation 'com.github.lixlee.DSBridge-Android:dsbridge-dwebview:3.1.0'
}
```
