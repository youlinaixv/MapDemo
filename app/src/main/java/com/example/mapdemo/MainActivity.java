package com.example.mapdemo;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.TextureView;
import android.view.View;

import android.widget.Toast;


import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {


    BDMap mBDMap;
    //CameraDemo cameraDemo;
    static MainActivity mainActivity;

    //@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mBDMap = new BDMap();
        mBDMap.initSDK(getApplicationContext());

        setContentView(R.layout.activity_main);
        //cameraDemo = new CameraDemo();



        mainActivity = this;

        initBDMap();  // 初始化地图

        // 相机初始化
        //initCamera();

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
        // 缩放控制
        mBDMap.zoomBar = findViewById(R.id.zoom_bar);
        mBDMap.zoomChange();

    }


    // 返回到当前位置视图
    public void returnTo(View view) {
        mBDMap.firstLocate = true;
    }

    //@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    /*
    public void initCamera() {
        cameraDemo.cameraView = (TextureView) findViewById(R.id.cameraView);
        Log.e("xxx", "init camera");
        cameraDemo.cameraView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
            @Override
            public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface,
                                                  int width, int height) {

                cameraDemo.setUpCamera(getApplicationContext());
                cameraDemo.openCamera();
            }

            @Override
            public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface,
                                                    int width, int height) {

            }

            @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
            @Override
            public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
                cameraDemo.deviceDestory();
                return false;
            }

            @Override
            public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {

            }
        });
    }
     */
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

