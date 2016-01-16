package rw.simplecast;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.projection.MediaProjectionManager;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.TimeUnit;

import rx.Observable;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;
import rx.subscriptions.CompositeSubscription;
import rx.util.async.Async;

public class SimpleCastService extends Service {
    public static final String
            MP_INTENT = "MP_INTENT",
            MP_RESULT = "MP_RESULT";

    public static boolean sRunning;

    private static byte[] compress(final Bitmap bmp) {
        final ByteArrayOutputStream bout = new ByteArrayOutputStream();
        bmp.compress(Bitmap.CompressFormat.JPEG, 45, bout);
        return bout.toByteArray();
    }

    private Subscription mSubscription;

    private void onError(final Throwable t) {
        Async.start(() -> {
            Toast.makeText(this, t.getMessage(), Toast.LENGTH_LONG).show();
            t.printStackTrace();
            return null;
        }, AndroidSchedulers.mainThread());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        sRunning = true;

        final MediaProjectionManager pm = (MediaProjectionManager) getSystemService(
                Context.MEDIA_PROJECTION_SERVICE);

        final Intent mpIntent = (Intent) intent.getParcelableExtra(MP_INTENT);
        final int mpResult = intent.getIntExtra(MP_RESULT, 0);

        final Observable<Bitmap> snaps = Snapshotter.create(
                pm.getMediaProjection(mpResult, mpIntent), 576, 960);

        final BitmapDeduper prevDedup = new BitmapDeduper(500 * 900);

        mSubscription = new CompositeSubscription(
                snaps.filter(new BitmapDeduper(2000))
                        .sample(200, TimeUnit.MILLISECONDS)
                        .onBackpressureLatest()
                        .observeOn(Schedulers.io())
                        .map(SimpleCastService::compress)
                        .subscribe(new S3Uploader("nautilus.jpg", this::onError)),
                snaps.filter(prevDedup)
                        .sample(1000, TimeUnit.MILLISECONDS)
                        .map(x -> prevDedup.getLastDuplicate())
                        .filter(x -> x != null)
                        .onBackpressureLatest()
                        .observeOn(Schedulers.io())
                        .map(SimpleCastService::compress)
                        .subscribe(new S3Uploader("previous.jpg", this::onError))
        );

        return START_NOT_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        mSubscription.unsubscribe();
        sRunning = false;
    }
}
