package com.example.mapdemo;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.media.ImageReader;
import android.media.MediaMetadataRetriever;
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
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
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
    CameraCaptureSession cameraCaptureSession;
    MediaRecorder mediaRecorder;
    String videoPath;
    String gazeVideoPath;  //当前凝视指令的路径
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
            gazeVideoPath = videoPath;
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

    private void getFrames(List<Bitmap> videoFrames, String framesDirName) {
        MediaMetadataRetriever mMMR = new MediaMetadataRetriever();
        mMMR.setDataSource(gazeVideoPath);

        // 获取视频总长度
        String durationStr = mMMR.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
        long duration = Long.valueOf(durationStr);
        Log.e("duration", durationStr);

        // 获取帧率
        float rate = 25;
        long deltaOfFrames = (long) (1000.0 / rate);
        String thePath = context.getExternalFilesDir(null) + "/frames/" + framesDirName;
        new File(thePath).mkdirs();

        Log.e("framePath", thePath);

        for (int count = 0; count * deltaOfFrames < duration; count++) {
            Log.e("xxxx", count + ";" + count * deltaOfFrames);
            Bitmap frame = mMMR.getFrameAtTime
                    (deltaOfFrames * count * 1000, MediaMetadataRetriever.OPTION_CLOSEST);
            if (frame != null) {
                videoFrames.add(frame);
            } else {
                break;
            }

            String name = thePath + "/" + count +".jpg";
            File file = new File(name);
            if(!file.exists()){
                try {
                    file.createNewFile();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            try {
                FileOutputStream out = new FileOutputStream(file);
                if (videoFrames.get(count).compress(Bitmap.CompressFormat.JPEG, 100, out)) {
                    out.flush();
                    out.close();
                }
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void startFaceDetect() {
        List<Bitmap> videoFrames = new ArrayList<>();
        String framesDirName = System.currentTimeMillis() + "";
        new Thread() {
            @Override
            public void run() {
                super.run();
                getFrames(videoFrames, framesDirName);

            }
        }.start();

    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public void deviceDestroy() {
        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
    }
}
