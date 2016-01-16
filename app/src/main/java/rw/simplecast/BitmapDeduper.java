// Copyright 2015 The Vanadium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package rw.simplecast;

import android.graphics.Bitmap;
import android.graphics.Color;

import rx.functions.Func1;

public class BitmapDeduper implements Func1<Bitmap, Boolean> {
    public static final int COLOR_THRESHOLD = 32;

    private static boolean diff(int a, int b) {
        return Math.abs(Color.red(a) - Color.red(b)) > COLOR_THRESHOLD ||
                Math.abs(Color.green(a) - Color.green(b)) > COLOR_THRESHOLD ||
                Math.abs(Color.blue(a) - Color.blue(b)) > COLOR_THRESHOLD;
    }

    private final int mPxThreshold;

    public BitmapDeduper(final int pxThreshold) {
        mPxThreshold = pxThreshold;
    }

    private boolean diff(final Bitmap a, final Bitmap b) {
        int acc = 0;
        for (int y = 0; y < a.getHeight(); y++) {
            for (int x = 0; x < a.getWidth(); x++) {
                if (a.getPixel(x, y) != b.getPixel(x, y)) {
                    acc++;
                }
                if (acc > mPxThreshold) {
                    return true;
                }
            }
        }

        return false;
    }

    private Bitmap mLast, mLastDuplicate;

    public Bitmap getLastDuplicate() {
        return mLastDuplicate;
    }

    @Override
    public Boolean call(final Bitmap bitmap) {
        final boolean diff = mLast == null ||
                mLast.getWidth() != bitmap.getWidth() ||
                mLast.getHeight() != bitmap.getHeight() ||
                diff(mLast, bitmap);
        if (diff) {
            mLastDuplicate = mLast;
        }
        mLast = bitmap;
        return diff;
    }
}
