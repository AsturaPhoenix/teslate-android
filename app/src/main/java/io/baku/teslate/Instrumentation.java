// Copyright 2015 The Vanadium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package io.baku.teslate;

public class Instrumentation extends android.app.Instrumentation {
    public static Instrumentation INSTANCE;

    public Instrumentation() {
        INSTANCE = this;
        /*final Intent intent = new Intent();
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setComponent(new ComponentName("io.baku.teslate", "io.baku.teslate.MainActivity"));
        getTargetContext().startActivity(intent);*/
    }
}
