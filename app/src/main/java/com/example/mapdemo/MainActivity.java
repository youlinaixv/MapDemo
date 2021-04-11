package com.example.mapdemo;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.os.Build;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.TextureView;
import android.view.View;

import android.widget.Button;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {


    BDMap mBDMap;
    CameraDemo cameraDemo;
    static MainActivity mainActivity;
    Button gaze;
    boolean isCameraStart = false;

    @SuppressLint("ClickableViewAccessibility")
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mBDMap = new BDMap();
        mBDMap.initSDK(getApplicationContext());

        setContentView(R.layout.activity_main);
        cameraDemo = new CameraDemo();

        gaze = (Button) findViewById(R.id.start_act);

        // 按住按钮不松开的时间内的动作视为凝视手势，未开启摄像头时不可见
        gaze.setOnTouchListener(new MTouchListener());
        gaze.setVisibility(View.INVISIBLE);

        mainActivity = this;

        initBDMap();  // 初始化地图

        // 相机初始化
        initCamera();

        // 判断权限获取情况
        checkPermission();



    }

    public static MainActivity getMainActivity() {
        return mainActivity;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {

        switch (requestCode) {
            case 1:
                if (grantResults.length > 0) { //检查是否所有权限都通过
                    for (int result: grantResults) {
                        if (result != PackageManager.PERMISSION_GRANTED) {
                            Toast.makeText(MainActivity.this, "必须赋予所有权限才能使用",
                                    Toast.LENGTH_SHORT).show();
                            finish();
                            return;
                        }

                    }
                    //所有权限都通过
                    mBDMap.requestLocation();
                } else {
                    Toast.makeText(MainActivity.this, "发生未知错误",
                            Toast.LENGTH_SHORT).show();
                    finish();
                }
                break;
        }


    }

    public void checkPermission() {
        List<String> permissionList = new ArrayList<String>();
        if (ContextCompat.checkSelfPermission(MainActivity.this,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionList.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (ContextCompat.checkSelfPermission(MainActivity.this,
                Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            permissionList.add(Manifest.permission.READ_PHONE_STATE);
        }
        if (ContextCompat.checkSelfPermission(MainActivity.this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            permissionList.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }

        if (!permissionList.isEmpty()) {
            //有权限未通过
            String[] permissions = permissionList.toArray(new String[permissionList.size()]);
            ActivityCompat.requestPermissions(MainActivity.this,
                    permissions, 1);
        } else {
            mBDMap.requestLocation();
        }
    }

    public void initBDMap() {
        // 地图显示
        mBDMap.mapView = findViewById(R.id.map_view);
        mBDMap.setBaiduMap();
        // 设置标记点图标
        mBDMap.setMarkerIcon();

        // 定位
        mBDMap.setLocationClient(getApplicationContext());

        mBDMap.baiduMap.setMyLocationEnabled(true);

        // 地图事件监听
        mBDMap.mapLongClick();

        // Marker点击监听
        mBDMap.markClick();
        /*
        // 缩放控制
        mBDMap.zoomBar = findViewById(R.id.zoom_bar);
        mBDMap.zoomChange();
        */
    }


    // 返回到当前位置视图
    public void returnTo(View view) {
        mBDMap.firstLocate = true;
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public void initCamera() {
        cameraDemo.cameraView = findViewById(R.id.cameraView);
        cameraDemo.cameraView.setSurfaceTextureListener(new MTextureListener());
        // 未开启摄像头时，TextureView不可见
        cameraDemo.cameraView.setVisibility(View.INVISIBLE);
    }

    // 开启摄像头
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public void startCamera(View view) {
        if (isCameraStart) {
            // 关闭摄像头
            Toast.makeText(getApplicationContext(), "凝视模式关闭", Toast.LENGTH_SHORT).show();
            cameraDemo.closeCamera();
            isCameraStart = false;
            gaze.setVisibility(View.INVISIBLE);
            cameraDemo.cameraView.setVisibility(View.INVISIBLE);
        } else {
            // 开启摄像头
            Toast.makeText(getApplicationContext(), "凝视模式开启", Toast.LENGTH_SHORT).show();
            cameraDemo.setUpCamera(getApplicationContext(), mBDMap);
            cameraDemo.openCamera();
            isCameraStart = true;
            gaze.setVisibility(View.VISIBLE);
            cameraDemo.cameraView.setVisibility(View.VISIBLE);
        }
    }

    // 定义TextureListener
    private class MTextureListener implements TextureView.SurfaceTextureListener {
        @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
        @Override
        public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface,
                                              int width, int height) {
        }

        @Override
        public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface,
                                                int width, int height) {

        }

        @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
        @Override
        public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
            cameraDemo.deviceDestroy();
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {
        }
    }

    private class MTouchListener implements View.OnTouchListener {
        @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    if (isCameraStart) {
                        gaze.setBackgroundResource(R.drawable.btn_pressed);
                        cameraDemo.startRecord();
                        /*
                        Toast.makeText(getApplicationContext(), "凝视开始",
                                Toast.LENGTH_SHORT).show();
                        */
                    } else {
                        Toast.makeText(getApplicationContext(), "请先开启摄像头",
                                Toast.LENGTH_SHORT).show();
                    }
                    break;
                case MotionEvent.ACTION_UP:
                    if (isCameraStart) {
                        gaze.setBackgroundResource(R.drawable.btn_normal);
                        /*
                        Toast.makeText(getApplicationContext(), "凝视结束",
                                Toast.LENGTH_SHORT).show();
                        */
                        cameraDemo.closeRecord();

                        cameraDemo.startFaceDetect();
                    }
                    break;
                default:
                    break;
            }
            return true;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mBDMap.onDestory();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mBDMap.mapView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mBDMap.mapView.onPause();
    }

}

