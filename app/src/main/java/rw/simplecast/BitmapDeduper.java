// Copyright 2015 The Vanadium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package rw.simplecast;

import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;

import rx.functions.Func1;

public class BitmapDeduper implements Func1<Bitmap, Boolean> {
    private static boolean diff(final int a, final int b, final int threshold) {
        return Math.abs(Color.red(a) - Color.red(b)) > threshold ||
                Math.abs(Color.green(a) - Color.green(b)) > threshold ||
                Math.abs(Color.blue(a) - Color.blue(b)) > threshold;
    }

    private final String mName;
    private final SharedPreferences mPrefs;
    private final String mFrameThreshKey, mColorThreshKey;
    private final float mFrameThreshDefault;
    private final int mColorThreshDefault;

    public BitmapDeduper(final String name, final SharedPreferences prefs,
                         final String frameThreshKey, final float frameThreshDefault,
                         final String colorThreshKey, final int colorThreshDefault) {
        mName = name;
        mPrefs = prefs;
        mFrameThreshKey = frameThreshKey;
        mFrameThreshDefault = frameThreshDefault;
        mColorThreshKey = colorThreshKey;
        mColorThreshDefault = colorThreshDefault;
    }

    private boolean diff(final Bitmap a, final Bitmap b) {
        final int colorThreshold = mPrefs.getInt(mColorThreshKey, mColorThreshDefault);
        final int pxThreshold = (int)(mPrefs.getFloat(mFrameThreshKey, mFrameThreshDefault)
                * a.getHeight() * a.getWidth());

        int acc = 0;
        for (int y = 0; y < a.getHeight(); y++) {
            for (int x = 0; x < a.getWidth(); x++) {
                if (diff(a.getPixel(x, y), b.getPixel(x, y), colorThreshold)) {
                    acc++;
                }
            }
        }
        if (acc > pxThreshold) {
            System.out.println(mName + " diff: " + acc * 100f / (a.getWidth() * a.getHeight()) + "%");
            return true;
        } else if (acc > pxThreshold / 2) {
            System.out.println(mName + " diff: " + acc * 100f / (a.getWidth() * a.getHeight()) + "% (below threshold)");
        }

        return false;
    }

    private Bitmap mLast;

    @Override
    public Boolean call(final Bitmap bitmap) {
        boolean diff = mLast == null ||
                mLast.getWidth() != bitmap.getWidth() ||
                mLast.getHeight() != bitmap.getHeight() ||
                diff(mLast, bitmap);
        mLast = bitmap;
        return diff;
    }
}
