// Copyright 2015 The Vanadium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package rw.simplecast;

import android.util.Log;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;

import lombok.RequiredArgsConstructor;
import rx.functions.Action1;
import rx.functions.Func1;

public class SimpleCastUploader
        implements Func1<Collection<Patch<byte[]>>, SimpleCastUploader.Stats> {
    @RequiredArgsConstructor
    public static class Stats {
        public final int size;
        public final int duration;
    }

    private final URL mEndpoint;
    private final Action1<Throwable> mOnError;

    public SimpleCastUploader(final String name, final Action1<Throwable> onError) {
        try {
            mEndpoint = new URL("https://simplecast-1297.appspot.com/frame/" + name +
                    "/frame.jpeg");
        } catch (final MalformedURLException e) {
            throw new IllegalArgumentException("Unable to connect to resource " + name, e);
        }
        mOnError = onError;
    }

    @Override
    public Stats call(final Collection<Patch<byte[]>> patches) {
        final long startedAt = System.currentTimeMillis();
        int size = 0;
        try {
            Log.i("SIMPLECAST", "Uploading frame " + mEndpoint + " ...");
            final HttpURLConnection conn = (HttpURLConnection) mEndpoint.openConnection();
            try {
                conn.setDoOutput(true);
                conn.setRequestMethod("PUT");
                conn.setRequestProperty("Content-Type", "image/jpeg");
                try (final ObjectOutputStream o = new ObjectOutputStream(conn.getOutputStream())) {
                    for (final Patch<byte[]> p : patches) {
                        o.writeInt(p.pt.x);
                        o.writeInt(p.pt.y);
                        o.writeInt(p.bmp.length);
                        o.write(p.bmp);
                        size += Integer.SIZE * 3 / 8 + p.bmp.length;
                    }
                }
                Log.i("SIMPLECAST", "Uploaded frame " + mEndpoint +
                        " (" + conn.getResponseCode() + ", " + size + " B, " +
                        patches.size() + " patches)");
                if (conn.getResponseCode() != 200) {
                    mOnError.call(new IOException(conn.getResponseMessage() == null ?
                            "HTTP " + conn.getResponseCode() :
                            conn.getResponseMessage() + " (" + conn.getResponseCode() + ")"));
                }
            } finally {
                conn.disconnect();
            }
        } catch (final IOException | RuntimeException e) {
            mOnError.call(e);
        }
        return new Stats(size, (int)(System.currentTimeMillis() - startedAt));
    }
}
