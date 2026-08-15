package me.pompel.elauncher;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Build;

import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

/** Performs a best-effort rear-torch toggle without exposing camera access to command parsing. */
public final class TorchController {
    public enum Result {
        ON,
        OFF,
        UNAVAILABLE,
        PERMISSION_DENIED
    }

    private final Context context;
    private boolean enabled;
    private String observedCameraId;
    private boolean observing;
    private boolean stateKnown;
    private Camera legacyCamera;
    private final List<Callback> pendingCallbacks = new ArrayList<>();

    public TorchController(Context context) {
        this.context = context.getApplicationContext();
    }

    public void toggle(Callback callback) {
        if (!context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)) {
            callback.onResult(Result.UNAVAILABLE);
            return;
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            callback.onResult(Result.PERMISSION_DENIED);
            return;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            callback.onResult(toggleLegacyCamera());
            return;
        }
        CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        if (manager == null) {
            callback.onResult(Result.UNAVAILABLE);
            return;
        }
        try {
            String cameraId = rearFlashCamera(manager);
            if (cameraId == null) {
                callback.onResult(Result.UNAVAILABLE);
                return;
            }
            if (!stateKnown || !cameraId.equals(observedCameraId)) {
                pendingCallbacks.add(callback);
                observeTorchState(manager, cameraId);
                return;
            }
            callback.onResult(toggleModernCamera(manager, cameraId));
        } catch (CameraAccessException | SecurityException exception) {
            callback.onResult(exception instanceof SecurityException
                    ? Result.PERMISSION_DENIED : Result.UNAVAILABLE);
        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    private Result toggleModernCamera(CameraManager manager, String cameraId) {
        try {
            enabled = !enabled;
            manager.setTorchMode(cameraId, enabled);
            return enabled ? Result.ON : Result.OFF;
        } catch (CameraAccessException | SecurityException exception) {
            return exception instanceof SecurityException ? Result.PERMISSION_DENIED : Result.UNAVAILABLE;
        }
    }

    @SuppressWarnings("deprecation")
    private Result toggleLegacyCamera() {
        try {
            if (legacyCamera != null) {
                Camera.Parameters parameters = legacyCamera.getParameters();
                parameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
                legacyCamera.setParameters(parameters);
                legacyCamera.stopPreview();
                legacyCamera.release();
                legacyCamera = null;
                enabled = false;
                return Result.OFF;
            }
            int cameraId = rearLegacyCamera();
            if (cameraId == -1) {
                return Result.UNAVAILABLE;
            }
            Camera camera = Camera.open(cameraId);
            Camera.Parameters parameters = camera.getParameters();
            List<String> flashModes = parameters.getSupportedFlashModes();
            String flashMode = flashModes != null && flashModes.contains(Camera.Parameters.FLASH_MODE_TORCH)
                    ? Camera.Parameters.FLASH_MODE_TORCH : Camera.Parameters.FLASH_MODE_ON;
            if (flashModes == null || !flashModes.contains(flashMode)) {
                camera.release();
                return Result.UNAVAILABLE;
            }
            parameters.setFlashMode(flashMode);
            camera.setParameters(parameters);
            camera.startPreview();
            legacyCamera = camera;
            enabled = true;
            return Result.ON;
        } catch (RuntimeException exception) {
            return exception instanceof SecurityException ? Result.PERMISSION_DENIED : Result.UNAVAILABLE;
        }
    }

    @SuppressWarnings("deprecation")
    private static int rearLegacyCamera() {
        Camera.CameraInfo info = new Camera.CameraInfo();
        for (int cameraId = 0; cameraId < Camera.getNumberOfCameras(); cameraId++) {
            Camera.getCameraInfo(cameraId, info);
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                return cameraId;
            }
        }
        return -1;
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private static String rearFlashCamera(CameraManager manager) throws CameraAccessException {
        for (String cameraId : manager.getCameraIdList()) {
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
            Boolean hasFlash = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
            if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK
                    && Boolean.TRUE.equals(hasFlash)) {
                return cameraId;
            }
        }
        return null;
    }

    @TargetApi(Build.VERSION_CODES.M)
    private void observeTorchState(CameraManager manager, final String cameraId) {
        if (observing && cameraId.equals(observedCameraId)) {
            return;
        }
        observedCameraId = cameraId;
        observing = true;
        stateKnown = false;
        manager.registerTorchCallback(new CameraManager.TorchCallback() {
            @Override
            public void onTorchModeChanged(String changedCameraId, boolean on) {
                if (cameraId.equals(changedCameraId)) {
                    enabled = on;
                    stateKnown = true;
                    for (Callback callback : new ArrayList<>(pendingCallbacks)) {
                        callback.onResult(toggleModernCamera(manager, cameraId));
                    }
                    pendingCallbacks.clear();
                }
            }

            @Override
            public void onTorchModeUnavailable(String changedCameraId) {
                if (cameraId.equals(changedCameraId)) {
                    enabled = false;
                    stateKnown = false;
                    for (Callback callback : new ArrayList<>(pendingCallbacks)) {
                        callback.onResult(Result.UNAVAILABLE);
                    }
                    pendingCallbacks.clear();
                }
            }
        }, null);
    }

    public interface Callback {
        void onResult(Result result);
    }
}
