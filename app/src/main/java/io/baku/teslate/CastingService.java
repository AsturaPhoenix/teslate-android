package io.baku.teslate;

import android.accessibilityservice.AccessibilityService;
import android.app.Service;
import android.app.UiAutomation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.PointF;
import android.media.projection.MediaProjectionManager;
import android.net.TrafficStats;
import android.os.IBinder;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.widget.Toast;

import com.google.common.base.Throwables;
import com.google.common.collect.Lists;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import lombok.RequiredArgsConstructor;
import rx.Observable;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;
import rx.subjects.PublishSubject;
import rx.subscriptions.CompositeSubscription;
import rx.util.async.Async;

public class CastingService extends Service {
    private static final String TAG = CastingService.class.getSimpleName();

    public static final String
            MP_INTENT = "MP_INTENT",
            MP_RESULT = "MP_RESULT";

    public static final String
            PREF_LAST_ERROR = "LAST_ERROR",
            PREF_FRAME_COLOR_THRESHOLD = "FRAME_COLOR_THRESHOLD";
    public static final int
            DEFAULT_FRAME_COLOR_THRESHOLD = 8;
    public static final long
            SNAPSHOT_PERIOD = 250;

    public static final int SCALE = 6;

    public static boolean sRunning;

    @RequiredArgsConstructor
    public static class Status {
        public final long bytesPayload, bytesTx, uptime;
        public final int latency, lat16;
    }

    private static final PublishSubject<Status> sStatus = PublishSubject.create();

    public static Observable<Status> getStatus() {
        return sStatus;
    }

    private static byte[] compress(final Bitmap bmp) {
        final ByteArrayOutputStream bout = new ByteArrayOutputStream();
        return bmp.compress(Bitmap.CompressFormat.WEBP, 30, bout) ? bout.toByteArray() : null;
    }

    private SharedPreferences mPrefs;
    private Subscription mSubscription;
    private PublishSubject<Throwable> mErrors = PublishSubject.create();
    private int mPayloadSize;
    private long mStartedAt;
    private long mInitTx;
    private final int[] mLat16 = new int[16];
    private int mLat16Ptr, mLat16Sum;

    private void onError(final Throwable t) {
        mErrors.onNext(t);
    }

    private void onFatal(final Throwable t) {
        onError(t);
        stopSelf();
    }

    private void processMetrics(final Uploader.Stats stats) {
        mPayloadSize += stats.size;
        mLat16Sum += stats.duration - mLat16[mLat16Ptr];
        mLat16[mLat16Ptr] = stats.duration;
        mLat16Ptr = (mLat16Ptr + 1) % 16;

        sStatus.onNext(new Status(
                mPayloadSize,
                TrafficStats.getUidTxBytes(getApplicationInfo().uid) - mInitTx,
                System.currentTimeMillis() - mStartedAt,
                stats.duration,
                mLat16Sum >> 4));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        mStartedAt = System.currentTimeMillis();
        mInitTx = TrafficStats.getUidTxBytes(getApplicationInfo().uid);

        mErrors.distinctUntilChanged(Throwable::getMessage)
                .onBackpressureBuffer()
                .observeOn(AndroidSchedulers.mainThread())
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

        final BitmapPatcher patcher = new BitmapPatcher(mPrefs,
                PREF_FRAME_COLOR_THRESHOLD, DEFAULT_FRAME_COLOR_THRESHOLD);

        final Subscription casting = snaps
                .map(patcher)
                .filter(p -> !p.isEmpty())
                .map(x -> Lists.transform(x, p -> new Patch<>(p.pt, compress(p.bmp))))
                .map(new Uploader("nautilus", patcher, this::onError))
                .subscribe(this::processMetrics, this::onFatal);

        final Subscription input = Observable.<String>create(s -> {
            final CommandPuller p = new CommandPuller("nautilus");

            while (!s.isUnsubscribed()) {
                final String c;
                try {
                    c = p.poll();
                } catch (IOException e) {
                    onError(e);
                    continue;
                }

                if (c != null) {
                    s.onNext(c);
                }
            }
        })
                .subscribeOn(Schedulers.io())
                .subscribe(this::processCommand, this::onFatal);

        mSubscription = new CompositeSubscription(casting, input);

        //startInstrumentation(new ComponentName(this, Instrumentation.class), null, null);

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


    public static boolean isInputPossible() {
        return Instrumentation.INSTANCE != null;
    }

    //private final Instrumentation mI = new Instrumentation();

    private Point getScreenSize() {
        final WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        final Point windowSize = new Point();
        wm.getDefaultDisplay().getSize(windowSize);
        return windowSize;
    }

    private PointF translateCoords(final String s) {
        final String[] coords = s.split(",");
        final float nx = Float.parseFloat(coords[0]), ny = Float.parseFloat(coords[1]);

        final Point screenSize = getScreenSize();
        return new PointF(nx * screenSize.x, ny * screenSize.y);
    }

    private MotionEvent createMotionEvent(final long downTime, final int action,
                                          final float x, final float y) {
        final MotionEvent evt = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(),
                action, x, y, 0);
        evt.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        return evt;
    }

