// Copyright 2015 The Vanadium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package io.baku.teslate;

import com.google.common.collect.Collections2;

import java.util.Collection;

import lombok.RequiredArgsConstructor;
import rx.functions.Func1;

@RequiredArgsConstructor
public class PatchSet<T> {
    public final int frameWidth;
    public final int frameHeight;
    public final Collection<Patch<T>> patches;

    public <U> PatchSet<U> transform(final Func1<T, U> fn) {
        return new PatchSet<>(frameWidth, frameHeight, Collections2.transform(patches,
                p -> p.transform(fn)));
    }
}
