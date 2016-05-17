// Copyright 2016 The Vanadium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package io.baku.teslate;

import android.graphics.Rect;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class PatchAccumulator {
    private final int mRes;
    public final Collection<Rect> clusters = new ArrayList<>();

    public void submit(int x, int y) {
        Rect cluster = null, neighborhood = new Rect(x - mRes, y - mRes, x + mRes, y + mRes);

        for (final Iterator<Rect> iter = clusters.iterator(); iter.hasNext();) {
            final Rect candidate = iter.next();

            if (candidate.intersects(x - mRes - 2, y - mRes - 2, x + mRes + 2, y + mRes + 2)) {
                if (cluster == null) {
                    cluster = candidate;
                    cluster.union(neighborhood.left, neighborhood.top);
                    cluster.union(neighborhood.right, neighborhood.bottom);
                } else {
                    cluster.union(candidate.left, candidate.top);
                    cluster.union(candidate.right, candidate.bottom);
                    iter.remove();
                }
            }
        }


        if (cluster == null) {
            clusters.add(neighborhood);
        }
    }
}
