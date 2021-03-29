package com.example.mapdemo;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.media.FaceDetector;
import android.widget.Toast;

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
    public void detect(Bitmap bitmap, Bitmap bitmapFace, Bitmap bitmapLeftEye,
                       Bitmap bitmapRightEye, int[][] arrMask) {
        // 需要转化为rgb565
        bitmap = bitmap.copy(Bitmap.Config.RGB_565, true);

        lengthToEven(bitmap);
        FaceDetector faceDetector = new FaceDetector(bitmap.getWidth(),
                bitmap.getHeight(), MAX_FACES);
        FaceDetector.Face[] faces = new FaceDetector.Face[MAX_FACES];
        int realFaceNum = faceDetector.findFaces(bitmap, faces);
        if (realFaceNum <= 0) {
            Toast.makeText(context, "没有检测到人脸", Toast.LENGTH_SHORT).show();
            return;
        }
        faceProcess(faces, realFaceNum, bitmap, bitmapFace, bitmapLeftEye, bitmapRightEye, arrMask);
    }

    // 对图像进行处理：裁剪+缩放
    private Bitmap imageProcess(Bitmap bitmap, int rectX, int rectY, int length, int destLength) {
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
    private void zeroOrOne(Bitmap bitmap, int[][] arrMask) {
        int edge = bitmap.getWidth();
        for (int i = 0; i < edge; i++) {
            for (int j = 0; j < edge; j++) {
                if (bitmap.getPixel(i, j) != Color.WHITE) {
                    arrMask[i][j] = 1;
                } else {
                    arrMask[i][j] = 0;
                }
            }
        }
    }

    // 根据面部检测的结果处理图片，获得脸部、左右眼和脸部网格
    private void faceProcess(FaceDetector.Face[] faces, int realFaceNum, Bitmap bitmap,
                             Bitmap bitmapFace, Bitmap bitmapLeftEye, Bitmap bitmapRightEye,
                             int[][] arrMask) {
        float eyesDistance = 0f;//两眼间距
        FaceDetector.Face face = faces[0]; // 暂时假定每次都只检测到一张脸
        if(face != null){
            PointF pointF = new PointF();
            face.getMidPoint(pointF); //获取中心点
            eyesDistance = face.eyesDistance(); //获取人脸两眼的间距



            bitmapFace = imageProcess(bitmap, (int) (pointF.x - eyesDistance),
                    (int) (pointF.y - eyesDistance * 0.5), (int) (eyesDistance * 2), PIC_SIZE);

            bitmapLeftEye = imageProcess(bitmap,
                    (int) (pointF.x - eyesDistance * (EYE_BOUND + 0.5)),
                    (int) (pointF.y - eyesDistance * EYE_BOUND),
                    (int) (eyesDistance * 2 * EYE_BOUND), PIC_SIZE);

            bitmapRightEye = imageProcess(bitmap,
                    (int) (pointF.x + eyesDistance * (0.5 - EYE_BOUND)),
                    (int) (pointF.y - eyesDistance * EYE_BOUND),
                    (int) (eyesDistance * 2 * EYE_BOUND), PIC_SIZE);

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
