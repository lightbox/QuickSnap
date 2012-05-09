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

package com.lightbox.android.camera.util;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;

// Class for getting the phone's location via GPS and Wifi-geolocation
// This class was adapted from:
// http://stackoverflow.com/questions/3145089/what-is-the-simplest-and-most-robust-way-to-get-the-users-current-location-in-an/3145655#3145655
public class MyLocation {
	private final static int TIMEOUT = 20 * 1000;   // Max time to wait for location update
	
    private Timer timer1;
    private LocationManager lm;
    private boolean gpsEnabled = false;
    private boolean networkEnabled = false;

    private LocationResult locationResult;

    public MyLocation(Context context) {
    	lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        
        // Determine which location sensors are available
        try {
        	gpsEnabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
        } catch (Exception e) {
        	e.printStackTrace();
        }
        try {
        	networkEnabled = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        } catch (Exception e) {
        	e.printStackTrace();
        }
    }
    
    public Location getLastKnownLocation() {
        Location netLoc = null;
        Location gpsLoc = null;
        if (gpsEnabled) {
            gpsLoc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        }
        if (networkEnabled) {
            netLoc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        }

        // If there are both values use the latest one
        if (gpsLoc != null && netLoc != null){
            if (gpsLoc.getTime() > netLoc.getTime()) {
                return gpsLoc;
            } else {
                return netLoc;
            }
        }

        if (gpsLoc != null) {
        	return gpsLoc;
        }
        if (netLoc != null) {
            return netLoc;
        }
    	return null;
    }
    
    public boolean requestCurrentLocation(LocationResult result) {
    	// I use LocationResult callback class to pass location value from MyLocation to user code.
        locationResult = result;

        // Don't start listeners if no provider is enabled
        if (!gpsEnabled && !networkEnabled) {
            return false;
        }
                
        if (gpsEnabled) {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListenerGps);
        }
        if (networkEnabled) {
            lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, locationListenerNetwork);
        }
        
        timer1 = new Timer();
        timer1.schedule(new GetLastLocation(), TIMEOUT);
        return true;
    }

    LocationListener locationListenerGps = new LocationListener() {
        public void onLocationChanged(Location location) {
            timer1.cancel();
            lm.removeUpdates(this);
            lm.removeUpdates(locationListenerNetwork);
            locationResult.gotLocation(location);
        }
        public void onProviderDisabled(String provider) {}
        public void onProviderEnabled(String provider) {}
        public void onStatusChanged(String provider, int status, Bundle extras) {}
    };

    LocationListener locationListenerNetwork = new LocationListener() {
        public void onLocationChanged(Location location) {
            timer1.cancel();
            lm.removeUpdates(this);
            lm.removeUpdates(locationListenerGps);
            locationResult.gotLocation(location);
        }
        public void onProviderDisabled(String provider) {}
        public void onProviderEnabled(String provider) {}
        public void onStatusChanged(String provider, int status, Bundle extras) {}
    };
    
	public static String getGeoLocationString(Context context, double latitude, double longitude) {
		String addrStr = "";

		Geocoder gc = new Geocoder(context, Locale.getDefault());
		try {
			List<Address> addresses = gc.getFromLocation(latitude, longitude, 1);
			StringBuilder sb = new StringBuilder();
			if (addresses.size() > 0) {
				Address addr = addresses.get(0);

				for (int i = 0; i < addr.getMaxAddressLineIndex() && i < 1; i++) {
					sb.append(addr.getAddressLine(i) + " ");
				}
				addrStr = sb.toString();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		return addrStr;
	}

    class GetLastLocation extends TimerTask {
        @Override
        public void run() {
             lm.removeUpdates(locationListenerGps);
             lm.removeUpdates(locationListenerNetwork);

             Location loc = getLastKnownLocation();
             locationResult.gotLocation(loc);
        }
    }

    public static abstract class LocationResult {
        public abstract void gotLocation(Location location);
    }
}
