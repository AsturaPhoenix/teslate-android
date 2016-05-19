// Copyright 2015 The Vanadium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package io.baku.teslate;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.Rect;

import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;

import java.util.Collections;
import java.util.List;

import lombok.RequiredArgsConstructor;
import rx.functions.Func1;

@RequiredArgsConstructor
public class BitmapPatcher implements Func1<Bitmap, List<Patch<Bitmap>>> {
    private static boolean diff(final int a, final int b, final int threshold) {
        return Math.abs(Color.red(a) - Color.red(b)) > threshold ||
                Math.abs(Color.green(a) - Color.green(b)) > threshold ||
                Math.abs(Color.blue(a) - Color.blue(b)) > threshold;
    }

    private final Settings mSettings;

    private static final long KEYFRAME_PERIOD = 30000;
    private static final int RES = 5;

    private List<Patch<Bitmap>> diff(final Bitmap orig, final Bitmap next) {
        final int colorThreshold = mSettings.getFrameColorThreshold();
        final PatchAccumulator p = new PatchAccumulator(RES * 3);

        for (int y = 0; y < orig.getHeight(); y += RES) {
            for (int x = 0; x < orig.getWidth(); x += RES) {
                if (diff(orig.getPixel(x, y), next.getPixel(x, y), colorThreshold)) {
                    p.submit(x, y);
                }
            }
        }

        for (Rect r : p.clusters) {
            //noinspection CheckResult
            r.intersect(0, 0, orig.getWidth() - 1, orig.getHeight() - 1);
        }
        return ImmutableList.copyOf(Collections2.transform(p.clusters, r ->
                new Patch<>(new Point(r.left, r.top), Bitmap.createBitmap(
                        next, r.left, r.top, r.width() + 1, r.height() + 1))));
    }

    private Bitmap mLast;
    private long mLastKeyframe = Long.MIN_VALUE;

    public void invalidate() {
        mLast = null;
    }

    @Override
    public List<Patch<Bitmap>> call(final Bitmap bitmap) {
        List<Patch<Bitmap>> ret;
        if (mLast == null ||
                mLast.getWidth() != bitmap.getWidth() ||
                mLast.getHeight() != bitmap.getHeight() ||
                System.currentTimeMillis() - mLastKeyframe > KEYFRAME_PERIOD) {
            mLastKeyframe = System.currentTimeMillis();
            ret = Collections.singletonList(new Patch<>(new Point(0, 0), bitmap));
        } else {
            ret = diff(mLast, bitmap);
        }
        mLast = bitmap;
        return ret;
    }
}
