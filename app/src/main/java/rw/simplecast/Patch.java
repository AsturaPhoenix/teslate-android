// Copyright 2016 The Vanadium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package rw.simplecast;

import android.graphics.Point;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class Patch<T> {
    public final Point pt;
    public final T bmp;
}
