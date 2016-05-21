// Copyright 2015 The Vanadium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package io.baku.teslate;

import android.util.Log;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;

import lombok.RequiredArgsConstructor;
import rx.functions.Action1;
import rx.functions.Func1;

public class Uploader implements Func1<PatchSet<byte[]>, Uploader.Stats> {
    private static final int PROTOCOL_VERSION = 0;
    private static final String TAG = Uploader.class.getSimpleName();

    @RequiredArgsConstructor
    public static class Stats {
        public final int size;
        public final int duration;
    }

    private Settings mSettings;
    private final BitmapPatcher mPatcher;
    private final Action1<Throwable> mOnError;

    public Uploader(final Settings settings, final BitmapPatcher patcher,
                    final Action1<Throwable> onError) {
        mSettings = settings;
        mPatcher = patcher;
        mOnError = onError;
    }

    @Override
    public Stats call(final PatchSet<byte[]> patchSet) {
        final long startedAt = System.currentTimeMillis();
        int size = 0;
        try {
            final URL endpoint = mSettings.getFrameEndpoint();
            Log.i(TAG, "Uploading frame " + endpoint + " ...");
            final HttpURLConnection conn = (HttpURLConnection) endpoint.openConnection();
            try {
                //conn.setConnectTimeout(2500);
                conn.setReadTimeout(4000);
                conn.setDoOutput(true);
                conn.setRequestMethod("PUT");
                conn.setRequestProperty("Content-Type", "image/webp");
                
                conn.getOutputStream().write(PROTOCOL_VERSION);
                try (final ObjectOutputStream o = new ObjectOutputStream(conn.getOutputStream())) {
                    o.writeInt(patchSet.frameWidth);
                    o.writeInt(patchSet.frameHeight);

                    for (final Patch<byte[]> p : patchSet.patches) {
                        o.writeInt(p.pt.x);
                        o.writeInt(p.pt.y);
                        o.writeInt(p.bmp.length);
                        o.write(p.bmp);
                        size += Integer.SIZE * 3 / 8 + p.bmp.length;
                    }
                }
                Log.i(TAG, "Uploaded frame " + endpoint +
                        " (" + conn.getResponseCode() + ", " + size + " B, " +
                        patchSet.patches.size() + " patches)");
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
