# VirtualCam 🎭

Virtual space Android app — jalankan app lain di dalam container, kamera dibelokkan ke foto/video lokal. **Tanpa root.**

## Arsitektur

```
┌──────────────────────────────────────────┐
│              VirtualCam App              │
│  ┌────────────────────────────────────┐  │
│  │         Virtual Space              │  │
│  │  (powered by VirtualApp)           │  │
│  │                                    │  │
│  │  ┌──────────┐  Camera2 API         │  │
│  │  │  Target  │──────────────────┐   │  │
│  │  │   App    │   (intercepted)  │   │  │
│  │  └──────────┘                  ▼   │  │
│  │                         CameraHook │  │
│  │                              │     │  │
│  └──────────────────────────────│─────┘  │
│                                 ▼        │
│                    CameraFrameProvider   │
│                   (foto / video lokal)   │
└──────────────────────────────────────────┘
```

## Modul

| Modul | Fungsi |
|-------|--------|
| `app` | UI utama, list virtual apps, kontrol sumber kamera |
| `virtual-core` | Engine virtual space, install/launch app |
| `camera-hook` | Intercept Camera2 API, inject frame dari foto/video |

## Setup

### 1. Clone + submodule VirtualApp
```bash
git clone https://github.com/brruham-arch/virtalcam.git
cd virtalcam
git submodule add https://github.com/asLody/VirtualApp VirtualApp
```

### 2. Aktifkan VirtualApp di build.gradle
Di `app/build.gradle`, uncomment:
```groovy
implementation project(':VirtualApp:lib')
```

### 3. Aktifkan VirtualApp di kode
Cari komentar `[VA]` di file-file ini dan uncomment barisnya:
- `VirtualSpaceEngine.kt` — init, install, launch
- `VirtualCamApp.kt` — startup

### 4. Register camera hook di VirtualApp
Buat class `CamHookProvider.kt`:
```kotlin
class CamHookProvider : HookProvider() {
    override fun onHook() {
        // Hook CameraManager service binder
        VirtualCameraHook.init(context, frameProvider)
    }
}
```

### 5. Build & Run
```bash
# Android Studio: Sync Gradle → Run
# atau via Gradle:
./gradlew assembleDebug
```

## Cara Pakai

1. Buka VirtualCam
2. Pilih sumber kamera (📷 real / 🖼️ foto / 🎬 video)
3. Tap **+** → pilih APK target (misal: WhatsApp, kamera app, video call app)
4. App ter-install di virtual space
5. Tap **▶** untuk launch app di dalam container
6. Kamera app tersebut sekarang output-nya = foto/video yang kamu pilih

## Mode Tanpa VirtualApp (Stub Mode)

Tanpa VirtualApp, engine berjalan dalam stub mode:
- App list bisa dikelola
- Camera hook aktif di dalam proses VirtualCam sendiri  
- App dilunch secara normal (tanpa isolasi)
- Untuk isolasi penuh → butuh VirtualApp

## Tech Stack

- **VirtualApp** — virtual container, no root
- **Camera2 API** — intercept via dynamic proxy + reflection
- **MediaPlayer** — video loop rendering
- **Kotlin Coroutines** — async frame rendering
- **MVVM** — ViewModel + LiveData

## Catatan

Beberapa versi Android (12+) memperketat reflection ke internal API.
Jika hook camera gagal, fallback ke VirtualApp's native hook system yang lebih dalam.

---
minSdk 24 · targetSdk 34 · Kotlin 1.9
