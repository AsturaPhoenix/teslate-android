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

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.drive.DriveContents;
import com.google.android.gms.drive.DriveFile;
import com.google.android.gms.drive.Metadata;
import com.google.android.gms.drive.MetadataChangeSet;
import com.google.android.gms.drive.query.Filters;
import com.google.android.gms.drive.query.Query;
import com.google.android.gms.drive.query.SearchableField;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.Buffer;
import java.util.Arrays;

public class SimpleCastService extends Service {
    public static final String
            MP_INTENT = "MP_INTENT",
            MP_RESULT = "MP_RESULT";

    public static boolean sRunning;

    public static GoogleApiClient mG;

    private MediaProjectionManager mProjectionManager;
    private MediaProjection mProjection;
    private ImageReader mImageReader;
    private VirtualDisplay mDisplay;
    private byte[] mLast;

    private int w = 768, h = 1280;

    private Handler mHandler;
    private Runnable mProclp = this::proclp;

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
        mDisplay.release();
        mProjection.stop();
        mImageReader.close();

        sRunning = false;
    }

    private void proclp() {
        try {
            if (mG != null) {
                final byte[] data = snapshot();
                if (data != null && !Arrays.equals(data, mLast)) {
                    upload(data);
                    return;
                }
            }
            queueNext();
        } catch (final RuntimeException e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
            e.printStackTrace();
            stopSelf();
        }
    }

    private void queueNext() {
        mHandler.postDelayed(mProclp, 2000);
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

        bmp.compress(Bitmap.CompressFormat.PNG, 96, bout);
        return bout.toByteArray();
    }

    private boolean write(final byte[] data, final DriveContents target) {
        try {
            target.getOutputStream().write(data);
            return true;
        } catch (final IOException e) {
            Toast.makeText(this, "Failed to write drive contents.", Toast.LENGTH_LONG)
                    .show();
            return false;
        }
    }

    private void upload(byte[] data) {
        final MetadataChangeSet md = new MetadataChangeSet.Builder()
                .setMimeType("image/png")
                .setTitle("SimpleCast.png")
                .build();

        Drive.DriveApi.query(mG, new Query.Builder()
                .addFilter(Filters.eq(SearchableField.TITLE, "SimpleCast.png"))
                .addFilter(Filters.eq(SearchableField.TRASHED, false))
                .build()).setResultCallback(qResult -> {
            Log.i("SIMPLECAST", "Found " + qResult.getMetadataBuffer().getCount() + " matches");
            if (qResult.getMetadataBuffer().getCount() == 0) {
                Drive.DriveApi.newDriveContents(mG)
                        .setResultCallback(cResult -> {
                            if (!cResult.getStatus().isSuccess()) {
                                Toast.makeText(this, "Failed to create drive contents.",
                                        Toast.LENGTH_LONG).show();
                                return;
                            }

                            write(data, cResult.getDriveContents());
                            Drive.DriveApi.getRootFolder(mG)
                                    .createFile(mG, md, cResult.getDriveContents())
                                    .setResultCallback(r -> {
                                        mLast = data;
                                        queueNext();
                                    });
                        });
            } else {
                final Metadata mmd = qResult.getMetadataBuffer().get(0);
                mmd.getDriveId()
                        .asDriveFile()
                        .open(mG, DriveFile.MODE_WRITE_ONLY, null)
                        .setResultCallback(oResult -> {
                            if (write(data, oResult.getDriveContents())) {
                                oResult.getDriveContents().commit(mG, md)
                                        .setResultCallback(r -> {
                                            mLast = data;
                                            queueNext();
                                        });
                            } else {
                                oResult.getDriveContents().discard(mG);
                                queueNext();
                            }
                        });
            }
            qResult.release();
        });
    }
}
