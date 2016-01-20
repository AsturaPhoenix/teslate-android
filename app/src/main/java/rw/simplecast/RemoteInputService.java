// Copyright 2015 The Vanadium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package rw.simplecast;

import android.content.Context;
import android.graphics.Point;
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

public class RemoteInputService extends GcmListenerService {
    public static final String
            APP_ARN = "arn:aws:sns:us-west-1:014890975119:app/GCM/simplecast",
            SCE_TOPIC = "arn:aws:sns:us-west-1:014890975119:sce";

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

    @Override
    public void onMessageReceived(final String from, final Bundle data) {
        final String payload = data.getString("default");
        Log.i("SIMPLECAST", "Input message " + payload);

        final String[] majorParts = payload.split("\\|");
        final String[] coords = majorParts[1].split(",");
        final float nx = Float.parseFloat(coords[0]), ny = Float.parseFloat(coords[1]);

        final WindowManager wm = (WindowManager)getSystemService(Context.WINDOW_SERVICE);
        final Point windowSize = new Point();
        wm.getDefaultDisplay().getSize(windowSize);
        final float x = nx * windowSize.x, y = ny * windowSize.y;

        try {
            /*mI.sendPointerSync(MotionEvent.obtain(SystemClock.uptimeMillis(),
                    SystemClock.uptimeMillis(), MotionEvent.ACTION_DOWN, x, y, 0));
            mI.sendPointerSync(MotionEvent.obtain(SystemClock.uptimeMillis(),
                    SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x, y, 0));*/
            final MotionEvent evt = MotionEvent.obtain(SystemClock.uptimeMillis(),
                    SystemClock.uptimeMillis(), MotionEvent.ACTION_DOWN, x, y, 0);
            evt.setSource(InputDevice.SOURCE_TOUCHSCREEN);
            rw.simplecast.Instrumentation.INSTANCE.getUiAutomation().injectInputEvent(evt, true);
            evt.setAction(MotionEvent.ACTION_UP);
            rw.simplecast.Instrumentation.INSTANCE.getUiAutomation().injectInputEvent(evt, true);
            evt.recycle();
        } catch (final RuntimeException e) {
            e.printStackTrace();
        }
    }
}
