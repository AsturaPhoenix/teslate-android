package io.baku.teslate;

import android.accessibilityservice.AccessibilityService;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.app.UiAutomation;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.PointF;
import android.media.projection.MediaProjectionManager;
import android.net.TrafficStats;
import android.os.IBinder;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.view.WindowMetrics;

import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
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

    public static final long
            SNAPSHOT_PERIOD = 250;

    private static final String NOTIFICATION_CHANNEL = "notifications";
    private static final int NOTIFICATION_ID = 1;

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
        final byte[] compressed = bmp.compress(Bitmap.CompressFormat.WEBP_LOSSY,
                30, bout) ? bout.toByteArray() : null;
        bmp.recycle();
        return compressed;
    }

    private Settings mSettings;
    private ErrorReporter mErrors;
    private Subscription mSubscription;
    private int mPayloadSize;
    private long mStartedAt;
    private long mInitTx;
    private final int[] mLat16 = new int[16];
    private int mLat16Ptr, mLat16Sum;

    private void onFatal(final Throwable t) {
        mErrors.call(t);
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

    private void startForeground() {
        final NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL,
                "Teslate", NotificationManager.IMPORTANCE_LOW);
        NotificationManager service = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        service.createNotificationChannel(channel);

        Notification notification =
                new Notification.Builder(this, NOTIFICATION_CHANNEL)
                        .build();

        startForeground(NOTIFICATION_ID, notification);
    }

    private WindowMetrics getWindowMetrics() {
        return ((WindowManager) getSystemService(Context.WINDOW_SERVICE))
                .getMaximumWindowMetrics();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        mStartedAt = System.currentTimeMillis();
        mInitTx = TrafficStats.getUidTxBytes(getApplicationInfo().uid);

        sRunning = true;

        mErrors = new ErrorReporter(this);

        startForeground();

        final MediaProjectionManager pm = (MediaProjectionManager) getSystemService(
                Context.MEDIA_PROJECTION_SERVICE);

        final Intent mpIntent = intent.getParcelableExtra(MP_INTENT);
        final int mpResult = intent.getIntExtra(MP_RESULT, 0);

        mSettings = new Settings(PreferenceManager.getDefaultSharedPreferences(this), mErrors);

        final WindowMetrics metrics = getWindowMetrics();
        final Observable<Bitmap> snaps = Snapshotter.create(
                pm.getMediaProjection(mpResult, mpIntent),
                SNAPSHOT_PERIOD, metrics.getBounds().width() + 8, // why???
                metrics.getBounds().height(), getResources().getConfiguration().densityDpi);

        final BitmapPatcher patcher = new BitmapPatcher(mSettings);

        final Subscription casting = snaps
                .map(patcher)
                .filter(p -> !p.patches.isEmpty())
                .map(x -> x.transform(CastingService::compress))
                .map(new Uploader(mSettings, patcher, mErrors))
                .subscribe(this::processMetrics, this::onFatal);

        final Subscription input = Observable.<String>create(s -> {
            final CommandPuller p = new CommandPuller(mSettings);

            while (!s.isUnsubscribed()) {
                final String c;
                try {
                    c = p.poll();
                } catch (Exception e) {
                    mErrors.call(e);
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

        return START_REDELIVER_INTENT;
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

    private PointF translateCoords(final String s) {
        final String[] coords = s.split(",");
        final float nx = Float.parseFloat(coords[0]), ny = Float.parseFloat(coords[1]);

        final WindowMetrics metrics = getWindowMetrics();
        return new PointF(nx * metrics.getBounds().width(),
                ny * metrics.getBounds().height());
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
        final WindowMetrics metrics = getWindowMetrics();

        final MotionEvent.PointerProperties[] p = new MotionEvent.PointerProperties[2];
        final MotionEvent.PointerCoords[][] c = new MotionEvent.PointerCoords[10][];


        if (delta > 0) {
            for (int t = 0; t < c.length; t++) {
                c[t] = new MotionEvent.PointerCoords[2];
                c[t][0] = new MotionEvent.PointerCoords();
                c[t][1] = new MotionEvent.PointerCoords();
                c[t][0].y = (UPPER_BASE - t * delta / (c.length - 1)) * metrics.getBounds().height();
                c[t][1].y = (LOWER_BASE + t * delta / (c.length - 1)) * metrics.getBounds().height();
            }
        } else {
            for (int t = 0; t < c.length; t++) {
                c[t] = new MotionEvent.PointerCoords[2];
                c[t][0] = new MotionEvent.PointerCoords();
                c[t][1] = new MotionEvent.PointerCoords();
                c[t][0].y = (UPPER_BASE + (c.length - 1 - t) * delta / (c.length - 1)) *
                        metrics.getBounds().height();
                c[t][1].y = (LOWER_BASE - (c.length - 1 - t) * delta / (c.length - 1)) *
                        metrics.getBounds().height();
            }
        }

        for (int i = 0; i < 2; i++) {
            p[i] = new MotionEvent.PointerProperties();
            p[i].id = i;
            p[i].toolType = MotionEvent.TOOL_TYPE_FINGER;

            for (int j = 0; j < c.length; j++) {
                c[j][i].x = metrics.getBounds().width() / 2;
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

            for (final String command : payload.split("\n")) {
                final String[] majorParts = command.split("\\|");

                try {
                    final UiAutomation ui = Instrumentation.INSTANCE.getUiAutomation();
                    final String cmd = majorParts[1];
                    if ("MD".equals(cmd)) {
                        final PointF pt = translateCoords(majorParts[2]);

                        final long downTime = SystemClock.uptimeMillis();
                        final MotionEvent evt =
                                createMotionEvent(downTime, MotionEvent.ACTION_DOWN, pt.x, pt.y);
                        ui.injectInputEvent(evt, true);
                        evt.recycle();
                    } else if ("MU".equals(cmd)) {
                        final PointF pt = translateCoords(majorParts[2]);

                        final long downTime = SystemClock.uptimeMillis();
                        final MotionEvent evt =
                                createMotionEvent(downTime, MotionEvent.ACTION_UP, pt.x, pt.y);
                        ui.injectInputEvent(evt, true);
                        evt.recycle();
                    } else if ("MM".equals(cmd)) {
                        final PointF pt = translateCoords(majorParts[2]);

                        final long downTime = SystemClock.uptimeMillis();

                        final MotionEvent evt =
                                createMotionEvent(downTime, MotionEvent.ACTION_MOVE, pt.x, pt.y);
                        ui.injectInputEvent(evt, true);
                        evt.recycle();
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

                try {
                    Thread.sleep(200);
                } catch (final InterruptedException e) {
                    Log.w(TAG, e);
                }
            }
        }
    }
}
