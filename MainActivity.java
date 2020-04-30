package com.example.appcam3;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.MediaStore;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Toast;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class MainActivity extends AppCompatActivity {

    private static int VIDEO_REQUEST = 101;
    private static int PICTURE_REQUEST = 102;
    private static final int CAMERA_PERMISSION = 0;

    private Uri videoURI = null;
    private Uri pictureURI = null;
    private String appCameraIdBack;
    private int TotalRotation;
    private Size appVideoSize;
    private TextureView imagePreview;
    private Size previewSize;
    private CaptureRequest.Builder appCaptureRequest;
    private CameraDevice appCamera;
    private HandlerThread appBackgroundThread;
    private Handler appBackgroundHandler;

    private static SparseIntArray ORIENTS = new SparseIntArray();

    static {
        ORIENTS.append(Surface.ROTATION_0, 0);
        ORIENTS.append(Surface.ROTATION_90, 90);
        ORIENTS.append(Surface.ROTATION_0, 180);
        ORIENTS.append(Surface.ROTATION_270, 270);
    }

    private CameraDevice.StateCallback appCameraStateCallBack = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            appCamera = camera;
            startPreview();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            camera.close();
            appCamera = null;
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            camera.close();
            appCamera = null;
        }
    };

    private TextureView.SurfaceTextureListener imagePreviewListener = new TextureView.SurfaceTextureListener() {
        @RequiresApi(api = Build.VERSION_CODES.M)
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            setupCamera(width, height);
            startCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {

        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        imagePreview = (TextureView) findViewById(R.id.preview);

    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private void startCamera() {
        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {

            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                cameraManager.openCamera(appCameraIdBack, appCameraStateCallBack, appBackgroundHandler);
            }else{

                if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
                    Toast.makeText(this, "CamApp3 requires access", Toast.LENGTH_SHORT).show();
                    requestPermissions(new String[] {Manifest.permission.CAMERA}, CAMERA_PERMISSION);
                }
            }


        }catch(CameraAccessException e){
            e.printStackTrace();
        }
    }


    //Preview uses background thread to prevent UI blocking
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void startPreview(){

        SurfaceTexture surfaceTexture = imagePreview.getSurfaceTexture();
        surfaceTexture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
        Surface previewSurface = new Surface(surfaceTexture);

        try {
            appCaptureRequest = appCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            appCaptureRequest.addTarget(previewSurface);

            appCamera.createCaptureSession(Arrays.asList(previewSurface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    try{
                        session.setRepeatingRequest(appCaptureRequest.build(),
                                null, appBackgroundHandler);
                    }catch (CameraAccessException e){
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    Toast.makeText(getApplicationContext(), "Failed to preview", Toast.LENGTH_SHORT).show();
                }
            }, null);

        } catch (CameraAccessException e){
            e.printStackTrace();
        }

    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onResume() {
        super.onResume();
        startThread();
        if (imagePreview.isAvailable()) {
            setupCamera(imagePreview.getWidth(), imagePreview.getHeight());
            startCamera();
        } else {
            imagePreview.setSurfaceTextureListener(imagePreviewListener);
        }
    }

    private void setupCamera(int width, int height) {
        CameraManager appCameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {

            //check if device have a camera
            int count = 0;
            for (String camID : appCameraManager.getCameraIdList()) {
                CameraCharacteristics cameraCharacteristics =
                        appCameraManager.getCameraCharacteristics(camID);

                if (cameraCharacteristics.get(CameraCharacteristics.LENS_FACING) ==
                        CameraCharacteristics.LENS_FACING_BACK){
                    appCameraIdBack = camID;
                    count++;
                }


                if (appCameraIdBack == null) continue;

                StreamConfigurationMap map =
                        cameraCharacteristics.get(cameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                int deviceOrient = getWindowManager().getDefaultDisplay().getRotation();
                TotalRotation = orientSensor(cameraCharacteristics, deviceOrient);
                boolean swapRotation = TotalRotation == 90 || TotalRotation == 270;
                int rotatedWidth = width;
                int rotateHeight = height;

                //detect landscape or portrait mode
                if (swapRotation) {
                    rotatedWidth = height;
                    rotatedWidth = width;
                }

                previewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class), rotatedWidth, rotateHeight);
                appVideoSize = chooseOptimalSize(map.getOutputSizes(MediaRecorder.class), rotatedWidth, rotateHeight);
            }

            //Device does not have a back facing camera/ or a camera
            if(count <= 0){
                Toast.makeText(getApplicationContext(), "Your device does not have a camera", Toast.LENGTH_SHORT).show();
            }


        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    public void captureVideo(View view) {

        Intent videoIntent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);

        if(videoIntent.resolveActivity(getPackageManager()) != null){
            startActivityForResult(videoIntent, VIDEO_REQUEST);
        }

    }

    public void capturePicture(View view) {
        Intent pictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if(pictureIntent.resolveActivity(getPackageManager()) != null){
            startActivityForResult(pictureIntent, PICTURE_REQUEST);
        }
    }

    private static Size chooseOptimalSize(Size[] choices, int width , int height){
        List<Size> big = new ArrayList<Size>();
        for(Size option : choices){
            if(option.getHeight() == option.getWidth() * height / width &&
                    option.getWidth() >= width && option.getHeight() >= height);
            big.add(option);
        }
        if(!big.isEmpty()){
            return Collections.min(big, new CompareSizeByArea());
        }else{
            return choices[0];
        }
    }

    private static int orientSensor(CameraCharacteristics cameraCharacteristics, int deviceOrient){
        int sensorOrient = cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        deviceOrient = ORIENTS.get(deviceOrient);
        return (sensorOrient + deviceOrient + 360) % 360;
    };

    private static class CompareSizeByArea implements Comparator<Size> {
        @Override
        public int compare(Size lhs, Size rhs){
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() /
                    (long) rhs.getWidth() * rhs.getWidth());
        }
    }


    private void startThread(){
        appBackgroundThread = new HandlerThread("camapp3");
        appBackgroundThread.start();
        appBackgroundHandler = new Handler(appBackgroundThread.getLooper());
    }


    private void stopThread(){
        appBackgroundThread.quitSafely();
        try {
            appBackgroundThread.join();
            appBackgroundThread = null;
            appBackgroundHandler = null;
        }catch (InterruptedException e){
            e.printStackTrace();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == VIDEO_REQUEST) {
            videoURI = data.getData();
        }

        if(requestCode == PICTURE_REQUEST){
            pictureURI = data.getData();
        }
    }
}
