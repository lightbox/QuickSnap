/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * Modified by Nilesh Patel
 */

package com.lightbox.android.camera.device;

import static com.lightbox.android.camera.Util.Assert;

import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Size;
import android.util.Log;

import java.io.IOException;

import com.lightbox.android.camera.CameraHardwareException;
import com.lightbox.android.camera.device.CameraHolder;

public class GingerbreadCameraHolder extends CameraHolder {
    private static final String TAG = "GingerbreadCameraHolder";
    protected CameraInfo[] mInfo;

    protected GingerbreadCameraHolder() {
    	super();
        mNumberOfCameras = android.hardware.Camera.getNumberOfCameras();
        mInfo = new CameraInfo[mNumberOfCameras];
        for (int i = 0; i < mNumberOfCameras; i++) {
            mInfo[i] = new CameraInfo();
            android.hardware.Camera.getCameraInfo(i, mInfo[i]);
        }
    }

    public int getNumberOfCameras() {
        return mNumberOfCameras;
    }
    
    public boolean isFrontFacing(int cameraId) {
    	return (mInfo[cameraId].facing == CameraInfo.CAMERA_FACING_FRONT);
    }
    
    public int getCameraOrientation(int cameraId, int orientationSensorValue) {
    	return mInfo[cameraId].orientation;
    }

    public synchronized android.hardware.Camera open(int cameraId)
            throws CameraHardwareException {
        Assert(mUsers == 0);
        if (mCameraDevice != null && mCameraId != cameraId) {
            mCameraDevice.release();
            mCameraDevice = null;
            mCameraId = -1;
        }
        if (mCameraDevice == null) {
            try {
                Log.v(TAG, "open camera " + cameraId);
                mCameraDevice = android.hardware.Camera.open(cameraId);
                mCameraId = cameraId;
            } catch (RuntimeException e) {
                Log.e(TAG, "fail to connect Camera", e);
                throw new CameraHardwareException(e);
            }
            mParameters = mCameraDevice.getParameters();
        } else {
            try {
                mCameraDevice.reconnect();
            } catch (IOException e) {
                Log.e(TAG, "reconnect failed.");
                throw new CameraHardwareException(e);
            }
            mCameraDevice.setParameters(mParameters);
        }
        ++mUsers;
        mHandler.removeMessages(RELEASE_CAMERA);
        mKeepBeforeTime = 0;
        return mCameraDevice;
    }

	@Override
	public double getAspectRatio() {
		Size size = mParameters.getPictureSize();
		return ((double) size.width / size.height);
	}

	@Override
	public int getFrontFacingCameraId() {
		int numCameras = getNumberOfCameras();
		CameraInfo cameraInfo = new CameraInfo();
		for (int cameraId = 0; cameraId < numCameras; cameraId++) {
			Camera.getCameraInfo(cameraId, cameraInfo);				
			if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
				return cameraId;
			}
		}
		return 0;
	}

	@Override
	public int getRearFacingCameraId() {
		int numCameras = getNumberOfCameras();
		CameraInfo cameraInfo = new CameraInfo();
		for (int cameraId = 0; cameraId < numCameras; cameraId++) {
			Camera.getCameraInfo(cameraId, cameraInfo);				
			if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
				return cameraId;
			}
		}
		return 0;
	}
}
