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

import rx.Subscriber;
import rx.functions.Action1;

public class SimpleCastUploader extends Subscriber<Collection<Patch<byte[]>>> {
    private final Action1<Throwable> mOnError;
    private final URL mEndpoint;

    public SimpleCastUploader(final String name, final Action1<Throwable> onError) {
        mOnError = onError;
        try {
            mEndpoint = new URL("https://simplecast-1297.appspot.com/frame/" + name);
        } catch (final MalformedURLException e) {
            throw new IllegalArgumentException("Unable to connect to resource x", e);
        }
    }

    @Override
    public void onStart() {
        request(1);
    }

    @Override
    public void onNext(final Collection<Patch<byte[]>> patches) {
        try {
            Log.i("SIMPLECAST", "Uploading frame " + mEndpoint + " ...");
            final HttpURLConnection conn = (HttpURLConnection)mEndpoint.openConnection();
            try {
                conn.setDoOutput(true);
                conn.setRequestMethod("PUT");
                conn.setRequestProperty("Content-Type", "image/jpeg");
                int size = 0;
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
            } finally {
                conn.disconnect();
            }
        } catch (final IOException|RuntimeException e) {
            mOnError.call(e);
        }

        request(1);
    }

    @Override
    public void onError(final Throwable e) {
        mOnError.call(e);
    }

    @Override
    public void onCompleted() {
    }
}
