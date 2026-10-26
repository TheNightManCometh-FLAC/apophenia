# Running Apophenia in the Android emulator (on this PC)

Set up 26 Oct 2026 so Claude can walk through screens without the phone. It catches crashes, dead ends and layout
problems; it does **not** stand in for the S22 Ultra on audio (latency, the mic, Oboe performance modes) or on speed.

- **Emulator** from the SDK (`D:\Android\Sdk\emulator`), accelerated by Windows Hypervisor Platform (`emulator -accel-check`: "WHPX(10.0.19045) is installed and usable").
- **Image** `system-images;android-33;google_apis;x86_64` (Android 13). It has **no ARM translation** (`ro.product.cpu.abilist` = x86_64, native bridge 0), so the app must include x86_64 libraries.
- **Virtual phone** `Apophenia` (Pixel 6 profile: 1080×2400, 420 dpi; 4 GB RAM, GPU on), stored in `D:\Android\avd` because C: is nearly full. Set `ANDROID_AVD_HOME=D:\Android\avd` (and `ANDROID_SDK_ROOT=D:\Android\Sdk`) before running the emulator or avdmanager.

```
emulator -avd Apophenia -no-boot-anim -gpu auto -no-snapshot-load
./gradlew.bat assembleDebug -Pemulator      # adds x86_64; without -Pemulator builds stay arm64-only for the phone
adb -s emulator-5554 install -r -g app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 shell am start -n com.betterappmafia.apophenia/.MainActivity
adb -s emulator-5554 exec-out screencap -p > shot.png
```

- `-Pemulator` APKs are about 275 MB (both ABIs); never ship one. The dist zips are built without it.
- Debug builds have two launcher icons, Apophenia and Device Lab (`app/src/debug/AndroidManifest.xml`); a plain
  `monkey` launch opens Device Lab, so start `.MainActivity` by name.
- "System UI isn't responding" after a cold boot is the emulator's own launcher, not the app; tap Wait.

- The emulator image's Bluetooth service crashes in a loop and keeps raising "System UI isn't responding"; `adb -s emulator-5554 shell cmd bluetooth_manager disable` stops it.
