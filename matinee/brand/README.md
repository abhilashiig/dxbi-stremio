# Matinee — Android TV assets

Colours: gold #F2B441, black #000000. Wordmark type: Unbounded Bold (SIL OFL), converted to outlines.

## Drop-in resources
Already in `matinee/tv/app/src/main/res/` (plus `drawable-nodpi/wordmark.png`, from `source/png/wordmark_transparent.png`):
- `mipmap-*/ic_launcher.png`, `ic_launcher_round.png` — legacy launcher icons (mdpi–xxxhdpi)
- `mipmap-anydpi-v26/` + `drawable/ic_launcher_foreground.xml` — adaptive icon (API 26+), mark sized inside the 66dp safe zone, with a monochrome layer
- `drawable-xhdpi/tv_banner.png` (320×180) plus xxhdpi/xxxhdpi — the Android TV home-screen banner

## Manifest
```xml
<uses-feature android:name="android.software.leanback" android:required="false" />
<uses-feature android:name="android.hardware.touchscreen" android:required="false" />

<application
    android:icon="@mipmap/ic_launcher"
    android:roundIcon="@mipmap/ic_launcher_round"
    android:banner="@drawable/tv_banner"
    ... >
    <activity android:name=".MainActivity" android:exported="true">
        <intent-filter>
            <action android:name="android.intent.action.MAIN" />
            <category android:name="android.intent.category.LEANBACK_LAUNCHER" />
        </intent-filter>
    </activity>
</application>
```

## Google Play
- `play-store/icon_512.png` — hi-res app icon
- `play-store/tv_banner_1280x720.png` — Android TV banner for the store listing
- `play-store/feature_graphic_1024x500.png` — feature graphic

## Source
`source/` has editable SVGs (wordmark, transparent wordmark, app icon, transparent mark, 1920×1080 banner) and PNG masters.
