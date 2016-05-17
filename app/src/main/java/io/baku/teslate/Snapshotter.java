// Copyright 2015 The Vanadium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package io.baku.teslate;

import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;

import java.nio.Buffer;
import java.util.concurrent.TimeUnit;

import rx.Observable;
import rx.schedulers.Schedulers;

public class Snapshotter {
    public static Observable<Bitmap> create(final MediaProjection mediaProjection,
                                            final long period, final int w, final int h) {
        return Observable.<Bitmap>create(s -> {
            final ImageReader imageReader = ImageReader.newInstance(w, h,
                    ImageFormat.JPEG, 2);
            final VirtualDisplay display = mediaProjection.createVirtualDisplay("Teslate", w, h,
                    96, DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC, imageReader.getSurface(), null,
                    null);

            s.add(Observable.interval(0, period, TimeUnit.MILLISECONDS)
                    .onBackpressureDrop()
                    .observeOn(Schedulers.io())
                    .doOnUnsubscribe(() -> {
                        display.release();
                        mediaProjection.stop();
                        imageReader.close();
                    })
                    .subscribe(x -> {
                        final Bitmap bmp;
                        try (final Image img = imageReader.acquireLatestImage()) {
                            if (img == null) return;
                            bmp = Bitmap.createBitmap(img.getWidth(), img.getHeight(),
                                    Bitmap.Config.ARGB_8888);
                            final Buffer buffer = img.getPlanes()[0].getBuffer().rewind();
                            bmp.copyPixelsFromBuffer(buffer);
                        }
                        s.onNext(bmp);
                    }, s::onError));
        }).share();
    }
}
