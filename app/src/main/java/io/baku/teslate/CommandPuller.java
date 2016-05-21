// Copyright 2016 The Vanadium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package io.baku.teslate;

import com.google.common.io.ByteStreams;

import java.io.IOException;
import java.io.InputStream;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class CommandPuller {
    private final Settings mSettings;

    public String poll() throws IOException {
        final InputStream i = (InputStream)mSettings.getCommandEndpoint().getContent();
        return i == null? null : new String(ByteStreams.toByteArray(i));
    }
}