    private static final float MIN_DEVIATION = .075f,
            UPPER_BASE = .5f - MIN_DEVIATION,
            LOWER_BASE = .5f + MIN_DEVIATION;

    private void pinch(final UiAutomation ui, float delta) {
        final Point screenSize = getScreenSize();

        final MotionEvent.PointerProperties[] p = new MotionEvent.PointerProperties[2];
        final MotionEvent.PointerCoords[][] c = new MotionEvent.PointerCoords[10][];


        if (delta > 0) {
            for (int t = 0; t < c.length; t++) {
                c[t] = new MotionEvent.PointerCoords[2];
                c[t][0] = new MotionEvent.PointerCoords();
                c[t][1] = new MotionEvent.PointerCoords();
                c[t][0].y = (UPPER_BASE - t * delta / (c.length - 1)) * screenSize.y;
                c[t][1].y = (LOWER_BASE + t * delta / (c.length - 1)) * screenSize.y;
            }
        } else {
            for (int t = 0; t < c.length; t++) {
                c[t] = new MotionEvent.PointerCoords[2];
                c[t][0] = new MotionEvent.PointerCoords();
                c[t][1] = new MotionEvent.PointerCoords();
                c[t][0].y = (UPPER_BASE + (c.length - 1 - t) * delta / (c.length - 1)) *
                        screenSize.y;
                c[t][1].y = (LOWER_BASE - (c.length - 1 - t) * delta / (c.length - 1)) *
                        screenSize.y;
            }
        }

        for (int i = 0; i < 2; i++) {
            p[i] = new MotionEvent.PointerProperties();
            p[i].id = i;
            p[i].toolType = MotionEvent.TOOL_TYPE_FINGER;

            for (int j = 0; j < c.length; j++) {
                c[j][i].x = screenSize.x / 2;
                c[j][i].pressure = 1;
                c[j][i].size = 1;
            }
        }

        final long downTime = SystemClock.uptimeMillis();

        Observable<?> o = Async.start(() -> {
            {
                final MotionEvent evt = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(),
                        MotionEvent.ACTION_DOWN, 1, p, c[0], 0, 0, 1, 1, 0, 0,
                        InputDevice.SOURCE_TOUCHSCREEN, 0);
                ui.injectInputEvent(evt, true);
                evt.recycle();
            }
            {
                final MotionEvent evt = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(),
                        MotionEvent.ACTION_POINTER_DOWN +
                                (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                        2, p, c[0], 0, 0, 1, 1, 0, 0,
                        InputDevice.SOURCE_TOUCHSCREEN, 0);
                ui.injectInputEvent(evt, true);
                evt.recycle();
            }
            return null;
        }, AndroidSchedulers.mainThread())
                .delay(5, TimeUnit.MILLISECONDS, AndroidSchedulers.mainThread());

