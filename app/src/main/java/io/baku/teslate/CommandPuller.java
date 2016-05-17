// Copyright 2016 The Vanadium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package io.baku.teslate;

import com.google.common.io.ByteStreams;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;

public class CommandPuller {
    private final URL mEndpoint;

    public CommandPuller(final String name) {
        try {
            mEndpoint = new URL("https://teslate-server.appspot.com/command/" + name);
        } catch (final MalformedURLException e) {
            throw new IllegalArgumentException("Unable to connect to resource " + name, e);
        }
    }

    public String poll() throws IOException {
        final InputStream i = (InputStream)mEndpoint.getContent();
        return i == null? null : new String(ByteStreams.toByteArray(i));
    }
}
