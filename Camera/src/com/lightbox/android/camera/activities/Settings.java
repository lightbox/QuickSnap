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

package com.lightbox.android.camera.activities;


import java.util.List;

import com.lightbox.android.camera.R;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;

/** 
 * Settings 
 * @author Nilesh Patel
 */
public class Settings extends PreferenceActivity {
	/** Used to tag logs */
	@SuppressWarnings("unused")
	private static final String TAG = "Settings";

	private static final String MARKET_APP_URI_FORMAT = "market://details?id=%s";
	private static final String MARKET_WEB_URI_FORMAT = "http://market.android.com/details?id=%s";
	private static final String LIGHTBOX_PACKAGE = "com.lightbox.android.photos";
	
	@Override
	protected void onResume() {
		super.onResume();
		
		PreferenceScreen prefScreenRoot = getPreferenceScreen();
		if (prefScreenRoot != null) {
			prefScreenRoot.removeAll();
		}
		addPreferencesFromResource(R.xml.preferences);
		
		Preference getLightboxPref = findPreference("get_lightbox");
		getLightboxPref.setIntent(buildViewMarketDetailsIntent(this, LIGHTBOX_PACKAGE));
	}
	
	public static Intent buildViewMarketDetailsIntent(Context context, String packageName) {
    	// TODO: Support Amazon Market intent
    	// http://stackoverflow.com/questions/7683130/how-to-support-amazon-and-android-market-links-in-same-apk/
    	// http://stackoverflow.com/questions/7658984/how-to-write-review-on-amazon-market-using-app
    	
    	// Build intent to view app details with the market app
        Intent viewMarketAppIntent = new Intent(Intent.ACTION_VIEW);
        viewMarketAppIntent.setData(Uri.parse(String.format(MARKET_APP_URI_FORMAT, packageName)));
        
    	// If this device can resolve the intent, return it
        if (canResolveIntent(context, viewMarketAppIntent)) {
        	return viewMarketAppIntent;
        } else {
        	// Else, fall-back to the online market Intent that cannot fail
            Intent viewMarketWebIntent = new Intent(Intent.ACTION_VIEW);
            viewMarketWebIntent.setData(Uri.parse(String.format(MARKET_WEB_URI_FORMAT, packageName)));
            return viewMarketWebIntent;
        }
    }
	
    public static boolean canResolveIntent(Context context, Intent intent) {
        PackageManager packageManager = context.getPackageManager();
        List<ResolveInfo> resolveInfo = packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
        return (resolveInfo.size() > 0);
    }
}
