package com.example.mapdemo;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.media.FaceDetector;
import android.util.Log;

public class FaceDetect {
    Context context;
    int PIC_SIZE = 64; // 要得到的脸部、左右眼图像的尺寸
    int MASK_SIZE = 25; // 要得到的脸部网格矩阵的尺寸
    int MAX_FACES = 2; // 能检测到的最多人脸数量
    double EYE_BOUND = 0.4; // 眼睛部分裁剪时边框长度的一半与两眼间距的比值

    public FaceDetect(Context con) {
        context = con;
    }

    // 将图片的宽高转化为偶数，否则进行面部检测会检测不到
    private void lengthToEven(Bitmap bitmap) {
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        if (w % 2 == 1) {
            w++;
            bitmap = Bitmap.createScaledBitmap(bitmap,
                    bitmap.getWidth()+1, bitmap.getHeight(), false);
        }
        if (h % 2 == 1) {
            h++;
            bitmap = Bitmap.createScaledBitmap(bitmap,
                    bitmap.getWidth(), bitmap.getHeight()+1, false);
        }
    }

    // 面部检测
    public Bitmap[] detect(Bitmap bitmap, float[][] arrMask) {
        Bitmap[] bitmapArr = new Bitmap[3]; // 存储要得到的图像
        // 需要转化为rgb565
        bitmap = bitmap.copy(Bitmap.Config.RGB_565, true);

        lengthToEven(bitmap);
        FaceDetector faceDetector = new FaceDetector(bitmap.getWidth(),
                bitmap.getHeight(), MAX_FACES);
        FaceDetector.Face[] faces = new FaceDetector.Face[MAX_FACES];
        int realFaceNum = faceDetector.findFaces(bitmap, faces);
        if (realFaceNum <= 0) {
            Log.e("detect", "没有检测到人脸");
            return null;
        }

        faceProcess(faces, realFaceNum, bitmap, bitmapArr, arrMask);
        return bitmapArr;
    }

    // 对图像进行处理：裁剪+缩放
    private Bitmap imageProcess(Bitmap bitmap, int rectX, int rectY, int length, int destLength) {
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        // 当截取的正方形部分超出原图像边界时，不进行预测，改为提醒用户调整姿势

        if (rectX + length > w || rectY + length > h) {
            Log.e("clip fail", "请调整姿势以尽量露出全脸");
            return null;
        }
        Bitmap bitmapClip = Bitmap.createBitmap(bitmap, rectX, rectY, length, length,
                null, false);
        Bitmap bitmapScale = imageScale(bitmapClip, destLength, destLength);
        return bitmapScale;
    }

    // 对图像进行缩放
    private Bitmap imageScale(Bitmap bitmap, int dst_w, int dst_h) {
        int src_w = bitmap.getWidth();
        int src_h = bitmap.getHeight();
        float scale_w = ((float) dst_w) / src_w;
        float scale_h = ((float) dst_h) / src_h;
        Matrix matrix = new Matrix();
        matrix.postScale(scale_w, scale_h);
        Bitmap dstbmp = Bitmap.createBitmap(bitmap, 0, 0, src_w, src_h, matrix, true);
        return dstbmp;
    }

    // 根据mask图像的像素值为arrMask矩阵赋值
    private void zeroOrOne(Bitmap bitmap, float[][] arrMask) {
        int edge = bitmap.getWidth();
        for (int i = 0; i < edge * edge; i++) {
            int row = i / edge;
            int col = i % edge;
            if (bitmap.getPixel(row, col) != Color.WHITE) {
                arrMask[0][i] = 1;
            } else {
                arrMask[0][i] = 0;
            }

        }
    }

    // 根据面部检测的结果处理图片，获得脸部、左右眼和脸部网格
    private void faceProcess(FaceDetector.Face[] faces, int realFaceNum, Bitmap bitmap,
            Bitmap[] bitmapArr, float[][] arrMask) {

        float eyesDistance = 0f;//两眼间距
        FaceDetector.Face face = faces[0]; // 暂时假定每次都只检测到一张脸
        if(face != null){
            PointF pointF = new PointF();
            face.getMidPoint(pointF); //获取中心点
            eyesDistance = face.eyesDistance(); //获取人脸两眼的间距



            Bitmap bitmapFace = imageProcess(bitmap, (int) (pointF.x - eyesDistance),
                    (int) (pointF.y - eyesDistance * 0.5), (int) (eyesDistance * 2), PIC_SIZE);

            Bitmap bitmapLeftEye = imageProcess(bitmap,
                    (int) (pointF.x - eyesDistance * (EYE_BOUND + 0.5)),
                    (int) (pointF.y - eyesDistance * EYE_BOUND),
                    (int) (eyesDistance * 2 * EYE_BOUND), PIC_SIZE);

            Bitmap bitmapRightEye = imageProcess(bitmap,
                    (int) (pointF.x + eyesDistance * (0.5 - EYE_BOUND)),
                    (int) (pointF.y - eyesDistance * EYE_BOUND),
                    (int) (eyesDistance * 2 * EYE_BOUND), PIC_SIZE);

            if (bitmapFace == null || bitmapLeftEye == null || bitmapRightEye == null) {
                String str = "";
                if (bitmapFace == null) {
                    str += "face,";
                }
                if (bitmapLeftEye == null) {
                    str += "lefteye,";
                }
                if (bitmapRightEye == null) {
                    str += "righteye,";
                }
                Log.e("bitmapNull", str);
                return;
            }

            bitmapArr[0] = bitmapFace;
            bitmapArr[1] = bitmapLeftEye;
            bitmapArr[2] = bitmapRightEye;



            // 获取脸部网格
            int edge = (int) (eyesDistance * 2);
            int[] pixels = new int[edge * edge];
            for (int j = 0; j < edge * edge; j++) {
                pixels[j] = Color.BLACK;
            }

            Bitmap bitmapMask = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(),
                    Bitmap.Config.ARGB_8888);
            bitmapMask.eraseColor(Color.WHITE);
            bitmapMask.setPixels(pixels, 0, edge, (int) (pointF.x - eyesDistance),
                    (int) (pointF.y - eyesDistance * 0.5), edge, edge);
            bitmapMask = imageScale(bitmapMask, MASK_SIZE, MASK_SIZE);

            zeroOrOne(bitmapMask, arrMask);
        }
    }
}
