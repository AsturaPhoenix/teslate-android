// Copyright 2015 The Vanadium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package io.baku.teslate;

import android.graphics.Bitmap;
import android.graphics.ColorSpace;
import android.graphics.ImageFormat;
import android.graphics.PixelFormat;
import android.hardware.HardwareBuffer;
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
                                            final long period, final int w, final int h, final int dpi) {
        return Observable.<Bitmap>create(s -> {
            final ImageReader imageReader = ImageReader.newInstance(w, h,
                    PixelFormat.RGBA_8888, 2);
            final VirtualDisplay display = mediaProjection.createVirtualDisplay("Teslate", w, h,
                    dpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC, imageReader.getSurface(), null,
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
                            try (final HardwareBuffer buffer = img.getHardwareBuffer()) {
                                final Bitmap source = Bitmap.wrapHardwareBuffer(
                                        buffer, null);
                                bmp = source.copy(Bitmap.Config.RGB_565, false);
                                source.recycle();
                            }
                        }
                        s.onNext(bmp);
                    }, s::onError));
        }).share();
    }
}
