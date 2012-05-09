/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.lightbox.android.camera.ui;

import android.content.Context;

import com.lightbox.android.camera.CameraSettings;
import com.lightbox.android.camera.ListPreference;
import com.lightbox.android.camera.PreferenceGroup;

public class CameraHeadUpDisplay extends HeadUpDisplay {

    @SuppressWarnings("unused")
	private static final String TAG = "CamcoderHeadUpDisplay";

    private OtherSettingsIndicator mOtherSettings;
    private ZoomIndicator mZoomIndicator;
    private Context mContext;
    private float[] mInitialZoomRatios;
    private int mInitialOrientation;

    public CameraHeadUpDisplay(Context context) {
        super(context);
        mContext = context;
    }

    public void initialize(Context context, PreferenceGroup group,
            float[] initialZoomRatios, int initialOrientation) {
        mInitialZoomRatios = initialZoomRatios;
        mInitialOrientation = initialOrientation;
        super.initialize(context, group);
    }

    @Override
    protected void initializeIndicatorBar(
            Context context, PreferenceGroup group) {
        super.initializeIndicatorBar(context, group);

        ListPreference prefs[] = getListPreferences(group,
                CameraSettings.KEY_FOCUS_MODE,
                CameraSettings.KEY_WHITE_BALANCE,
                CameraSettings.KEY_EXPOSURE,
                CameraSettings.KEY_SCENE_MODE);

        mOtherSettings = new OtherSettingsIndicator(context, prefs);
        mOtherSettings.setOnRestorePreferencesClickedRunner(new Runnable() {
            public void run() {
                if (mListener != null) {
                    mListener.onRestorePreferencesClicked();
                }
            }
        });
        mIndicatorBar.addComponent(mOtherSettings);

        if (mInitialZoomRatios != null) {
            mZoomIndicator = new ZoomIndicator(mContext);
            mZoomIndicator.setZoomRatios(mInitialZoomRatios);
            mIndicatorBar.addComponent(mZoomIndicator);
        } else {
            mZoomIndicator = null;
        }

        mIndicatorBar.setOrientation(mInitialOrientation);
    }

    public void setZoomListener(ZoomControllerListener listener) {
        // The rendering thread won't access listener variable, so we don't
        // need to do concurrency protection here
        mZoomIndicator.setZoomListener(listener);
    }

    public void setZoomIndex(int index) {
    	if (mZoomIndicator != null) {
	        GLRootView root = getGLRootView();
	        if (root != null) {
	            synchronized (root) {
	                mZoomIndicator.setZoomIndex(index);
	            }
	        } else {
	            mZoomIndicator.setZoomIndex(index);
	        }
    	}
    }

    /**
     * Sets the zoom rations the camera driver provides. This methods must be
     * called before <code>setZoomListener()</code> and
     * <code>setZoomIndex()</code>
     */
    public void setZoomRatios(float[] zoomRatios) {
        GLRootView root = getGLRootView();
        if (root != null) {
            synchronized(root) {
                setZoomRatiosLocked(zoomRatios);
            }
        } else {
            setZoomRatiosLocked(zoomRatios);
        }
    }

    private void setZoomRatiosLocked(float[] zoomRatios) {
        mZoomIndicator.setZoomRatios(zoomRatios);
    }
}
