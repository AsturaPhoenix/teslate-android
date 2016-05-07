// Copyright 2016 The Vanadium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package rw.simplecast;

import com.google.common.io.ByteStreams;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;

import rx.functions.Action1;

public class CommandPuller {
    private final URL mEndpoint;
    private final Action1<Throwable> mOnError;

    public CommandPuller(final String name, final Action1<Throwable> onError) {
        try {
            mEndpoint = new URL("https://simplecast-1297.appspot.com/command/" + name);
        } catch (final MalformedURLException e) {
            throw new IllegalArgumentException("Unable to connect to resource " + name, e);
        }
        mOnError = onError;
    }

    public String poll() throws IOException {
        final InputStream i = (InputStream)mEndpoint.getContent();
        return i == null? null : new String(ByteStreams.toByteArray(i));
    }
}
