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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import android.app.Application;
import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.view.OrientationEventListener;

import com.lightbox.android.camera.util.MyLocation;
import com.lightbox.android.camera.util.MyLocation.LocationResult;

/** 
 * CameraApplication 
 * @author Nilesh Patel
 */
public class CameraApplication extends Application {
	/** Used to tag logs */
	@SuppressWarnings("unused")
	private static final String TAG = "CameraApplication";

	public static final int JPEG_HIGH_QUALITY = 90;
	
	private MyOrientationEventListener mOrientationEventListener;
	private long mLocationLastUpdateTime = 0;
	private ArrayList<OrientationChangeListener> mOrientationChangeListeners = new ArrayList<OrientationChangeListener>();
	private int mLastKnownOrientation = -1;

	// Current location & nearby places
	public double lat = 0;
	public double lng = 0;
	public String locStr = "";
	
	@Override
	public void onCreate() {
		super.onCreate();
				
		mOrientationEventListener = new MyOrientationEventListener(getApplicationContext());
		mOrientationEventListener.enable();
	}
	
	public void registerOrientationChangeListener(OrientationChangeListener listener) {
		mOrientationChangeListeners.add(listener);
	}
	
	public void deregisterOrientationChangeListener(OrientationChangeListener listener) {
		mOrientationChangeListeners.remove(listener);
	}
	
	public interface OrientationChangeListener {
		public void onOrientationChanged(int orientation);
	}
	
	public int getLastKnownOrientation() {
		return mLastKnownOrientation;
	}
	
	public void requestLocationUpdate(boolean forceUpdate) {
		// Only request the location once an hour unless forceUpdate is set
		if (!forceUpdate && (System.currentTimeMillis() - mLocationLastUpdateTime < 1000 * 60 * 60)) {
			return;
		}

		mLocationLastUpdateTime = System.currentTimeMillis();
		
		MyLocation myLocation = new MyLocation(this);
		myLocation.requestCurrentLocation(new LocationResult() {
			@Override
			public void gotLocation(final android.location.Location location) {
				Thread thread = new Thread(new Runnable() {
					@Override
					public void run() {
						updateWithNewLocation(location);
					}
				});
				thread.start();				
			}
		});
    }
	
	public void setLocation(double _lat, double _lng, String _locStr) {
		lat = _lat;
		lng = _lng;
		locStr = _locStr;
	}
	
	public void updateWithNewLocation(android.location.Location loc) {
		String locStr;

		if (loc != null) {
			double lat = loc.getLatitude();
			double lng = loc.getLongitude();
			String addrStr = "";

			Geocoder gc = new Geocoder(this, Locale.getDefault());
			try {
				List<Address> addresses = gc.getFromLocation(lat, lng, 1);
				StringBuilder sb = new StringBuilder();
				if (addresses.size() > 0) {
					Address addr = addresses.get(0);

					for (int i = 0; i < addr.getMaxAddressLineIndex() && i < 1; i++) {
						sb.append(addr.getAddressLine(i) + " ");
					}
					addrStr = sb.toString();
				}
			} catch (IOException e) {
			}

			locStr = addrStr;

			this.lat = lat;
			this.lng = lng;
		} else {
			locStr = "Location not found";

			this.lat = 0;
			this.lng = 0;
		}
		this.locStr = locStr;
	}
	

	//----------------------------------------------
	// MyOrientationEventListener

	public class MyOrientationEventListener extends OrientationEventListener {
		public MyOrientationEventListener(Context context) {
			super(context);
		}

		@Override
		public void onOrientationChanged(int orientation) {
			mLastKnownOrientation = orientation;
			for (OrientationChangeListener listener : mOrientationChangeListeners) {
				listener.onOrientationChanged(orientation);
			}
		}
	}
}
