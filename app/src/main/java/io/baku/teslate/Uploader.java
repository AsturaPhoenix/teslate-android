// Copyright 2015 The Vanadium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package io.baku.teslate;

import android.util.Log;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.Collection;

import lombok.RequiredArgsConstructor;
import rx.functions.Action1;
import rx.functions.Func1;

public class Uploader implements Func1<Collection<Patch<byte[]>>, Uploader.Stats> {
    private static final String TAG = Uploader.class.getSimpleName();

    @RequiredArgsConstructor
    public static class Stats {
        public final int size;
        public final int duration;
    }

    private final URL mEndpoint;
    private final BitmapPatcher mPatcher;
    private final Action1<Throwable> mOnError;

    public Uploader(final String name, final BitmapPatcher patcher,
                    final Action1<Throwable> onError) {
        try {
            mEndpoint = new URL("https://teslate-server.appspot.com/frame/" + name +
                    "/frame.jpeg");
        } catch (final MalformedURLException e) {
            throw new IllegalArgumentException("Unable to connect to resource " + name, e);
        }
        mPatcher = patcher;
        mOnError = onError;
    }

    @Override
    public Stats call(final Collection<Patch<byte[]>> patches) {
        final long startedAt = System.currentTimeMillis();
        int size = 0;
        try {
            Log.i(TAG, "Uploading frame " + mEndpoint + " ...");
            final HttpURLConnection conn = (HttpURLConnection) mEndpoint.openConnection();
            try {
                //conn.setConnectTimeout(2500);
                conn.setReadTimeout(4000);
                conn.setDoOutput(true);
                conn.setRequestMethod("PUT");
                conn.setRequestProperty("Content-Type", "image/webp");
                try (final ObjectOutputStream o = new ObjectOutputStream(conn.getOutputStream())) {
                    for (final Patch<byte[]> p : patches) {
                        o.writeInt(p.pt.x);
                        o.writeInt(p.pt.y);
                        o.writeInt(p.bmp.length);
                        o.write(p.bmp);
                        size += Integer.SIZE * 3 / 8 + p.bmp.length;
                    }
                }
                Log.i(TAG, "Uploaded frame " + mEndpoint +
                        " (" + conn.getResponseCode() + ", " + size + " B, " +
                        patches.size() + " patches)");
                if (conn.getResponseCode() != 200) {
                    mOnError.call(new IOException(conn.getResponseMessage() == null ?
                            "HTTP " + conn.getResponseCode() :
                            conn.getResponseMessage() + " (" + conn.getResponseCode() + ")"));
                    mPatcher.invalidate();
                }
            } finally {
                conn.disconnect();
            }
        } catch (final SocketTimeoutException e) {
            mPatcher.invalidate();
        } catch (final IOException | RuntimeException e) {
            mOnError.call(e);
            mPatcher.invalidate();
        }
        return new Stats(size, (int)(System.currentTimeMillis() - startedAt));
    }
}