        for (int i = 1; i < c.length - 1; i++) {
            final int fi = i;
            o = o.map(x -> {
                final MotionEvent evt = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(),
                        MotionEvent.ACTION_MOVE, 2, p, c[fi], 0, 0, 1, 1, 0, 0,
                        InputDevice.SOURCE_TOUCHSCREEN, 0);
                ui.injectInputEvent(evt, true);
                evt.recycle();
                return null;
            }).delay(5, TimeUnit.MILLISECONDS, AndroidSchedulers.mainThread());
        }

        o.subscribe(x -> {
            {
                final MotionEvent evt = MotionEvent.obtain(downTime,
                        SystemClock.uptimeMillis(),
                        MotionEvent.ACTION_POINTER_UP +
                                (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                        2, p, c[c.length - 1], 0, 0, 1, 1, 0, 0,
                        InputDevice.SOURCE_TOUCHSCREEN, 0);
                ui.injectInputEvent(evt, true);
                evt.recycle();
            }
            {
                final MotionEvent evt = MotionEvent.obtain(downTime,
                        SystemClock.uptimeMillis(),
                        MotionEvent.ACTION_UP, 1, p, c[c.length - 1], 0, 0, 1, 1, 0, 0,
                        InputDevice.SOURCE_TOUCHSCREEN, 0);
                ui.injectInputEvent(evt, true);
                evt.recycle();
            }
        }, Throwable::printStackTrace);
    }

    public void processCommand(final String payload) {
        if (CastingService.sRunning) {
            Log.i(TAG, "Input message " + payload);

            if (!isInputPossible()) {
                return;
            }

            final String[] majorParts = payload.split("\\|");

            try {
                final UiAutomation ui = Instrumentation.INSTANCE.getUiAutomation();
                final String cmd = majorParts[0];
                if ("MC".equals(cmd)) {
                    final PointF pt = translateCoords(majorParts[1]);

                    final long downTime = SystemClock.uptimeMillis();
                    MotionEvent evt =
                            createMotionEvent(downTime, MotionEvent.ACTION_DOWN, pt.x, pt.y);
                    ui.injectInputEvent(evt, true);
                    evt.recycle();
                    evt = createMotionEvent(downTime, MotionEvent.ACTION_UP, pt.x, pt.y);
                    ui.injectInputEvent(evt, true);
                    evt.recycle();
                } else if ("MD".equals(cmd)) {
                    final PointF p1 = translateCoords(majorParts[1]),
                            p2 = translateCoords(majorParts[2]);
                    final long downTime = SystemClock.uptimeMillis();
                    Async.start(() -> {
                        final MotionEvent evt =
                                createMotionEvent(downTime, MotionEvent.ACTION_DOWN, p1.x, p1.y);
                        ui.injectInputEvent(evt, true);
                        evt.recycle();
                        return null;
                    }).delay(150, TimeUnit.MILLISECONDS)
                            .map(x -> {
                                final MotionEvent evt =
                                        createMotionEvent(downTime, MotionEvent.ACTION_MOVE,
                                                (2 * p1.x + p2.x) / 3, (2 * p1.y + p2.y) / 3);
                                ui.injectInputEvent(evt, true);
                                evt.recycle();
                                return null;
                            })
                            .delay(150, TimeUnit.MILLISECONDS)
                            .map(x -> {
                                final MotionEvent evt =
                                        createMotionEvent(downTime, MotionEvent.ACTION_MOVE,
                                                (p1.x + 2 * p2.x) / 3, (p1.y + 2 * p2.y) / 3);
                                ui.injectInputEvent(evt, true);
                                evt.recycle();
                                return null;
                            })
                            .delay(150, TimeUnit.MILLISECONDS)
                            .subscribe(x -> {
                                final MotionEvent evt =
                                        createMotionEvent(downTime, MotionEvent.ACTION_UP,
                                                p2.x, p2.y);
                                ui.injectInputEvent(evt, true);
                                evt.recycle();
                            }, Throwable::printStackTrace);
                } else if ("B".equals(cmd)) {
                    ui.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);
                } else if ("H".equals(cmd)) {
                    ui.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME);
                } else if ("R".equals(cmd)) {
                    ui.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS);
                } else if ("ZI".equals(cmd)) {
                    pinch(ui, .125f);
                } else if ("ZO".equals(cmd)) {
                    pinch(ui, -.11f);
                }
            } catch (final RuntimeException e) {
                e.printStackTrace();
            }
        }
    }
}
