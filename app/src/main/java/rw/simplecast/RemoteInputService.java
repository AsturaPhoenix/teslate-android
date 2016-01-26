// Copyright 2015 The Vanadium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package rw.simplecast;

import android.accessibilityservice.AccessibilityService;
import android.app.UiAutomation;
import android.content.Context;
import android.graphics.Point;
import android.graphics.PointF;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.WindowManager;

import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.sns.AmazonSNSClient;
import com.amazonaws.services.sns.model.CreatePlatformEndpointRequest;
import com.google.android.gms.gcm.GcmListenerService;

import java.util.concurrent.TimeUnit;

import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;
import rx.util.async.Async;

public class RemoteInputService extends GcmListenerService {
    public static final String
            APP_ARN = "arn:aws:sns:us-west-1:014890975119:app/GCM/simplecast",
            SCE_TOPIC = "arn:aws:sns:us-west-1:014890975119:sce";

    public static boolean isPossible() {
        return Instrumentation.INSTANCE != null;
    }

    public static void subscribeToken(final String token) {
        final AmazonSNSClient sns = new AmazonSNSClient(Aws.CREDS);
        sns.setRegion(Region.getRegion(Regions.US_WEST_1));

        final String endpoint = sns.createPlatformEndpoint(new CreatePlatformEndpointRequest()
                .withPlatformApplicationArn(APP_ARN)
                .withToken(token))
                .getEndpointArn();

        final String subscription = sns.subscribe(SCE_TOPIC, "application", endpoint)
                .getSubscriptionArn();
        Log.i("SIMPLECAST", "Subscribed as " + subscription);
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

    @Override
    public void onMessageReceived(final String from, final Bundle data) {
        if (SimpleCastService.sRunning) {
            final String payload = data.getString("default");
            Log.i("SIMPLECAST", "Input message " + payload);

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
