// Copyright 2015 The Vanadium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package rw.simplecast;

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

public class Snapshotter {
    public static Observable<Bitmap> create(final MediaProjection mediaProjection,
                                            final int w, final int h) {

        return Observable.<Observable<Bitmap>>create(s -> {
            final ImageReader mImageReader = ImageReader.newInstance(w, h,
                    ImageFormat.JPEG, 2);
            final VirtualDisplay mDisplay = mediaProjection.createVirtualDisplay("SimpleCast", w, h,
                    96, DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC, mImageReader.getSurface(), null,
                    null);

            s.onNext(Observable.interval(0, 50, TimeUnit.MILLISECONDS)
                    .doOnUnsubscribe(() -> {
                        mDisplay.release();
                        mediaProjection.stop();
                        mImageReader.close();
                    })
                    .map(x -> {
                        final Bitmap bmp;
                        try (final Image img = mImageReader.acquireLatestImage()) {
                            if (img == null)
                                return null;

                            final Buffer buffer = img.getPlanes()[0].getBuffer().rewind();
                            bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                            bmp.copyPixelsFromBuffer(buffer);
                        }
                        return bmp;
                    })
                    .filter(x -> x != null));
        })
                .flatMap(y -> y)
                .share();
    }
}
