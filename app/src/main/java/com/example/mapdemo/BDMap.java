package com.example.mapdemo;

import android.content.Context;
import android.graphics.Point;
import android.os.Build;
import android.util.Log;
import android.widget.SeekBar;
import android.widget.ZoomControls;

import androidx.annotation.RequiresApi;

import com.baidu.location.BDAbstractLocationListener;
import com.baidu.location.BDLocation;
import com.baidu.location.LocationClient;
import com.baidu.location.LocationClientOption;
import com.baidu.mapapi.SDKInitializer;
import com.baidu.mapapi.map.BaiduMap;
import com.baidu.mapapi.map.BitmapDescriptor;
import com.baidu.mapapi.map.BitmapDescriptorFactory;
import com.baidu.mapapi.map.MapPoi;
import com.baidu.mapapi.map.MapStatusUpdate;
import com.baidu.mapapi.map.MapStatusUpdateFactory;
import com.baidu.mapapi.map.MapView;
import com.baidu.mapapi.map.Marker;
import com.baidu.mapapi.map.MarkerOptions;
import com.baidu.mapapi.map.MyLocationData;
import com.baidu.mapapi.map.OverlayOptions;
import com.baidu.mapapi.map.UiSettings;
import com.baidu.mapapi.model.LatLng;

public class BDMap {

    MapView mapView;
    BaiduMap baiduMap = null;
    LocationClient locationClient;
    boolean firstLocate = true;
    BitmapDescriptor bitmapDescriptor, bitmapDescriptorCenter;  // 显示目标位置的图标(普通和被选中)
    SeekBar zoomBar;
    final int LEVEL = 3;
    float zoomLevel;
    boolean zoomEnd = false;
    LatLng zoomCenter = null;
    Marker pointCenter = null;

    private class LocationListener extends BDAbstractLocationListener {

        @Override
        public void onReceiveLocation(BDLocation bdLocation) {

            navigateTo(bdLocation);
        }
    }


    public void initSDK(Context context) {
        SDKInitializer.initialize(context);
    }

    public void setBaiduMap() {
        baiduMap = mapView.getMap();
        baiduMap.setMapType(BaiduMap.MAP_TYPE_NORMAL);
        // 取消双击放大
        UiSettings mUiSettings = baiduMap.getUiSettings();
        mUiSettings.setTwoTouchClickZoomEnabled(false);
        mUiSettings.setDoubleClickZoomEnabled(false);

        // 调整缩放按钮的显示位置
        baiduMap.setOnMapLoadedCallback(new BaiduMap.OnMapLoadedCallback() {
            @Override
            public void onMapLoaded() {
                mapView.setZoomControlsPosition(new Point(900, 1200));
            }
        });

    }

    public void setMarkerIcon() {
        bitmapDescriptor = BitmapDescriptorFactory.fromResource(R.drawable.mark);
        bitmapDescriptorCenter = BitmapDescriptorFactory.fromResource
                (R.drawable.mark_center);
    }

    public void setLocationClient(Context context) {
        locationClient = new LocationClient(context);
        locationClient.registerLocationListener(new LocationListener());
    }

    public void mapLongClick() {
        baiduMap.setOnMapLongClickListener(latLng -> {
            zoomCenter = latLng;

            OverlayOptions option = new MarkerOptions().position(latLng).
                    icon(bitmapDescriptorCenter);

            if (pointCenter != null) {
                pointCenter.setIcon(bitmapDescriptor);
            }
            pointCenter = (Marker) baiduMap.addOverlay(option);
        });
    }

    // 双击地图开启凝视模式，打开摄像头


    public void markClick() {
        // 点击Marker时，Marker被选中
        baiduMap.setOnMarkerClickListener(new BaiduMap.OnMarkerClickListener() {
            @Override
            public boolean onMarkerClick(Marker marker) {
                if (pointCenter != null) {
                    pointCenter.setIcon(bitmapDescriptor);
                }
                pointCenter = marker;
                zoomCenter = marker.getPosition();
                marker.setIcon(bitmapDescriptorCenter);
                return true;
            }
        });
    }

    public void zoomChange() {

        zoomBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!zoomEnd) {
                    float zoom_scale = progress - LEVEL;
                    float finalLevel = zoomLevel + zoom_scale;
                    MapStatusUpdate update = MapStatusUpdateFactory.zoomTo(finalLevel);
                    baiduMap.animateMapStatus(update);
                }

            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                zoomLevel = baiduMap.getMapStatus().zoom;
                baiduMap.setMapStatus(MapStatusUpdateFactory.newLatLng(zoomCenter));
                zoomEnd = false;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                zoomEnd = true;
                seekBar.setProgress(3);
            }
        });
    }

    private void initLocation() {
        LocationClientOption option = new LocationClientOption();

        option.setLocationMode(LocationClientOption.LocationMode.Hight_Accuracy);//设置高精度定位定位模式
        option.setCoorType("bd09ll");
        option.setScanSpan(1000);
        option.setOpenGps(true);
        option.setLocationNotify(true);
        option.setIgnoreKillProcess(true);
        option.setIsNeedAddress(true);
        option.setIsNeedLocationDescribe(true);
        option.setIsNeedLocationPoiList(true);


        locationClient.setLocOption(option);
    }

    public void requestLocation() {
        initLocation();
        locationClient.start();
    }

    private void navigateTo(BDLocation bdLocation) {
        LatLng ll = new LatLng(bdLocation.getLatitude(), bdLocation.getLongitude());
        if (firstLocate) {
            MapStatusUpdate update = MapStatusUpdateFactory.newLatLng(ll);
            baiduMap.animateMapStatus(update);
            update = MapStatusUpdateFactory.zoomTo(16f);
            baiduMap.animateMapStatus(update);
            zoomCenter = ll;
            firstLocate = false;
        }

        MyLocationData.Builder myBuilder = new MyLocationData.Builder();
        myBuilder.longitude(ll.longitude);
        myBuilder.latitude(ll.latitude);
        MyLocationData myData = myBuilder.build();
        baiduMap.setMyLocationData(myData);
    }

    public void onDestory() {
        mapView.onDestroy();
        baiduMap.setMyLocationEnabled(false);
        locationClient.stop();
    }

}
