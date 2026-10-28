# Matinee

Native apps, separate from dx.bi: nothing here touches `app/` or `tv/`.

| Folder | App | ID | Release tag |
|---|---|---|---|
| `tv/` | Matinee for Android TV, Google TV and Fire TV (Kotlin, Compose for TV) | `com.abhilash.matinee.tv` | `matinee-tv-v<version>` |
| `desktop/` (later) | Matinee for Windows, Mac and Linux | `com.abhilash.matinee` | `matinee-desktop-v<version>` |

## Building the TV app

Needs JDK 17 or 21 and the Android SDK (platform 35).

```sh
cd matinee/tv
./gradlew assembleDebug    # app/build/outputs/apk/debug/app-debug.apk
```

## Releasing

Publish a GitHub release tagged `matinee-tv-v0.1.0` (any `x.y.z`). `.github/workflows/matinee-tv.yml` builds
the signed APK and attaches it as `matinee-tv.apk`. dx.bi's workflows only build `v*` tags, so they skip it.

Untick "Set as the latest release" when publishing. dx.bi's download links and update checks follow the
latest release; the workflow hands it back to dx.bi's newest release anyway, but only after a few seconds.

The release key lives in the repository's secrets, never in git:

```sh
keytool -genkeypair -v -keystore matinee-tv-release.jks -alias matinee-tv -keyalg RSA -keysize 4096 -validity 36500
base64 -i matinee-tv-release.jks | pbcopy   # paste as MATINEE_TV_KEYSTORE_B64; its password as MATINEE_TV_KEYSTORE_PASSWORD
```

Back the key up somewhere safe. Every update must be signed with the same key, or Android won't install it
over the old version.
