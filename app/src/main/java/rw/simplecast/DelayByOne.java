// Copyright 2015 The Vanadium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package rw.simplecast;

public class DelayByOne<T> {
    public final T previous, current;

    public DelayByOne(final T prev, final T cur) {
        previous = prev;
        current = cur;
    }

    public static <T> DelayByOne<T> scan(final DelayByOne<T> delayByOne, final T t) {
        return new DelayByOne<>(delayByOne == null? null : delayByOne.current, t);
    }
}
