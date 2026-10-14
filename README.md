<p align="center">
  <img src="assets/logo.svg" width="120" alt="PortConnect logo">
</p>

<h1 align="center">PortConnect</h1>

<p align="center">
  A small Android launcher for the web pages running on devices on your local network.<br>
  Pick a service, and it opens right inside the app.
</p>

<p align="center">
  <a href="https://github.com/zilyzal/PortConnect/releases/latest/download/PortConnect.apk"><b>Download the APK</b></a>
</p>

---

## What it does

- Lists your services as large tiles, each showing its port number.
- Opens the page for a service at `http://<device address>:<port>` in an in-app web view, with back and reload buttons.
- Remembers your device address between launches. Type it into the **Device address** box and tap **Save**. The default is `192.168.100.4`, and leaving the box empty uses the default.
- If a page can't be reached, it tells you which address it tried and offers **Try again**.

## Services

| Name | Port |
| --- | --- |
| Blackbox Store | 1453 |
| Orbit Store | 34177 |
| Payload Manager | 8084 |
| CheatRunner | 9999 |
| Ps5upload | 9120 |

To add or change a service, edit the `targets` list in `app/src/main/java/com/example/portconnect/MainActivity.kt`.

## Install

1. Download [PortConnect.apk](https://github.com/zilyzal/PortConnect/releases/latest/download/PortConnect.apk) on your phone.
2. Open it and allow installs from your browser or file manager when Android asks.
3. Make sure your phone is on the same Wi-Fi network as the device you want to reach.

Every build is signed with a temporary debug key, so if you already have an older build installed, uninstall it first.

## Build it yourself

Open the project in Android Studio and run it, or push to GitHub: the **Build APK** workflow compiles a debug APK on every push to `main` and publishes it as a release.

- Kotlin, Jetpack Compose (Material 3)
- Android Gradle Plugin 8.5.2, Kotlin 2.0.20
- minSdk 26, targetSdk 35
