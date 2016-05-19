// Copyright 2015 The Vanadium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package io.baku.teslate;

import android.content.SharedPreferences;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Objects;

import lombok.RequiredArgsConstructor;
import rx.functions.Action1;

@RequiredArgsConstructor
public class Settings {
    private static final String
            PREF_SESSION_ID = "SESSION_ID",
            PREF_FRAME_COLOR_THRESHOLD = "FRAME_COLOR_THRESHOLD";
    public static final int
            DEFAULT_FRAME_COLOR_THRESHOLD = 8;
    private final SharedPreferences mPrefs;
    private final Action1<Throwable> mOnError;

    public String getSessionId() {
        return mPrefs.getString(PREF_SESSION_ID, null);
    }

    public void setSessionId(final String sessionId) {
        mPrefs.edit().putString(PREF_SESSION_ID, sessionId)
                .apply();
    }

    private String mSessionId;
    private URL mEndpoint;

    public URL getEndpoint() {
        final String curSessId = getSessionId();
        if (!Objects.equals(curSessId, mSessionId)) {
            mSessionId = curSessId;
            try {
                mEndpoint = new URL("https://teslate-server.appspot.com/frame/" + mSessionId +
                        "/frame.jpeg");
            } catch (final MalformedURLException e) {
                mOnError.call(e);
            }
        }
        return mEndpoint;
    }

    public int getFrameColorThreshold() {
        return mPrefs.getInt(PREF_FRAME_COLOR_THRESHOLD, DEFAULT_FRAME_COLOR_THRESHOLD);
    }
}
