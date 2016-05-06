package rw.simplecast;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.media.projection.MediaProjectionManager;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.gcm.GoogleCloudMessaging;
import com.google.android.gms.iid.InstanceID;
import com.google.common.base.Throwables;
import com.google.common.collect.Lists;

import java.io.ByteArrayOutputStream;

import lombok.RequiredArgsConstructor;
import rx.Observable;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;
import rx.subjects.PublishSubject;
import rx.util.async.Async;

public class SimpleCastService extends Service {
    public static final String
            MP_INTENT = "MP_INTENT",
            MP_RESULT = "MP_RESULT";

    public static final String
            PREF_TOKEN = "TOKEN",
            PREF_LAST_ERROR = "LAST_ERROR",
            PREF_FRAME_COLOR_THRESHOLD = "FRAME_COLOR_THRESHOLD";
    public static final int
            DEFAULT_FRAME_COLOR_THRESHOLD = 8;
    public static final long
            SNAPSHOT_PERIOD = 200;

    public static final int SCALE = 6;

    public static boolean sRunning;

    @RequiredArgsConstructor
    public static class Status {
        public final long bytesSent;
        public final long uptime;
        public final int latency;
        public final int lat16;
    }

    private static final PublishSubject<Status> sStatus = PublishSubject.create();

    public static Observable<Status> getStatus() {
        return sStatus;
    }

    private static byte[] compress(final Bitmap bmp) {
        final ByteArrayOutputStream bout = new ByteArrayOutputStream();
        return bmp.compress(Bitmap.CompressFormat.JPEG, 45, bout) ? bout.toByteArray() : null;
    }

    private SharedPreferences mPrefs;
    private Subscription mSubscription;
    private PublishSubject<Throwable> mErrors = PublishSubject.create();
    private int mPayloadSize;
    private long mStartedAt;
    private final int[] mLat16 = new int[16];
    private int mLat16Ptr, mLat16Sum;

    private void onError(final Throwable t) {
        mErrors.onNext(t);
    }

    private void onFatal(final Throwable t) {
        onError(t);
        stopSelf();
    }

    private void processMetrics(final SimpleCastUploader.Stats stats) {
        mPayloadSize += stats.size;
        mLat16Sum += stats.duration - mLat16[mLat16Ptr];
        mLat16[mLat16Ptr] = stats.duration;
        mLat16Ptr = (mLat16Ptr + 1) % 16;

        sStatus.onNext(new Status(
                mPayloadSize,
                System.currentTimeMillis() - mStartedAt,
                stats.duration,
                mLat16Sum >> 4));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        mStartedAt = System.currentTimeMillis();

        mErrors.observeOn(AndroidSchedulers.mainThread())
                .distinctUntilChanged(Throwable::getMessage)
                .subscribe(t -> {
                    try {
                        PreferenceManager.getDefaultSharedPreferences(this).edit()
                                .putString(PREF_LAST_ERROR, Throwables.getStackTraceAsString(t))
                                .apply();
                        Toast.makeText(this, t.getMessage(), Toast.LENGTH_LONG).show();
                        t.printStackTrace();
                    } catch (final Throwable t2) {
                        PreferenceManager.getDefaultSharedPreferences(this).edit()
                                .putString(PREF_LAST_ERROR, Throwables.getStackTraceAsString(t2))
                                .apply();
                        t2.printStackTrace();
                    }
                });

        sRunning = true;

        final MediaProjectionManager pm = (MediaProjectionManager) getSystemService(
                Context.MEDIA_PROJECTION_SERVICE);

        final Intent mpIntent = intent.getParcelableExtra(MP_INTENT);
        final int mpResult = intent.getIntExtra(MP_RESULT, 0);

        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);

        final Observable<Bitmap> snaps = Snapshotter.create(
                pm.getMediaProjection(mpResult, mpIntent),
                SNAPSHOT_PERIOD, 72 * SCALE, 128 * SCALE);

        mSubscription = snaps
                .onBackpressureLatest()
                .observeOn(Schedulers.io())
                .map(new BitmapPatcher(mPrefs,
                        PREF_FRAME_COLOR_THRESHOLD, DEFAULT_FRAME_COLOR_THRESHOLD))
                .filter(p -> !p.isEmpty())
                .map(x -> Lists.transform(x, p -> new Patch<>(p.pt, compress(p.bmp))))
                .map(new SimpleCastUploader("nautilus", this::onError))
                .subscribe(this::processMetrics, this::onFatal);

        registerGcm();

        //startInstrumentation(new ComponentName(this, Instrumentation.class), null, null);

        return START_NOT_STICKY;
    }

    private void registerGcm() {
        Log.i("SIMPLECAST", "App ID: " + getString(R.string.google_app_id));
        final String prefToken = mPrefs.getString(PREF_TOKEN, null);
        (prefToken == null ?
                Async.fromCallable(() -> InstanceID.getInstance(this).getToken(
                        getString(R.string.gcm_defaultSenderId),
                        GoogleCloudMessaging.INSTANCE_ID_SCOPE))
                        .doOnNext(t -> mPrefs.edit().putString(PREF_TOKEN, t).apply()) :
                Observable.just(prefToken))
                .observeOn(Schedulers.io())
                .subscribe(t -> {
                    Log.i("SIMPLECAST", "GCM Token: " + t);
                    RemoteInputService.subscribeToken(t);
                }, this::onError);
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
