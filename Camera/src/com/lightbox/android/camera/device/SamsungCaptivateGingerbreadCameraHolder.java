/**
 * Copyright (c) 2011 Lightbox
 */
package com.lightbox.android.camera.device;

/** 
 * SamsungCaptivate 
 * @author Nilesh Patel
 */
public class SamsungCaptivateGingerbreadCameraHolder extends GingerbreadCameraHolder {
	/** Used to tag logs */
	@SuppressWarnings("unused")
	private static final String TAG = "SamsungCaptivate";
	
	public int getCameraOrientation(int cameraId, int orientationSensorValue) {
		switch (orientationSensorValue) {
		case 0:
			return 180;
		case 90:
			return 270;
		case 180:
			return 0;
		case 270:
			return 90;
		}
		return mInfo[cameraId].orientation;
    }
}
