package com.example.mapdemo;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;

import org.tensorflow.lite.Interpreter;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
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
    ImageReader imageReader;
    ImageView iv_show;
    boolean inGazing = false;
    List<byte[]> frames;

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
        frames = new ArrayList<>();

        cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        faceDetect = new FaceDetect(con);

    }

    public void openCamera() {
        try {
            List<String> permissionList = new ArrayList<String>();
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA) !=
                    PackageManager.PERMISSION_GRANTED) {
                permissionList.add(Manifest.permission.CAMERA);
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

    private void initImageReader() {
        imageReader = ImageReader.newInstance(1920, 1080, ImageFormat.JPEG, 40);
        //处理拍照得到的临时照片
        imageReader.setOnImageAvailableListener(reader -> {
            // 拿到拍照照片数据
            Image image = reader.acquireLatestImage();
            if (image != null) {
                ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                byte[] bytes = new byte[buffer.capacity()];
                buffer.get(bytes);//由缓冲区存入字节数组
                image.close();
                if (inGazing) {
                    frames.add(bytes);
                }
            }
        }, mainHandler);
    }

    public void startRecord() {
        inGazing = true;
        frames.clear();
        initImageReader();
        try {
            final CaptureRequest.Builder mPreviewBuilder =
                    cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);

            List<Surface> surfaces = new ArrayList<>();
            Surface mSurface = imageReader.getSurface();
            surfaces.add(mSurface);
            mPreviewBuilder.addTarget(mSurface);

            // 创建CameraCaptureSession，该对象负责管理处理预览请求
            cameraDevice.createCaptureSession(surfaces,
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            if (cameraDevice == null)
                                return;
                            cameraCaptureSession = session;
                            CaptureRequest previewRequest = mPreviewBuilder.build();

                            //cameraCaptureSession.setRepeatingRequest(previewRequest, null, childHandler);
                            new Thread() {
                                public void run() {
                                    super.run();
                                    while (inGazing) {
                                        try {
                                            cameraCaptureSession.capture(previewRequest, null, childHandler);
                                        } catch (CameraAccessException e) {
                                            e.printStackTrace();
                                        }
                                        try {
                                            sleep(80);
                                        } catch (InterruptedException e) {
                                            e.printStackTrace();
                                        }
                                    }
                                }
                            }.start();


                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                            Toast.makeText(context, "配置失败", Toast.LENGTH_SHORT).show();

                        }
                    }, childHandler);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    public void closeRecord() {
        if (inGazing) {
            inGazing = false;
        }
        imageReader = null;
        Log.e("close", frames.size() + "");
    }

    // 将视频转化为帧
    @RequiresApi(api = Build.VERSION_CODES.N)

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

    public void startPredict() {
        new Thread() {
            @RequiresApi(api = Build.VERSION_CODES.N)
            @Override
            public void run() {
                super.run();
                predict();
            }
        }.start();

    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private void predict() {
        final int TOTAL_THREADS = 4;
        int frameNum = frames.size();
        if (frameNum <= 5) {
            Log.e("GetFrames", "the num of frames is too few to predict");
            return;
        }
        int count = 1;
        Map<Integer, float[]> treeMap = new TreeMap<>();
        ExecutorService taskExecutor  = Executors.newFixedThreadPool(TOTAL_THREADS);
        final CountDownLatch latch = new CountDownLatch(frameNum - 1);
        faceDetect.captureFace = false;
        // 先串行运行几次，直到captureFace为true，再将剩余的用多线程运行
        for (; count < frameNum; count++) {
            predictProcess(latch, count, treeMap);
            if (faceDetect.captureFace) {
                break;
            }
        }
        count++;
        // 开始多线程处理
        for (; count < frameNum; count++) {
            Log.e("xxxx", count - 1 + ";" + count * 80);
            final int num = count;
            Runnable run = () -> {
                predictProcess(latch, num, treeMap);
            };
            taskExecutor.execute(run);
        }
        try {
            //等待所有线程执行完毕
            latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        taskExecutor.shutdown();//关闭线程池
        Log.e("Thread", "task completed");
        PositionProcess pp = new PositionProcess();
        float level = pp.getDistance(treeMap);
        Log.e("Result", level + "");
        bdMap.zoomMap(level);
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private void predictProcess(final CountDownLatch latch, final int num, Map<Integer, float[]> treeMap) {
        Bitmap frame = byte2Bitmap(frames.get(num));
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
    }

    private Bitmap byte2Bitmap(byte[] bytes) {
        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        if (bitmap != null) {
            bitmap = adjustPhotoRotation(bitmap, 270);
        }
        return bitmap;
    }

    // 将bitmap进行旋转
    Bitmap adjustPhotoRotation(Bitmap bm, final int orientationDegree) {

        Matrix m = new Matrix();
        m.setRotate(orientationDegree, (float) bm.getWidth() / 2, (float) bm.getHeight() / 2);
        float targetX, targetY;
        if (orientationDegree == 90) {
            targetX = bm.getHeight();
            targetY = 0;
        } else if (orientationDegree == 270) {
            targetX = 0;
            targetY = bm.getWidth();
        }
        else {
            targetX = bm.getHeight();
            targetY = bm.getWidth();
        }

        final float[] values = new float[9];
        m.getValues(values);

        float x1 = values[Matrix.MTRANS_X];
        float y1 = values[Matrix.MTRANS_Y];

        m.postTranslate(targetX - x1, targetY - y1);

        Bitmap bm1 = Bitmap.createBitmap(bm.getHeight(), bm.getWidth(), Bitmap.Config.ARGB_8888);

        Paint paint = new Paint();
        Canvas canvas = new Canvas(bm1);
        canvas.drawBitmap(bm, m, paint);

        return bm1;
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public void deviceDestroy() {
        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
    }
}