package com.example.mapdemo;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
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

import org.tensorflow.lite.Interpreter;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
    FaceDetect faceDetect;
    int MASK_SIZE = 25; // 要得到的脸部网格矩阵的尺寸
    BDMap bdMap = null;



    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public void setUpCamera(Context con, BDMap mBDMap) {
        context = con;
        bdMap = mBDMap;
        HandlerThread handlerThread = new HandlerThread("Camera2");
        handlerThread.start();
        childHandler = new Handler(handlerThread.getLooper());
        mainHandler = new Handler(Looper.getMainLooper());
        cameraId = "" + CameraCharacteristics.LENS_FACING_BACK;

        cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        faceDetect = new FaceDetect(con);

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
            try {
                mediaRecorder.stop();
                mediaRecorder.reset();
                mediaRecorder.release();
            } catch (RuntimeException e) {
                e.printStackTrace();
            }

            mediaRecorder = null;
            isRecording = false;
        }

        videoPath = null;
    }

    // 将视频转化为帧
    @RequiresApi(api = Build.VERSION_CODES.N)
    private void getFrames(List<Bitmap> videoFrames, String framesDirName) {
        MediaMetadataRetriever mMMR = new MediaMetadataRetriever();
        mMMR.setDataSource(gazeVideoPath);

        // 获取视频总长度
        String durationStr = mMMR.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
        if (durationStr == null) {
            Log.e("duration", "the gaze time is too short");
            removeGazeFile();
            return;
        }
        long duration = Long.valueOf(durationStr);
        Log.e("duration", durationStr);

        // 帧率
        float rate = 25;
        long deltaOfFrames = (long) (1000.0 / rate * 2);
        /*
        String thePath = context.getExternalFilesDir(null) + "/frames/" + framesDirName;
        new File(thePath).mkdirs();
        Log.e("framePath", thePath);
         */
        // 使用多线程处理每一帧,不选取0s对应的帧,即防止由于用户在凝视开始时刻的波动导致误差，将凝视点的起始点删除
        int framesCount = (int) Math.ceil(duration / deltaOfFrames) - 1;
        final int TOTAL_THREADS = framesCount / 4;
        if (TOTAL_THREADS <= 1) {
            Log.e("GetFrames", "the num of frames is too few to predict");
            removeGazeFile();
            return;
        }
        Map<Integer, float[]> treeMap = new TreeMap<>();
        ExecutorService taskExecutor  = Executors.newFixedThreadPool(TOTAL_THREADS);
        final CountDownLatch latch = new CountDownLatch(framesCount);
        for (int count = 1; count * deltaOfFrames < duration; count++) {
            Log.e("xxxx", count - 1 + ";" + count * deltaOfFrames);
            final int num = count;
            Runnable run = () -> {

                Bitmap frame = mMMR.getFrameAtTime(deltaOfFrames * num * 1000,
                        MediaMetadataRetriever.OPTION_CLOSEST);
                if (frame != null) {
                    videoFrames.add(frame);
                } else {
                    Log.e("Frame", "null");
                    latch.countDown();
                    return;
                }

                // 对图片进行面部检测
                float[][] arrMask = new float[1][MASK_SIZE * MASK_SIZE];
                Bitmap[] bitmapArr = null;
                bitmapArr = faceDetect.detect(frame, arrMask);

                if (bitmapArr[0] == null || bitmapArr[1] == null || bitmapArr[2] == null) {
                    Log.e("BitmapArray", "null");
                    latch.countDown();
                    return;
                }

                // 使用iTracker模型进行凝视点预测
                Tracker mTracker = new Tracker(context);
                Interpreter interpreter = mTracker.get();
                Object[] inputs = new Object[4];
                inputs[0] = bitmap2Arr(bitmapArr[1]); // 左眼
                inputs[1] = bitmap2Arr(bitmapArr[2]); // 右眼
                inputs[2] = bitmap2Arr(bitmapArr[0]); // 脸
                inputs[3] = arrMask;
                float[][] output = new float[1][2];
                Map<Integer, Object> outputs = new HashMap();
                outputs.put(0, output);

                interpreter.allocateTensors();
                interpreter.runForMultipleInputsOutputs(inputs, outputs);
                treeMap.put(num - 1, new float[]{output[0][0], output[0][1]});

                latch.countDown();

            };
            taskExecutor.execute(run);
            // 将得到的bitmap储存起来
            /*
            String name = thePath + "/" + count;
            new File(name).mkdir();
            generatePic(bitmapArr[0], name, "face");
            generatePic(bitmapArr[1], name, "lefteye");
            generatePic(bitmapArr[2], name, "righteye");*/

        }
        try {
            //等待所有线程执行完毕
            latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        taskExecutor.shutdown();//关闭线程池
        Log.e("Thread", "task completed");

        // 删除生成的视频文件
        removeGazeFile();

        for (Map.Entry<Integer, float[]> entry: treeMap.entrySet()) {
            Log.e("posInfo", entry.getKey() + " " + entry.getValue()[0] + " "
            + entry.getValue()[1]);
        }
        PositionProcess pp = new PositionProcess();
        float level = pp.getDistance(treeMap);
        Log.e("Result", level + "");
        bdMap.zoomMap(level);
    }

    private boolean removeGazeFile() {
        File gazeVideo = new File(gazeVideoPath);
        if (gazeVideo != null && gazeVideo.exists() && !gazeVideo.isDirectory()){
            Log.e("deleteVideo", "succeed");
            gazeVideo.delete();
            return true;
        }
        return false;
    }

    public float[][][][] bitmap2Arr(Bitmap bitmap) {
        float[][][][] result = new float[1][64][64][3];

        // 将bitmap转成rgb形式
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] intValues = new int[width * height];
        bitmap.getPixels(intValues, 0, width, 0, 0, width, height);
        for (int i = 0; i < intValues.length; i++) {
            final int value = intValues[i];
            int row = i / width;
            int col = i % width;
            result[0][row][col][0] = byte2Float((byte) ((value >> 16) & 0xFF)) / 255; // R
            result[0][row][col][1] = byte2Float((byte) ((value >> 8) & 0xFF)) / 255; // G
            result[0][row][col][2] = byte2Float((byte) (value & 0xFF)) / 255; // B
        }
        return result;
    }

    private float byte2Float(byte num) {
        int heightBit = (int) ((num >> 4) & 0x0F);
        int lowBit = (int) (0x0F & num);
        float result = heightBit * 16 + lowBit;
        return result;
    }

    private void generatePic(Bitmap bitmap, String thePath, String fileName) {
        String name = thePath + "/" +fileName + ".jpg";
        File file = new File(name);
        try {
            FileOutputStream out = new FileOutputStream(file);
            if (bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)) {
                out.flush();
                out.close();
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void startFaceDetect() {
        List<Bitmap> videoFrames = new ArrayList<>();

        String framesDirName = System.currentTimeMillis() + "";
        new Thread() {
            @RequiresApi(api = Build.VERSION_CODES.N)
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
