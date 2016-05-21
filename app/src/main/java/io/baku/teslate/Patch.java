// Copyright 2016 The Vanadium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package io.baku.teslate;

import android.graphics.Point;

import lombok.RequiredArgsConstructor;
import rx.functions.Func1;

@RequiredArgsConstructor
public class Patch<T> {
    public final Point pt;
    public final T bmp;

    public <U> Patch<U> transform(final Func1<T, U> fn) {
        return new Patch<>(pt, fn.call(bmp));
    }
}
