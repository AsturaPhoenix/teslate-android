// Copyright 2015 The Vanadium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package rw.simplecast;

import android.util.Log;

import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;

import java.io.ByteArrayInputStream;

import rx.Subscriber;
import rx.functions.Action1;

public class S3Uploader extends Subscriber<byte[]> {
    private final String mName;
    private final AmazonS3 mS3;
    private final Action1<Throwable> mOnError;

    public S3Uploader(final String name, final Action1<Throwable> onError) {
        mName = name;

        mS3 = new AmazonS3Client(Aws.CREDS);
        mS3.setRegion(Region.getRegion(Regions.US_WEST_1));

        mOnError = onError;
    }

    @Override
    public void onStart() {
        request(1);
    }

    @Override
    public void onNext(final byte[] bytes) {
        try {
            Log.i("SIMPLECAST", "Uploading " + mName + "...");
            final ObjectMetadata md = new ObjectMetadata();
            md.setContentType("image/jpeg");
            mS3.putObject(new PutObjectRequest("simplecast", mName,
                    new ByteArrayInputStream(bytes), md)
                    .withCannedAcl(CannedAccessControlList.PublicRead));
            Log.i("SIMPLECAST", "Uploaded " + mName);
        } catch (final RuntimeException e) {
            mOnError.call(e);
        }
        request(1);
    }

    @Override
    public void onError(final Throwable e) {
        mOnError.call(e);
    }

    @Override
    public void onCompleted() {
    }
}
