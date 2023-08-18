// Copyright 2015 The Vanadium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package io.baku.teslate;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.Rect;

import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import lombok.RequiredArgsConstructor;
import rx.functions.Func1;

@RequiredArgsConstructor
public class BitmapPatcher implements Func1<Bitmap, PatchSet<Bitmap>> {
    private static boolean diff(final int a, final int b, final int threshold) {
        return Math.abs(Color.red(a) - Color.red(b)) > threshold ||
                Math.abs(Color.green(a) - Color.green(b)) > threshold ||
                Math.abs(Color.blue(a) - Color.blue(b)) > threshold;
    }

    private final Settings mSettings;

    private static final long KEYFRAME_PERIOD = 30000;
    private static final int RES = 2, SCAN_RES = 2;

    private static final Matrix SCALE = new Matrix();
    static {
        SCALE.postScale(1.0f / RES, 1.0f / RES);
    }

    private List<Patch<Bitmap>> diff(final Bitmap orig, final Bitmap next) {
        final int colorThreshold = mSettings.getFrameColorThreshold();
        final PatchAccumulator p = new PatchAccumulator(SCAN_RES * 3);

        for (int y = 0, sy = 0; sy < orig.getHeight(); y += SCAN_RES, sy += RES * SCAN_RES) {
            for (int x = 0, sx = 0; sx < orig.getWidth(); x += SCAN_RES, sx += RES * SCAN_RES) {
                if (diff(orig.getPixel(sx, sy), next.getPixel(sx, sy), colorThreshold)) {
                    p.submit(x, y);
                }
            }
        }

        for (Rect r : p.clusters) {
            //noinspection CheckResult
            r.intersect(0, 0, orig.getWidth() / RES, orig.getHeight() / RES);
        }
        return ImmutableList.copyOf(Collections2.transform(p.clusters, r -> {
            final Bitmap downsampled = Bitmap.createBitmap(
                    next, r.left * RES, r.top * RES, r.width() * RES, r.height() * RES, SCALE, false);
            return new Patch<>(new Point(r.left, r.top), downsampled);
        }));
    }

    private Bitmap mLast;
    private long mLastKeyframe = Long.MIN_VALUE;

    public void invalidate() {
        mLast = null;
    }

    @Override
    public PatchSet<Bitmap> call(final Bitmap bitmap) {
        int sw = bitmap.getWidth() / RES, sh = bitmap.getHeight() / RES;
        Collection<Patch<Bitmap>> patches;
        if (mLast == null ||
                mLast.getWidth() != bitmap.getWidth() ||
                mLast.getHeight() != bitmap.getHeight() ||
                System.currentTimeMillis() - mLastKeyframe > KEYFRAME_PERIOD) {
            mLastKeyframe = System.currentTimeMillis();

            final Bitmap downsampled = Bitmap.createBitmap(
                    bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), SCALE, false);

            patches = Collections.singleton(new Patch<>(new Point(0, 0), downsampled));
        } else {
            patches = diff(mLast, bitmap);
            mLast.recycle();
        }
        mLast = bitmap;
        return new PatchSet<>(sw, sh, patches);
    }
}
