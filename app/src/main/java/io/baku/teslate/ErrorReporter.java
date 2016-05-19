// Copyright 2015 The Vanadium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package io.baku.teslate;

import android.content.Context;
import android.widget.Toast;

import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.subjects.PublishSubject;

public class ErrorReporter implements Action1<Throwable> {
    private final PublishSubject<Throwable> mErrors = PublishSubject.create();

    public ErrorReporter(final Context context) {
        mErrors.distinctUntilChanged(Throwable::getMessage)
                .onBackpressureBuffer()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(t -> {
                    try {
                        Toast.makeText(context, t.getMessage(), Toast.LENGTH_LONG).show();
                        t.printStackTrace();
                    } catch (final Throwable t2) {
                        t2.printStackTrace();
                    }
                });
    }

    @Override
    public void call(Throwable t) {
        mErrors.onNext(t);
    }
}
