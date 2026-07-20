# Android WebView Scaffold

A reusable Android WebView project scaffold with GitHub Actions CI/CD pipeline.

## Features

- **Full WebView Support** - Load any web URL
- **JavaScript Enabled** - Full JS support with DOM storage
- **File Upload/Download** - Handle file operations
- **Permissions** - Camera, microphone, location support
- **Pull-to-Refresh** - Swipe down to reload
- **Back Navigation** - Hardware back button support
- **App Cache** - Offline caching enabled
- **CI/CD Ready** - GitHub Actions auto-build and release

## Quick Start

### 1. Clone this scaffold

```bash
git clone https://github.com/programmerauthor/AndroidWebView-Scaffold.git
cd AndroidWebView-Scaffold
```

### 2. Customize the URL

Edit `app/src/main/java/com/example/webviewapp/MainActivity.kt`:

```kotlin
companion object {
    // Change this to your target URL
    const val TARGET_URL = "https://your-website.com"
}
```

### 3. Update app info (optional)

Edit `app/build.gradle`:
```gradle
defaultConfig {
    applicationId "com.yourcompany.yourapp"
    versionCode 1
    versionName "1.0"
}
```

### 4. Build locally

```bash
./gradlew assembleDebug
```

APK will be at: `app/build/outputs/apk/debug/app-debug.apk`

### 5. Or use GitHub Actions

Push to `main` branch - Actions will automatically:
- Build Debug APK
- Upload to Artifacts
- Create/Update Release

## Project Structure

```
.
├── app/
│   ├── build.gradle              # App-level build config
│   └── src/main/
│       ├── AndroidManifest.xml   # App manifest with permissions
│       ├── java/com/example/webviewapp/
│       │   └── MainActivity.kt   # Main WebView activity
│       └── res/
│           ├── layout/
│           │   └── activity_main.xml
│           ├── drawable/
│           │   └── progress_drawable.xml
│           └── values/
│               ├── strings.xml
│               ├── colors.xml
│               └── themes.xml
├── build.gradle                  # Project-level build config
├── settings.gradle
├── gradle.properties
└── .github/workflows/
    └── build.yml                 # CI/CD workflow
```

## Customization Guide

### Change Target URL

```kotlin
// In MainActivity.kt
const val TARGET_URL = "https://kimi.moonshot.cn"
```

### Add More Permissions

Edit `app/src/main/AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.YOUR_PERMISSION" />
```

### Change App Name

Edit `app/src/main/res/values/strings.xml`:

```xml
<string name="app_name">Your App Name</string>
```

### Change Theme Colors

Edit `app/src/main/res/values/colors.xml` and `themes.xml`

## Permissions Included

| Permission | Purpose |
|------------|---------|
| `INTERNET` | Network access |
| `ACCESS_NETWORK_STATE` | Check connectivity |
| `WRITE_EXTERNAL_STORAGE` | File downloads |
| `READ_EXTERNAL_STORAGE` | File uploads |
| `CAMERA` | Camera access |
| `RECORD_AUDIO` | Microphone access |
| `ACCESS_FINE_LOCATION` | Precise location |
| `ACCESS_COARSE_LOCATION` | Approximate location |

## GitHub Actions Workflow

The included workflow (`/.github/workflows/build.yml`) will:

1. Checkout code
2. Setup JDK 17
3. Setup Android SDK
4. Cache Gradle dependencies
5. Build Debug APK
6. Upload to GitHub Artifacts
7. Create/Update GitHub Release

### Triggering Builds

- **Automatic**: Push to `main` or `master` branch
- **Manual**: Go to Actions → Build Android WebView APK → Run workflow

### Downloading APK

1. Go to Repository → Actions
2. Click on the latest successful run
3. Download `WebViewApp-APK` artifact

Or download from the [Releases](../../releases) page.

## License

MIT License - Feel free to use for any project!
