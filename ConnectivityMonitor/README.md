# ConnectivityMonitor

## Overview

`ConnectivityMonitor` is an Android library that provides a simple way to monitor the network connectivity status of an Android device. It offers a drop-in replacement for your application's `Application` class, which will automatically start monitoring the connectivity status when your app is launched.

## Installation

Add the following dependency to your project's `build.gradle` file:

```gradle.kts
implementation("com.github.appoly.AppolyDroid-Toolbox:connectivitymonitor:1.0.34")
```

## Usage

### Option 1: Use provided Application class

AndroidManifest.xml

```xml
<application
    android:name="uk.co.appoly.droid.ConnectivityMonitorApplication"
    ... >
</application>
```

### Option 2: Extend it

If you prefer to keep your existing `Application` class, you can extend the `ConnectivityMonitorApplication` class and override the `onCreate` method:

```java
public class MyApplication extends ConnectivityMonitorApplication {
    @Override
    public void onCreate() {
        super.onCreate();
        // Your custom code here
    }
}
```

Don't forget to update your `AndroidManifest.xml` to use your custom application class:

```xml
<application
    android:name=".MyApplication"
    ... >
</application>
```

## License

`ConnectivityMonitor` is released under the MIT License. See the [LICENSE](LICENSE) file for details.