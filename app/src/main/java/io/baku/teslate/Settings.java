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
        return mPrefs.getString(PREF_SESSION_ID, "");
    }

    public void setSessionId(final String sessionId) {
        mPrefs.edit().putString(PREF_SESSION_ID, sessionId)
                .apply();
    }

    private String mSessionId;
    private URL mFrameEndpoint, mCommandEndpoint;

    private void ensureEndpoints() {
        final String curSessId = getSessionId();
        if (!Objects.equals(curSessId, mSessionId)) {
            mSessionId = curSessId;
            try {
                mFrameEndpoint = new URL("https://teslate.appspot.com/frame/" + mSessionId +
                        "/frame.jpeg");
            } catch (final MalformedURLException e) {
                mOnError.call(e);
            }
            try {
                mCommandEndpoint = new URL("https://teslate.appspot.com/command/" +
                        mSessionId);
            } catch (final MalformedURLException e) {
                mOnError.call(e);
            }
        }
    }

    public URL getFrameEndpoint() {
        ensureEndpoints();
        return mFrameEndpoint;
    }

    public URL getCommandEndpoint() {
        ensureEndpoints();
        return mCommandEndpoint;
    }

    public int getFrameColorThreshold() {
        return mPrefs.getInt(PREF_FRAME_COLOR_THRESHOLD, DEFAULT_FRAME_COLOR_THRESHOLD);
    }
}
