package com.example.mapdemo;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.os.Build;

import androidx.annotation.RequiresApi;

import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Objects;

public class Tracker {
    private static Context mContext;
    Interpreter mInterpreter;
    private static Tracker instance;

    public Tracker(Context context) {
        mContext = context;
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    Interpreter get() {
        try {
            if (Objects.isNull(mInterpreter))
                mInterpreter = new Interpreter(loadModelFile(mContext));
        } catch (IOException e) {
            e.printStackTrace();
        }
        return mInterpreter;
    }

    // 获取文件
    private MappedByteBuffer loadModelFile(Context context) throws IOException {
        AssetFileDescriptor fileDescriptor = context.getAssets().openFd
                ("model.tflite");
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }


}
