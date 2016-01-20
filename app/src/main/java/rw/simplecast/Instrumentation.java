// Copyright 2015 The Vanadium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package rw.simplecast;

public class Instrumentation extends android.app.Instrumentation {
    public static Instrumentation INSTANCE;

    public Instrumentation() {
        INSTANCE = this;
    }
}
