# teslate-android

Teslate is a temporary mirroring solution for Tesla vehicles
[until Tesla supports mirroring natively](http://www.teslarati.com/tesla-sdk-iphone-android-app-mirroring/). teslate-android is the
Android app that enables this mirroring solution.

# Installation

The APK is not yet available on the Play Store. To install, you will need to clone the repo and build. You will need the Android SDK and build tools, available [here](https://developer.android.com/studio/index.html#downloads).

Once you have the prerequisites, with your device plugged in for [software installation](https://developer.android.com/studio/run/device.html):

1. `./gradlew installDebug`
2. `./start-instrumentation` (required for remote input)

For more information:

* [Gradle](http://gradle.org/getting-started-android/)
* [Android Studio](https://developer.android.com/studio/index.html)

# Usage

1. In your car browser, navigate to https://teslate-server.appspot.com. The webpage will generate and append a random session ID to the URL. Alternatively, you can enter your own unique session ID as a query string after the URL (e.g. https://teslate-server.appspot.com?my-session). You may want to bookmark this URL.
2. Start the Teslate Android app.
3. If the app shows a message saying "ADB injection required for remote input", you will need to perform step 2 under installation instructions again. This is necessary for remote input any time the device is restarted, and from time to time thereafter.
4. Set the session ID to your session ID.
5. In the Android app, tap the "Start Server" button.

# Data usage

The in-car browser [will not connect to LAN resources]
(https://teslamotorsclub.com/tmc/threads/successful-connection-on-the-model-s-internal-ethernet-network.28185/page-19), so mirroring must
be done by bouncing off WAN via [teslate-server](https://github.com/AsturaPhoenix/teslate-server).

At present, Teslate may use about 40 MB per hour while mirroring actively changing screens such as navigation on a map. Monthly usage may
vary, but with liberal estimates of 4 hours on weekdays and 10 hours on weekends, this amounts to about 5 GB per month.
