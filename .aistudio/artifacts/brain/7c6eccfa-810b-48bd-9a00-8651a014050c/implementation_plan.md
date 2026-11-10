# YVGSPCAM — CameraX Reopening Timeout Fix & Field Viewfinder Fallback Plan

## 1. Root Cause Analysis
- **Error Observed**: `E/Camera2CameraImpl: Camera reopening attempted for 10000ms without success.`
- **Cause**:
  1. `CameraScreen` mounts `AndroidView(PreviewView)` and calls `ProcessCameraProvider.bindToLifecycle(...)` immediately on app launch before the user has granted `Manifest.permission.CAMERA`, or while the emulator's virtual camera device is unavailable/locked.
  2. When Camera2 fails to open the camera device, `Camera2CameraImpl` enters a 10-second internal retry loop unless the use cases are explicitly unbound upon the first `CameraState.Error` or permission/device check.
  3. `CameraSelector.DEFAULT_BACK_CAMERA` was bound without first verifying `cameraProvider.hasCamera(...)` or cleaning up bindings via `DisposableEffect`.

---

## 2. Planned Changes

### A. Permission-Gated CameraX Lifecycle & Instant Error Recovery (`CameraScreen.kt`)
- **Permission Gate**: Pass `hasCameraPermission` from `MainActivity` into `CameraScreen`. Only initialize `ProcessCameraProvider` when `hasCameraPermission == true` and the user has not selected Simulated Field Mode.
- **Hardware Camera Validation**:
  - Check `cameraProvider.hasCamera(selectedLens)` (supporting both `CameraSelector.DEFAULT_BACK_CAMERA` and `CameraSelector.DEFAULT_FRONT_CAMERA`, or falling back to any available lens).
  - If no hardware camera is available on the device/emulator, immediately call `cameraProvider.unbindAll()` and switch to the **Interactive Simulated Field Viewfinder** without triggering `Camera2CameraImpl` retries.
- **Real-Time `CameraState` Observer**:
  - Attach a `camera.cameraInfo.cameraState.observe(lifecycleOwner)` listener immediately upon `bindToLifecycle`.
  - If `cameraState.error != null` (such as `ERROR_CAMERA_IN_USE`, `ERROR_MAX_CAMERAS_IN_USE`, `ERROR_CAMERA_DISABLED`, or `ERROR_CAMERA_FATAL_ERROR`), immediately call `cameraProvider.unbindAll()` to halt Camera2's 10,000ms reopening loop and automatically switch to the **Interactive Simulated Field Viewfinder**.
- **Lifecycle Cleanup**:
  - Add a `DisposableEffect(lifecycleOwner, cameraProvider)` to unbind all use cases cleanly when navigating away from `CameraScreen` or disposing the view.

### B. Top-Bar Camera Lens & Simulation Mode Toggle (`CameraScreen.kt` & `MainActivity.kt`)
- **Mode & Lens Switcher**:
  - Add a dedicated control chip/button in the top HUD bar allowing the user to cycle or toggle between:
    - **Back Camera (Live)**
    - **Front Camera (Live)**
    - **Field Simulation Viewfinder** (interactive surveyor terrain canvas with selectable inspection scenes, live compass/horizon crosshair, and full high-resolution photo + GPS stamp capture support).
- **Permission Prompt Banner**:
  - If camera permission has not been granted yet, provide a one-tap **"Grant Camera"** action pill in the HUD while keeping the interactive simulated field viewfinder immediately usable.
