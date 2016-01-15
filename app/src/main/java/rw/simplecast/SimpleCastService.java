package rw.simplecast;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;
import android.widget.Toast;

import com.cloudinary.Cloudinary;
import com.google.common.collect.ImmutableMap;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.Buffer;
import java.util.Arrays;

import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;
import rx.util.async.Async;

public class SimpleCastService extends Service {
    public static final String
            MP_INTENT = "MP_INTENT",
            MP_RESULT = "MP_RESULT";

    public static boolean sRunning;

    private static final ImmutableMap<String, String> CLOUDINARY_CONFIG = ImmutableMap.of(
            "cloud_name", "lmuapu9iq",
            "api_key", "927145266681416",
            "api_secret", "uTijwScXIVwYUNYfeenCzscWH4U"
    );

    private MediaProjectionManager mProjectionManager;
    private MediaProjection mProjection;
    private ImageReader mImageReader;
    private VirtualDisplay mDisplay;
    private byte[] mLast;

    private int w = 768, h = 1280;

    private Handler mHandler;
    private Runnable mProclp = this::proclp;

    private Cloudinary mCloudinary;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        sRunning = true;

        mProjectionManager = (MediaProjectionManager) getSystemService(
                Context.MEDIA_PROJECTION_SERVICE);

        final Intent mpIntent = (Intent) intent.getParcelableExtra(MP_INTENT);
        final int mpResult = intent.getIntExtra(MP_RESULT, 0);

        mProjection = mProjectionManager.getMediaProjection(mpResult, mpIntent);

        mImageReader = ImageReader.newInstance(w, h, ImageFormat.JPEG, 2);

        mDisplay = mProjection.createVirtualDisplay("SimpleCast", w, h, 96,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC, mImageReader.getSurface(), null, null);

        mCloudinary = new Cloudinary(CLOUDINARY_CONFIG);

        mHandler = new Handler();
        proclp();

        return START_NOT_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        mHandler.removeCallbacks(mProclp);
        mHandler = null;
        mDisplay.release();
        mProjection.stop();
        mImageReader.close();

        sRunning = false;
    }

    private void proclp() {
        try {
            final byte[] data = snapshot();
            if (data != null && !Arrays.equals(data, mLast)) {
                upload(data);
                return;
            }
            queueNext();
        } catch (final RuntimeException e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
            e.printStackTrace();
            stopSelf();
        }
    }

    private void queueNext() {
        if (mHandler != null) {
            mHandler.postDelayed(mProclp, 500);
        }
    }

    private byte[] snapshot() {
        final Bitmap bmp;
        try (final Image img = mImageReader.acquireLatestImage()) {
            if (img == null)
                return null;

            final Buffer buffer = img.getPlanes()[0].getBuffer().rewind();
            bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            bmp.copyPixelsFromBuffer(buffer);
        }

        final ByteArrayOutputStream bout = new ByteArrayOutputStream();

        bmp.compress(Bitmap.CompressFormat.JPEG, 50, bout);
        return bout.toByteArray();
    }

    private void upload(byte[] data) {
        Async.start(() -> {
            try {
                Log.i("SIMPLECAST", "Uploading...");
                mCloudinary.uploader().upload(data, ImmutableMap.of(
                        "public_id", "nautilus",
                        "invalidate", "true"));
            } catch (final IOException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
            return null;
        }, Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        x -> queueNext(),
                        e -> {
                            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                            stopSelf();
                        }
                );
    }
}
