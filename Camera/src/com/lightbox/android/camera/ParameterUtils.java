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

package com.lightbox.android.camera;

import android.hardware.Camera.Parameters;

public class ParameterUtils {
	private static final String KEY_EXPOSURE_COMPENSATION = "exposure-compensation";
	private static final String KEY_MAX_EXPOSURE_COMPENSATION = "max-exposure-compensation";
    private static final String KEY_MIN_EXPOSURE_COMPENSATION = "min-exposure-compensation";
    private static final String KEY_EXPOSURE_COMPENSATION_STEP = "exposure-compensation-step";
    
    private static final String KEY_ZOOM = "zoom";
    private static final String KEY_MAX_ZOOM = "max-zoom";
    private static final String KEY_ZOOM_RATIOS = "zoom-ratios";
    private static final String KEY_ZOOM_SUPPORTED = "zoom-supported";
    private static final String KEY_SMOOTH_ZOOM_SUPPORTED = "smooth-zoom-supported";
    
    public static final String FOCUS_MODE_EDOF = "edof";
    
    private static final String KEY_GPS_PROCESSING_METHOD = "gps-processing-method";
    
    private static final String TRUE = "true";
    
    public static int getMaxExposureCompensation(Parameters parameters) {
    	String str = parameters.get(KEY_MAX_EXPOSURE_COMPENSATION);
    	if (str != null) {
    		try {
    			return Integer.parseInt(str);
    		} catch (NumberFormatException e) {
    			return 0;
    		}
    	}
    	return 0;
    }
    
    public static int getMinExposureCompensation(Parameters parameters) {
    	String str = parameters.get(KEY_MIN_EXPOSURE_COMPENSATION);
    	if (str != null) {
    		try {
    			return Integer.parseInt(str);
    		} catch (NumberFormatException e) {
    			return 0;
    		}
    	}
    	return 0;
    }

    public static float getExposureCompensationStep(Parameters parameters) {
		String str = parameters.get(KEY_EXPOSURE_COMPENSATION_STEP);
    	if (str != null) {
    		try {
    			return Float.parseFloat(str);
    		} catch (NumberFormatException e) {
    			return 0;
    		}
    	}
    	return 0;
	}
    
    public static void setExposureCompensation(Parameters parameters, int value) {
    	parameters.set(KEY_EXPOSURE_COMPENSATION, value);
    }
    
    public static boolean isZoomSupported(Parameters parameters) {
    	 String str = parameters.get(KEY_ZOOM_SUPPORTED);
         return (str != null && TRUE.equals(str));
    }
    
    public static void setZoom(Parameters parameters, int value) {
    	parameters.set(KEY_ZOOM, value);
    }
    
    public static void setGpsProcessingMethod(Parameters parameters, String processing_method) {
    	parameters.set(KEY_GPS_PROCESSING_METHOD, processing_method);
    }
}
