/*
 * Copyright (C) 2012 Lightbox
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

package com.lightbox.android.camera.device;

import com.lightbox.android.camera.device.GingerbreadCameraHolder;

public class HTCFrontFacingFixGingerbreadCameraHolder extends GingerbreadCameraHolder {
    @SuppressWarnings("unused")
	private static final String TAG = "HTCFrontFacingFixGingerbreadCameraHolder";
    
    public int getCameraOrientation(int cameraId, int orientationSensorValue) {
		if (isFrontFacing(cameraId)) {
			switch (orientationSensorValue) {
			case 0:
				return 90;
			case 90:
				return 270;
			case 180:
				return 90;
			case 270:
				return 270;
			}
		}
    	return mInfo[cameraId].orientation;
    }
}
