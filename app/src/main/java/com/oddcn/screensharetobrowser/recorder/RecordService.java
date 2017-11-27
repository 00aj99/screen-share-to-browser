package com.oddcn.screensharetobrowser.recorder;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.os.Binder;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.WindowManager;
import android.widget.Toast;

import com.oddcn.screensharetobrowser.RxBus;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.ObservableSource;
import io.reactivex.Observer;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;
import io.reactivex.schedulers.Schedulers;


public class RecordService extends Service {
    private static final String TAG = "RecordService";
    private MediaProjection mediaProjection;
    private MediaRecorder mediaRecorder;
    private ImageReader imageReader;
    private VirtualDisplay virtualDisplay;
    private WindowManager mWindowManager;
    private boolean running;
    private int width;
    private int height;
    private int dpi;

    private ScreenHandler screenHandler;

    private Image img;

    private RecordServiceListener recordServiceListener;

    public void setListener(RecordServiceListener listener) {
        recordServiceListener = listener;
    }

    public void removeListener() {
        recordServiceListener = null;
    }

    private class ScreenHandler extends Handler {
        public ScreenHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return new RecordBinder();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        HandlerThread serviceThread = new HandlerThread("service_thread",
                android.os.Process.THREAD_PRIORITY_BACKGROUND);
        serviceThread.start();
        running = false;
        mediaRecorder = new MediaRecorder();

        HandlerThread handlerThread = new HandlerThread("Screen Record");
        handlerThread.start();
        screenHandler = new ScreenHandler(handlerThread.getLooper());

        //get the size of the window
        mWindowManager = (WindowManager) getApplication().getSystemService(Context.WINDOW_SERVICE);
//        width = mWindowManager.getDefaultDisplay().getWidth() + 40;
        width = mWindowManager.getDefaultDisplay().getWidth();
        height = mWindowManager.getDefaultDisplay().getHeight();
        //height = 2300;
        Log.i(TAG, "onCreate: w is " + width + " h is " + height);
        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2);

        Disposable disposable =
                getByteBufferObservable()
                        .subscribeOn(Schedulers.io())
                        .map(new Function<ImageInfo, Bitmap>() {
                            @Override
                            public Bitmap apply(ImageInfo imageInfo) throws Exception {
                                Bitmap bitmap = Bitmap.createBitmap(imageInfo.width + imageInfo.rowPadding / imageInfo.pixelStride, imageInfo.height,
                                        Bitmap.Config.ARGB_8888);

                                bitmap.copyPixelsFromBuffer(imageInfo.byteBuffer);
                                bitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height);
                                return bitmap;
                            }
                        })
                        .subscribe(getBitmapConsumer());
        compositeDisposable.add(disposable);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopRecord();
        compositeDisposable.dispose();
    }

    public void setMediaProject(MediaProjection project) {
        mediaProjection = project;
    }

    public boolean isRunning() {
        return running;
    }

    public void setConfig(int width, int height, int dpi) {
        this.width = width;
        this.height = height;
        this.dpi = dpi;
    }

    public boolean startRecord() {
        if (mediaProjection == null || running) {
            return false;
        }
        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2);
        createVirtualDisplayForImageReader();
        running = true;
        if (recordServiceListener != null) {
            recordServiceListener.onRecorderStatusChanged(running);
        }
        return true;
    }

    public boolean stopRecord() {
        if (!running) {
            return false;
        }
        running = false;
        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }
        if (mediaProjection != null) {
            mediaProjection.stop();
            mediaProjection = null;
        }

        if (imageReader != null)
            imageReader.close();
        if (recordServiceListener != null) {
            recordServiceListener.onRecorderStatusChanged(running);
        }
        return true;
    }

    private void createVirtualDisplayForImageReader() {

        virtualDisplay = mediaProjection.createVirtualDisplay("MainScreen", width, height, dpi
                , DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC, imageReader.getSurface()
                , null, screenHandler);
        imageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader imageReader) {
                try {
                    img = imageReader.acquireLatestImage();
                    if (img != null) {
                        if (img.getPlanes()[0].getBuffer() == null) {
                            return;
                        }
                        int width = img.getWidth();
                        int height = img.getHeight();
                        Image.Plane[] planes = img.getPlanes();

                        ByteBuffer buffer = planes[0].getBuffer();
                        int pixelStride = planes[0].getPixelStride();
                        int rowStride = planes[0].getRowStride();
                        int rowPadding = rowStride - pixelStride * width;
                        if (img != null) {
                            img.close();
                        }
                        ImageInfo imageInfo = new ImageInfo(width, height, buffer, pixelStride, rowPadding);

                        imageInfoObservableEmitter.onNext(imageInfo);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }, screenHandler);
    }

    private CompositeDisposable compositeDisposable = new CompositeDisposable();

    private ObservableEmitter<ImageInfo> imageInfoObservableEmitter;

    private Observable<ImageInfo> getByteBufferObservable() {
        return Observable.create(new ObservableOnSubscribe<ImageInfo>() {
            @Override
            public void subscribe(ObservableEmitter<ImageInfo> e) throws Exception {
                imageInfoObservableEmitter = e;
            }
        });
    }

    private Consumer<Bitmap> getBitmapConsumer() {
        return new Consumer<Bitmap>() {
            @Override
            public void accept(Bitmap bitmap) throws Exception {
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG, 10, byteArrayOutputStream);

                byte[] b = byteArrayOutputStream.toByteArray();
                String base64Str = org.java_websocket.util.Base64.encodeBytes(b);

                RxBus.getDefault().post(base64Str);

                try {
                    byteArrayOutputStream.flush();
                    byteArrayOutputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    bitmap.recycle();
                }
            }
        };
    }

    public class RecordBinder extends Binder {
        public RecordService getRecordService() {
            return RecordService.this;
        }
    }

    private class ImageInfo {
        int width;
        int height;
        ByteBuffer byteBuffer;
        int pixelStride;
        int rowPadding;

        public ImageInfo(int width, int height, ByteBuffer byteBuffer, int pixelStride, int rowPadding) {
            this.width = width;
            this.height = height;
            this.byteBuffer = byteBuffer;
            this.pixelStride = pixelStride;
            this.rowPadding = rowPadding;
        }
    }
}