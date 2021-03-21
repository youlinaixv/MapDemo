package com.example.mapdemo;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class CameraDemo {
    Context context;
    TextureView cameraView;
    CameraManager cameraManager;
    String cameraId;
    CameraDevice cameraDevice;
    Handler childHandler, mainHandler;
    ImageReader imageReader;
    CameraCaptureSession cameraCaptureSession;
    MediaRecorder mediaRecorder;
    String videoPath;
    boolean isRecording = false;


    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public void setUpCamera(Context con) {
        context = con;
        HandlerThread handlerThread = new HandlerThread("Camera2");
        handlerThread.start();
        childHandler = new Handler(handlerThread.getLooper());
        mainHandler = new Handler(Looper.getMainLooper());
        cameraId = "" + CameraCharacteristics.LENS_FACING_BACK;

        cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);

    }
    // 设置视频的存储路径
    private String getVideoFilePath(Context context) {
        final File dir = context.getExternalFilesDir(null);
        return (dir == null ? "" : (dir.getAbsolutePath() + "/"))
                + System.currentTimeMillis() + ".mp4";
    }
    // 初始化视频参数
    private void setUpMediaRecorder() throws IOException {
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        if (videoPath == null || videoPath.isEmpty()) {
            videoPath = getVideoFilePath(context);
        }
        mediaRecorder.setOutputFile(videoPath);
        Log.e("xxx", "videoPath " + videoPath);
        mediaRecorder.setVideoEncodingBitRate(10000000);
        mediaRecorder.setVideoFrameRate(30);
        mediaRecorder.setVideoSize(1920, 1080);
        mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        // 调整视频方向
        mediaRecorder.setOrientationHint(270);
        mediaRecorder.prepare();
    }

    public void openCamera() {
        try {
            List<String> permissionList = new ArrayList<String>();
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA) !=
                    PackageManager.PERMISSION_GRANTED) {
                permissionList.add(Manifest.permission.CAMERA);
            }
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
                    PackageManager.PERMISSION_GRANTED) {
                permissionList.add(Manifest.permission.RECORD_AUDIO);
            }
            if (!permissionList.isEmpty()) {
                //有权限未通过
                String[] permissions = permissionList.toArray(new String[permissionList.size()]);

                ActivityCompat.requestPermissions(MainActivity.getMainActivity(),
                        permissions, 1);
            }

            cameraManager.openCamera(cameraId, stateCallback, mainHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

    }

    public void closeCamera() {
        closeRecord();
        deviceDestroy();
    }

    // 摄像头监听
    private CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            cameraDevice = camera;
            //takePreview();  // 开启预览
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            deviceDestroy();
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            Toast.makeText(context, "摄像头开启失败", Toast.LENGTH_SHORT).show();
        }
    };

    public void startRecord() {
        try {
            mediaRecorder = new MediaRecorder();
            setUpMediaRecorder();
            final CaptureRequest.Builder mPreviewBuilder =
                    cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);

            List<Surface> surfaces = new ArrayList<>();
            Surface recorderSurface = mediaRecorder.getSurface();
            surfaces.add(recorderSurface);
            mPreviewBuilder.addTarget(recorderSurface);

            // 创建CameraCaptureSession，该对象负责管理处理预览请求
            cameraDevice.createCaptureSession(surfaces,
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            if (cameraDevice == null)
                                return;
                            cameraCaptureSession = session;
                            CaptureRequest previewRequest = mPreviewBuilder.build();

                            try {
                                cameraCaptureSession.setRepeatingRequest(previewRequest, null, childHandler);
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }

                            isRecording = true;
                            mediaRecorder.start();

                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                            Toast.makeText(context, "配置失败", Toast.LENGTH_SHORT).show();

                        }
                    }, childHandler);

        } catch (CameraAccessException | IOException e) {
            e.printStackTrace();
        }
    }

    public void closeRecord() {
        if (isRecording) {
            mediaRecorder.stop();
            mediaRecorder.reset();
            mediaRecorder.release();
            mediaRecorder = null;
            isRecording = false;
        }

        videoPath = null;
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public void deviceDestroy() {
        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
    }
}
