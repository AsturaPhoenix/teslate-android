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

import java.io.ByteArrayOutputStream;
import java.util.concurrent.TimeUnit;

import rx.Observable;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;
import rx.subjects.PublishSubject;
import rx.subscriptions.CompositeSubscription;
import rx.util.async.Async;

public class SimpleCastService extends Service {
    public static final String
            MP_INTENT = "MP_INTENT",
            MP_RESULT = "MP_RESULT";

    public static final String
            PREF_TOKEN = "TOKEN",
            PREF_LAST_ERROR = "LAST_ERROR",
            PREF_FRAME_THRESHOLD = "FRAME_THRESHOLD",
            PREF_FRAME_COLOR_THRESHOLD = "FRAME_COLOR_THRESHOLD",
            PREF_SCREEN_THRESHOLD = "SCREEN_THRESHOLD",
            PREF_SCREEN_COLOR_THRESHOLD = "SCREEN_COLOR_THRESHOLD",
            PREF_MIN_STABLE = "MIN_STABLE",
            PREF_SCREEN_DELAY = "SCREEN_DELAY";
    public static final float
            DEFAULT_FRAME_THRESHOLD = .005f,
            DEFAULT_SCREEN_THRESHOLD = .22f;
    public static final int
            DEFAULT_FRAME_COLOR_THRESHOLD = 32,
            DEFAULT_SCREEN_COLOR_THRESHOLD = 128;
    public static final long
            SNAPSHOT_PERIOD = 250,
            FRAME_PERIOD = 500,
            DEFAULT_MIN_STABLE = 3500,
            DEFAULT_SCREEN_DELAY = 2500;

    public static final int SCALE = 6;

    public static boolean sRunning;

    private static byte[] compress(final Bitmap bmp) {
        final ByteArrayOutputStream bout = new ByteArrayOutputStream();
        return bmp.compress(Bitmap.CompressFormat.JPEG, 45, bout) ? bout.toByteArray() : null;
    }

    private SharedPreferences mPrefs;
    private Subscription mSubscription;
    private PublishSubject<Throwable> mErrors = PublishSubject.create();

    private void onError(final Throwable t) {
        mErrors.onNext(t);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
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

        final Intent mpIntent = (Intent) intent.getParcelableExtra(MP_INTENT);
        final int mpResult = intent.getIntExtra(MP_RESULT, 0);

        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);

        final Observable<Bitmap> snaps = Snapshotter.create(
                pm.getMediaProjection(mpResult, mpIntent),
                SNAPSHOT_PERIOD, 72 * SCALE, 128 * SCALE);

        final Observable<Bitmap> screens = snaps.filter(new BitmapDeduper("Screens", mPrefs,
                PREF_SCREEN_THRESHOLD, DEFAULT_SCREEN_THRESHOLD,
                PREF_SCREEN_COLOR_THRESHOLD, DEFAULT_SCREEN_COLOR_THRESHOLD))
                .share();

        final long screenStable = mPrefs.getLong(PREF_MIN_STABLE, DEFAULT_MIN_STABLE);

        mSubscription = new CompositeSubscription(
                snaps.filter(new BitmapDeduper("Frames", mPrefs,
                        PREF_FRAME_THRESHOLD, DEFAULT_FRAME_THRESHOLD,
                        PREF_FRAME_COLOR_THRESHOLD, DEFAULT_FRAME_COLOR_THRESHOLD))
                        .sample(FRAME_PERIOD, TimeUnit.MILLISECONDS, Schedulers.io())
                        .map(SimpleCastService::compress)
                        .filter(j -> j != null)
                        .subscribe(new S3Uploader("nautilus.jpg", this::onError)),
                snaps.delay(mPrefs.getLong(PREF_SCREEN_DELAY, DEFAULT_SCREEN_DELAY),
                        TimeUnit.MILLISECONDS)
                        .window(screens)
                        .concatMap(w -> w.takeLast(1))
                        .window(screens.debounce(screenStable, TimeUnit.MILLISECONDS,
                                Schedulers.io()))
                        .concatMap(w -> w.take(1))
                        .map(SimpleCastService::compress)
                        .filter(j -> j != null)
                        .subscribe(new S3Uploader("previous.jpg", this::onError))
        );

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
