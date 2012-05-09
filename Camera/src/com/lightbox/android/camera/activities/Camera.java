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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.BitmapFactory.Options;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.PreviewCallback;
import android.hardware.Camera.Size;
import android.media.AudioManager;
import android.media.CameraProfile;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.MessageQueue;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.Display;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.AnimationSet;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.ScaleAnimation;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.lightbox.android.camera.CameraApplication;
import com.lightbox.android.camera.CameraApplication.OrientationChangeListener;
import com.lightbox.android.camera.CameraHardwareException;
import com.lightbox.android.camera.CameraSettings;
import com.lightbox.android.camera.ComboPreferences;
import com.lightbox.android.camera.FocusRectangle;
import com.lightbox.android.camera.ImageManager;
import com.lightbox.android.camera.MenuHelper;
import com.lightbox.android.camera.NoSearchActivity;
import com.lightbox.android.camera.OnScreenHint;
import com.lightbox.android.camera.ParameterUtils;
import com.lightbox.android.camera.PreviewFrameLayout;
import com.lightbox.android.camera.R;
import com.lightbox.android.camera.RotateImageView;
import com.lightbox.android.camera.ShutterButton;
import com.lightbox.android.camera.Switcher;
import com.lightbox.android.camera.ThumbnailController;
import com.lightbox.android.camera.Util;
import com.lightbox.android.camera.device.CameraHolder;
import com.lightbox.android.camera.ui.CameraHeadUpDisplay;
import com.lightbox.android.camera.ui.GLRootView;
import com.lightbox.android.camera.ui.HeadUpDisplay;
import com.lightbox.android.camera.ui.ZoomControllerListener;

/** The Camera activity which can preview and take pictures. */
public class Camera extends NoSearchActivity implements View.OnClickListener,
        ShutterButton.OnShutterButtonListener, SurfaceHolder.Callback,
        Switcher.OnSwitchListener {

    private static final String TAG = "camera";

    private static final int CROP_MSG = 1;
    private static final int FIRST_TIME_INIT = 2;
    private static final int RESTART_PREVIEW = 3;
    private static final int CLEAR_SCREEN_DELAY = 4;
    private static final int SET_CAMERA_PARAMETERS_WHEN_IDLE = 5;

    // The subset of parameters we need to update in setCameraParameters().
    private static final int UPDATE_PARAM_INITIALIZE = 1;
    private static final int UPDATE_PARAM_ZOOM = 2;
    private static final int UPDATE_PARAM_PREFERENCE = 4;
    private static final int UPDATE_PARAM_ALL = -1;

    // When setCameraParametersWhenIdle() is called, we accumulate the subsets
    // needed to be updated in mUpdateSet.
    private int mUpdateSet;

    // The brightness settings used when it is set to automatic in the system.
    // The reason why it is set to 0.7 is just because 1.0 is too bright.
    private static final float DEFAULT_CAMERA_BRIGHTNESS = 0.7f;

    private static final int SCREEN_DELAY = 2 * 60 * 1000;
    private static final int FOCUS_BEEP_VOLUME = 100;

    private static final int ZOOM_STOPPED = 0;
    private static final int ZOOM_START = 1;
    private static final int ZOOM_STOPPING = 2;

    private int mZoomState = ZOOM_STOPPED;
    private boolean mSmoothZoomSupported = false;
    private int mZoomValue;  // The current zoom value.
    private int mZoomMax;
    private int mTargetZoomValue;

    private Parameters mParameters;
    private Parameters mInitialParams;

    // The device orientation in degrees. Default is unknown.
    private int mOrientation = OrientationEventListener.ORIENTATION_UNKNOWN;
    // The orientation compensation for icons and thumbnails.
    private int mOrientationCompensation = 0;
    private ComboPreferences mPreferences;

    private static final int IDLE = 1;
    private static final int SNAPSHOT_IN_PROGRESS = 2;

    private static final boolean SWITCH_CAMERA = true;
    private static final boolean SWITCH_VIDEO = false;

    private int mStatus = IDLE;
    private static final String sTempCropFilename = "crop-temp";

    private android.hardware.Camera mCameraDevice;
    private ContentProviderClient mMediaProviderClient;
    private SurfaceView mSurfaceView;
    private SurfaceHolder mSurfaceHolder = null;
    private ShutterButton mShutterButton;
    private FocusRectangle mFocusRectangle;
    private ToneGenerator mFocusToneGenerator;
    private GestureDetector mGestureDetector;
    private boolean mStartPreviewFail = false;

    private GLRootView mGLRootView;

    // mPostCaptureAlert, mLastPictureButton, mThumbController
    // are non-null only if isImageCaptureIntent() is true.
    private ImageView mLastPictureButton;
    private ImageView mFlashButton;
    private ImageView mCameraTypeButton;
    private ThumbnailController mThumbController;

    // mCropValue and mSaveUri are used only if isImageCaptureIntent() is true.
    private String mCropValue;
    private Uri mSaveUri;

    private ImageCapture mImageCapture = null;

    private boolean mPreviewing;
    private boolean mPausing;
    private boolean mFirstTimeInitialized;
    private boolean mIsImageCaptureIntent;

    private static final int FOCUS_NOT_STARTED = 0;
    private static final int FOCUSING = 1;
    private static final int FOCUSING_SNAP_ON_FINISH = 2;
    private static final int FOCUS_SUCCESS = 3;
    private static final int FOCUS_FAIL = 4;
    private int mFocusState = FOCUS_NOT_STARTED;

    private ContentResolver mContentResolver;
    private boolean mDidRegister = false;

    private final ShutterCallback mShutterCallback = new ShutterCallback();
    private final PostViewPictureCallback mPostViewPictureCallback =
            new PostViewPictureCallback();
    private final RawPictureCallback mRawPictureCallback =
            new RawPictureCallback();
    private final AutoFocusCallback mAutoFocusCallback =
            new AutoFocusCallback();
    private final PreviewFrameCallback mPreviewFrameCallback = new PreviewFrameCallback();
    private final ZoomListener mZoomListener = (Build.VERSION.SDK_INT >= 0x00000008) ? new ZoomListener() : null;
    // Use the ErrorCallback to capture the crash count
    // on the mediaserver
    private final ErrorCallback mErrorCallback = new ErrorCallback();

    private long mFocusStartTime;
    private long mFocusCallbackTime;
    private long mCaptureStartTime;
    private long mShutterCallbackTime;
    private long mPostViewPictureCallbackTime;
    private long mRawPictureCallbackTime;
    private long mJpegPictureCallbackTime;
    private int mPicturesRemaining;

    // These latency time are for the CameraLatency test.
    public long mAutoFocusTime;
    public long mShutterLag;
    public long mShutterToPictureDisplayedTime;
    public long mPictureDisplayedToJpegCallbackTime;
    public long mJpegCallbackFinishTime;

    // Add for test
    public static boolean mMediaServerDied = false;

    // Focus mode. Options are pref_camera_focusmode_entryvalues.
    private String mFocusMode;
    private String mSceneMode;

    private final Handler mHandler = new MainHandler();
    private CameraHeadUpDisplay mHeadUpDisplay;

    // multiple cameras support
    private int mNumberOfCameras;
    private int mCameraId;
	
    private AudioManager mAudioManager;

    private boolean mIsLightboxPhotosIntent;
    
    /**
     * This Handler is used to post message back onto the main thread of the
     * application
     */
    private class MainHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case RESTART_PREVIEW: {
                    restartPreview();
                    if (mJpegPictureCallbackTime != 0) {
                        long now = System.currentTimeMillis();
                        mJpegCallbackFinishTime = now - mJpegPictureCallbackTime;
                        Log.v(TAG, "mJpegCallbackFinishTime = "
                                + mJpegCallbackFinishTime + "ms");
                        mJpegPictureCallbackTime = 0;
                    }
                    break;
                }

                case CLEAR_SCREEN_DELAY: {
                    getWindow().clearFlags(
                            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    break;
                }

                case FIRST_TIME_INIT: {
                    initializeFirstTime();
                    break;
                }

                case SET_CAMERA_PARAMETERS_WHEN_IDLE: {
                    setCameraParametersWhenIdle(0);
                    break;
                }
            }
        }
    }

    private void resetExposureCompensation() {
        String value = mPreferences.getString(CameraSettings.KEY_EXPOSURE,
                CameraSettings.EXPOSURE_DEFAULT_VALUE);
        if (!CameraSettings.EXPOSURE_DEFAULT_VALUE.equals(value)) {
            Editor editor = mPreferences.edit();
            editor.putString(CameraSettings.KEY_EXPOSURE, "0");
            editor.commit();
            if (mHeadUpDisplay != null) {
                mHeadUpDisplay.reloadPreferences();
            }
        }
    }

    private void keepMediaProviderInstance() {
        // We want to keep a reference to MediaProvider in camera's lifecycle.
        // TODO: Utilize mMediaProviderClient instance to replace
        // ContentResolver calls.
        if (mMediaProviderClient == null) {
            mMediaProviderClient = getContentResolver()
                    .acquireContentProviderClient(MediaStore.AUTHORITY);
        }
    }

    // Snapshots can only be taken after this is called. It should be called
    // once only. We could have done these things in onCreate() but we want to
    // make preview screen appear as soon as possible.
    private void initializeFirstTime() {
        if (mFirstTimeInitialized) return;

        // Create orientation listenter. This should be done first because it
        // takes some time to get first orientation.
        ((CameraApplication)getApplication()).registerOrientationChangeListener(mOrientationChangeListener);

        keepMediaProviderInstance();
        checkStorage();

        // Initialize last picture button.
        mContentResolver = getContentResolver();
        //if (!mIsImageCaptureIntent)  {
            mLastPictureButton =
                    (ImageView) findViewById(R.id.review_thumbnail);
            mLastPictureButton.setOnClickListener(this);
            mFlashButton = (RotateImageView) findViewById(R.id.btn_flash);
            mFlashButton.setOnClickListener(this);
            mCameraTypeButton = (RotateImageView) findViewById(R.id.btn_camera_type);
            mCameraTypeButton.setOnClickListener(this);
            if (CameraHolder.instance().getNumberOfCameras() > 1) {
            	mCameraTypeButton.setVisibility(View.VISIBLE);
            } else {
            	mCameraTypeButton.setVisibility(View.GONE);
            }
            
            mThumbController = new ThumbnailController(
                    getResources(), mLastPictureButton, mContentResolver);
            //mThumbController.loadData(ImageManager.getLastImageThumbPath());
            String lastPhotoThumbPath = getLastPhotoThumbPath();
            if (lastPhotoThumbPath != null) {
            	mThumbController.loadData(lastPhotoThumbPath);
			}
            // Update last image thumbnail.
            updateThumbnailButton();
        //}

        // Initialize shutter button.
        mShutterButton = (ShutterButton) findViewById(R.id.shutter_button);
        mShutterButton.setOnShutterButtonListener(this);
        mShutterButton.setVisibility(View.VISIBLE);

        mFocusRectangle = (FocusRectangle) findViewById(R.id.focus_rectangle);
        updateFocusIndicator();

        initializeScreenBrightness();
        installIntentFilter();
        initializeFocusTone();
        initializeZoom();
        mHeadUpDisplay = new CameraHeadUpDisplay(this);
        mHeadUpDisplay.setListener(new MyHeadUpDisplayListener());
        initializeHeadUpDisplay();
        mFirstTimeInitialized = true;
        changeHeadUpDisplayState();
        addIdleHandler();
        setInitialOrientation();
    }

    private void setControlsPadding() {
    	if (mCameraTypeButton.getVisibility() == View.GONE && mFlashButton.getVisibility() == View.GONE) {
    		findViewById(R.id.controls).setVisibility(View.GONE);
    	} else if (mCameraTypeButton.getVisibility() == View.VISIBLE && mFlashButton.getVisibility() == View.VISIBLE) {
    		DisplayMetrics dm = new DisplayMetrics();
    		getWindowManager().getDefaultDisplay().getMetrics(dm);
    		int pixels = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2, dm);
    		((LinearLayout.LayoutParams)mCameraTypeButton.getLayoutParams()).topMargin = pixels;
    		((LinearLayout.LayoutParams)mFlashButton.getLayoutParams()).bottomMargin = pixels;
    	} else {
    		if (mCameraTypeButton.getVisibility() == View.VISIBLE && mFlashButton.getVisibility() == View.GONE) {
    			((LinearLayout.LayoutParams)mCameraTypeButton.getLayoutParams()).topMargin = 0;
    		} else if (mCameraTypeButton.getVisibility() == View.GONE && mFlashButton.getVisibility() == View.VISIBLE) {
    			((LinearLayout.LayoutParams)mFlashButton.getLayoutParams()).bottomMargin = 0;
    		}
    	}
    }
    
    private String getLastPhotoThumbPath() {
    	//TODO if launched from lightbox
    	if (mIsLightboxPhotosIntent) {
    		String path = getIntent().getStringExtra("com.lightbox.android.photos.activities.TakePhotoActivity.mostRecentPhotoPath");
    		if (path != null && path.length() > 0) {
    			File file = new File(path);
    			if (file.exists()) {
    				return path;
    			}
    		}
    	}
        
        return ImageManager.getLastImageThumbPath();
    }
    
    private void addIdleHandler() {
        MessageQueue queue = Looper.myQueue();
        queue.addIdleHandler(new MessageQueue.IdleHandler() {
            public boolean queueIdle() {
                ImageManager.ensureOSXCompatibleFolder();
                return false;
            }
        });
    }

    private void updateThumbnailButton() {
        // Update last image if URI is invalid and the storage is ready.
        if (!mThumbController.isUriValid() && mPicturesRemaining >= 0) {
            updateLastImage();
        }
        mThumbController.updateDisplayIfNeeded(500);
    }

    // If the activity is paused and resumed, this method will be called in
    // onResume.
    private void initializeSecondTime() {
    	// Create orientation listenter. This should be done first because it
        // takes some time to get first orientation.
        ((CameraApplication)getApplication()).registerOrientationChangeListener(mOrientationChangeListener);
        
        installIntentFilter();
        initializeFocusTone();
        initializeZoom();
        changeHeadUpDisplayState();

        keepMediaProviderInstance();
        checkStorage();

        if (!mIsImageCaptureIntent) {
            updateThumbnailButton();
        }
    }

    private void initializeZoom() {
        if (!isZoomSupported()) return;

        mZoomMax = getMaxZoom();
        mSmoothZoomSupported = isSmoothZoomSupported();
        mGestureDetector = new GestureDetector(this, new ZoomGestureListener());

        mCameraDevice.setZoomChangeListener(mZoomListener);
    }

    private static final String KEY_ZOOM_SUPPORTED = "zoom-supported";
    private static final String KEY_SMOOTH_ZOOM_SUPPORTED = "smooth-zoom-supported";
    private static final String KEY_MAX_ZOOM = "max-zoom";
    private static final String TRUE = "true";
    private boolean isZoomSupported() {
    	if (Build.VERSION.SDK_INT <= 0x00000007) {
    		return false;
    	}
    	
    	String str = mParameters.get(KEY_ZOOM_SUPPORTED);
    	return (str != null && TRUE.equals(str) && getMaxZoom() > 0);
    }
    public boolean isSmoothZoomSupported() {
    	if (Build.VERSION.SDK_INT <= 0x00000007) {
    		return false;
    	}
    	
        String str = mParameters.get(KEY_SMOOTH_ZOOM_SUPPORTED);
        return (str != null && TRUE.equals(str));
    }
    public int getMaxZoom() {
        return mParameters.getInt(KEY_MAX_ZOOM);
    }
    
    private void onZoomValueChanged(int index) {
        if (mSmoothZoomSupported) {
            if (mTargetZoomValue != index && mZoomState != ZOOM_STOPPED) {
                mTargetZoomValue = index;
                if (mZoomState == ZOOM_START) {
                    mZoomState = ZOOM_STOPPING;
                    mCameraDevice.stopSmoothZoom();
                }
            } else if (mZoomState == ZOOM_STOPPED && mZoomValue != index) {
                mTargetZoomValue = index;
                mCameraDevice.startSmoothZoom(index);
                mZoomState = ZOOM_START;
            }
        } else {
            mZoomValue = index;
            setCameraParametersWhenIdle(UPDATE_PARAM_ZOOM);
        }
    }

    private float[] getZoomRatios() {
        if(!isZoomSupported()) return null;
        List<Integer> zoomRatios = mParameters.getZoomRatios();
        float result[] = new float[zoomRatios.size()];
        for (int i = 0, n = result.length; i < n; ++i) {
            result[i] = (float) zoomRatios.get(i) / 100f;
        }
        return result;
    }

    private class ZoomGestureListener extends
            GestureDetector.SimpleOnGestureListener {

        @Override
        public boolean onDoubleTap(MotionEvent e) {
            // Perform zoom only when preview is started and snapshot is not in
            // progress.
            if (mPausing || !isCameraIdle() || !mPreviewing
                    || mZoomState != ZOOM_STOPPED) {
                return false;
            }

            if (mZoomValue < getMaxZoom()) {
                // Zoom in to the maximum.
                mZoomValue = getMaxZoom();//mZoomMax;
            } else {
                mZoomValue = 0;
            }

            setCameraParametersWhenIdle(UPDATE_PARAM_ZOOM);

            mHeadUpDisplay.setZoomIndex(mZoomValue);
            return true;
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent m) {
        if (!super.dispatchTouchEvent(m) && mGestureDetector != null) {
            return mGestureDetector.onTouchEvent(m);
        }
        return true;
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(Intent.ACTION_MEDIA_MOUNTED)
                    || action.equals(Intent.ACTION_MEDIA_UNMOUNTED)
                    || action.equals(Intent.ACTION_MEDIA_CHECKING)) {
                checkStorage();
            } else if (action.equals(Intent.ACTION_MEDIA_SCANNER_FINISHED)) {
                checkStorage();
                if (!mIsImageCaptureIntent)  {
                    updateThumbnailButton();
                }
            }
        }
    };

    private final class ShutterCallback
            implements android.hardware.Camera.ShutterCallback {
        public void onShutter() {
            mShutterCallbackTime = System.currentTimeMillis();
            mShutterLag = mShutterCallbackTime - mCaptureStartTime;
            Log.v(TAG, "mShutterLag = " + mShutterLag + "ms");
            clearFocusState();
        }
    }

    private final class PostViewPictureCallback implements PictureCallback {
        public void onPictureTaken(
                byte [] data, android.hardware.Camera camera) {
            mPostViewPictureCallbackTime = System.currentTimeMillis();
            Log.v(TAG, "mShutterToPostViewCallbackTime = "
                    + (mPostViewPictureCallbackTime - mShutterCallbackTime)
                    + "ms");
        }
    }

    private final class RawPictureCallback implements PictureCallback {
        public void onPictureTaken(
                byte [] rawData, android.hardware.Camera camera) {
            mRawPictureCallbackTime = System.currentTimeMillis();
            Log.v(TAG, "mShutterToRawCallbackTime = "
                    + (mRawPictureCallbackTime - mShutterCallbackTime) + "ms");
        }
    }

    private final class JpegPictureCallback implements PictureCallback {

        public JpegPictureCallback() {
        }

        public void onPictureTaken(
                final byte [] jpegData, final android.hardware.Camera camera) {
        	if (isSoundFXDisabled()) {
            	mAudioManager.setStreamMute(AudioManager.STREAM_SYSTEM, false);
            }
        	
            if (mPausing) {
                return;
            }

        	
            mJpegPictureCallbackTime = System.currentTimeMillis();
            
            // If postview callback has arrived, the captured image is displayed
            // in postview callback. If not, the captured image is displayed in
            // raw picture callback.
            if (mPostViewPictureCallbackTime != 0) {
                mShutterToPictureDisplayedTime =
                        mPostViewPictureCallbackTime - mShutterCallbackTime;
                mPictureDisplayedToJpegCallbackTime =
                        mJpegPictureCallbackTime - mPostViewPictureCallbackTime;
            } else {
                mShutterToPictureDisplayedTime =
                        mRawPictureCallbackTime - mShutterCallbackTime;
                mPictureDisplayedToJpegCallbackTime =
                        mJpegPictureCallbackTime - mRawPictureCallbackTime;
            }
            Log.v(TAG, "mPictureDisplayedToJpegCallbackTime = "
                    + mPictureDisplayedToJpegCallbackTime + "ms");
            mHeadUpDisplay.setEnabled(true);

            if (!mIsImageCaptureIntent) {
                // We want to show the taken picture for a while, so we wait
                // for at least 1.2 second before restarting the preview.
                long delay = ((CameraHolder.instance().isFrontFacing(mCameraId)) ? 1200 : 400) - mPictureDisplayedToJpegCallbackTime;
                if (delay < 0) {
                    restartPreview();
                } else {
                    mHandler.sendEmptyMessageDelayed(RESTART_PREVIEW, delay);
                }
            }
            mImageCapture.storeImage(jpegData, camera);
            
            // Calculate this in advance of each shot so we don't add to shutter
            // latency. It's true that someone else could write to the SD card in
            // the mean time and fill it, but that could have happened between the
            // shutter press and saving the JPEG too.
            calculatePicturesRemaining();

            if (mPicturesRemaining < 1) {
                updateStorageHint(mPicturesRemaining);
            }

            if (!mHandler.hasMessages(RESTART_PREVIEW)) {
                long now = System.currentTimeMillis();
                mJpegCallbackFinishTime = now - mJpegPictureCallbackTime;
                Log.v(TAG, "mJpegCallbackFinishTime = "
                        + mJpegCallbackFinishTime + "ms");
                mJpegPictureCallbackTime = 0;
            }
            
            if (mIsImageCaptureIntent) {
            	doAttach();
            }
            
            if (mAnimationDone) {
            	Log.d(TAG, "BUG: updating after capture");
            	mThumbController.updateDisplayIfNeeded(0);
            }
        }
    }

    private byte[] mPreviewData;
    private final class PreviewFrameCallback implements PreviewCallback {
    	public PreviewFrameCallback() {}
    	
		@Override
		public void onPreviewFrame(byte[] data, android.hardware.Camera camera) {
			Log.d(TAG, "onPreviewFrame");
			
            mImageCapture.capture();
            if (!isPreviewAnimationDisable()) {
            	animatePreviewToThumb(data);
            }
		}
    	
    }
    
    private Bitmap loadPreviewBitmap(byte[] jpegData, int degree) {
    	//TODO use preview, so rotation is always correct?
    	Options opts = new Options();
    	opts.inJustDecodeBounds = true;
    	int sampleSize = 1;
    	opts.inSampleSize = sampleSize;
    	BitmapFactory.decodeByteArray(jpegData, 0, jpegData.length, opts);
    	int width = opts.outWidth;
    	int height = opts.outHeight;
    	
    	int surfaceWidth = mSurfaceView.getWidth();
    	int surfaceHeight = mSurfaceView.getHeight();
    	
    	while (width/sampleSize > surfaceWidth && height/sampleSize > surfaceHeight) {
    		sampleSize++;
    	}
    	
    	opts.inSampleSize = sampleSize;
    	opts.inJustDecodeBounds = false;
    	
    	return Util.rotate(BitmapFactory.decodeByteArray(jpegData, 0, jpegData.length, opts), degree);
    }
    
    private final class AutoFocusCallback
            implements android.hardware.Camera.AutoFocusCallback {
        public void onAutoFocus(
                boolean focused, android.hardware.Camera camera) {
            mFocusCallbackTime = System.currentTimeMillis();
            mAutoFocusTime = mFocusCallbackTime - mFocusStartTime;
            Log.v(TAG, "mAutoFocusTime = " + mAutoFocusTime + "ms");
            if (mFocusState == FOCUSING_SNAP_ON_FINISH) {
                // Take the picture no matter focus succeeds or fails. No need
                // to play the AF sound if we're about to play the shutter
                // sound.
                if (focused) {
                    mFocusState = FOCUS_SUCCESS;
                } else {
                    mFocusState = FOCUS_FAIL;
                }
                mImageCapture.onSnap();
            } else if (mFocusState == FOCUSING) {
                // User is half-pressing the focus key. Play the focus tone.
                // Do not take the picture now.
            	if (!isSoundFXDisabled()) {
	                ToneGenerator tg = mFocusToneGenerator;
	                if (tg != null) {
	                    tg.startTone(ToneGenerator.TONE_PROP_BEEP2);
	                }
            	}
                if (focused) {
                    mFocusState = FOCUS_SUCCESS;
                } else {
                    mFocusState = FOCUS_FAIL;
                }
            } else if (mFocusState == FOCUS_NOT_STARTED) {
                // User has released the focus key before focus completes.
                // Do nothing.
            }
            updateFocusIndicator();
        }
    }

    private static final class ErrorCallback
        implements android.hardware.Camera.ErrorCallback {
        public void onError(int error, android.hardware.Camera camera) {
            if (error == android.hardware.Camera.CAMERA_ERROR_SERVER_DIED) {
                 mMediaServerDied = true;
                 Log.v(TAG, "media server died");
            }
        }
    }

    private final class ZoomListener
            implements android.hardware.Camera.OnZoomChangeListener {
        public void onZoomChange(
                int value, boolean stopped, android.hardware.Camera camera) {
            Log.v(TAG, "Zoom changed: value=" + value + ". stopped="+ stopped);
            mZoomValue = value;
            // Keep mParameters up to date. We do not getParameter again in
            // takePicture. If we do not do this, wrong zoom value will be set.
            mParameters.setZoom(value);
            // We only care if the zoom is stopped. mZooming is set to true when
            // we start smooth zoom.
            if (stopped && mZoomState != ZOOM_STOPPED) {
                if (value != mTargetZoomValue) {
                    mCameraDevice.startSmoothZoom(mTargetZoomValue);
                    mZoomState = ZOOM_START;
                } else {
                    mZoomState = ZOOM_STOPPED;
                }
            }
        }
    }

    private class ImageCapture {

        private Uri mLastContentUri;

        byte[] mCaptureOnlyData;

        // Returns the rotation degree in the jpeg header.
        private int storeImage(byte[] data) {
            try {
                long dateTaken = System.currentTimeMillis();
                String title = createName(dateTaken);
                String filename = title + ".jpg";
                int[] degree = new int[1];
                mLastContentUri = ImageManager.addImage(
                        mContentResolver,
                        title,
                        dateTaken,
                        null, // location from gps/network
                        ImageManager.CAMERA_IMAGE_BUCKET_NAME, filename,
                        null, data,
                        degree);
                return degree[0];
            } catch (Exception ex) {
                Log.e(TAG, "Exception while compressing image.", ex);
                return 0;
            }
        }

        public int storeImage(final byte[] data,
                android.hardware.Camera camera) {
            if (!mIsImageCaptureIntent) {
                int degree = storeImage(data);
                sendBroadcast(new Intent(
                        "com.android.camera.NEW_PICTURE", mLastContentUri));
                if (isPreviewAnimationDisable()) {
                	setLastPictureThumb(data, degree,
                            mImageCapture.getLastCaptureUri());
                	mThumbController.updateDisplayIfNeeded(500);
                } else {
                	mThumbController.setUri(mImageCapture.getLastCaptureUri());
                }
                return degree;
            } else {
                mCaptureOnlyData = data;
                //showPostCaptureAlert();
            }
            return 0;
        }

        /**
         * Initiate the capture of an image.
         */
        public void initiate() {
            if (mCameraDevice == null) {
                return;
            }

        	mDoAnimation = true;
            mCameraDevice.setOneShotPreviewCallback(mPreviewFrameCallback);
        }

        public Uri getLastCaptureUri() {
            return mLastContentUri;
        }

        public byte[] getLastCaptureData() {
            return mCaptureOnlyData;
        }

        private void capture() {
            mCaptureOnlyData = null;
            
            // See android.hardware.Camera.Parameters.setRotation for
            // documentation.
            int rotation = 0;
            if (mOrientation != OrientationEventListener.ORIENTATION_UNKNOWN) {
            	CameraHolder holder = CameraHolder.instance();
            	if (holder.isFrontFacing(mCameraId)) {
            		rotation = (holder.getCameraOrientation(mCameraId, mOrientation) - mOrientation + 360) % 360;
            	} else {
            		rotation = (holder.getCameraOrientation(mCameraId, mOrientation) + mOrientation) % 360;
            	}
            }
            mParameters.setRotation(rotation);

            // Clear previous GPS location from the parameters.
            mParameters.removeGpsData();

            // We always encode GpsTimeStamp
            mParameters.setGpsTimestamp(System.currentTimeMillis() / 1000);

            mCameraDevice.setParameters(mParameters);
            
            if (isSoundFXDisabled()) {
            	mAudioManager.setStreamMute(AudioManager.STREAM_SYSTEM, true);
            }

            mCameraDevice.takePicture(mShutterCallback, mRawPictureCallback,
                    mPostViewPictureCallback, new JpegPictureCallback());
            mPreviewing = false;
        }

        public void onSnap() {
            // If we are already in the middle of taking a snapshot then ignore.
            if (mPausing || mStatus == SNAPSHOT_IN_PROGRESS) {
                return;
            }
            mCaptureStartTime = System.currentTimeMillis();
            mPostViewPictureCallbackTime = 0;
            mHeadUpDisplay.setEnabled(false);
            mStatus = SNAPSHOT_IN_PROGRESS;

            mImageCapture.initiate();
        }

        private void clearLastData() {
            mCaptureOnlyData = null;
        }
    }
    
    public boolean isSoundFXDisabled() {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		return prefs.getBoolean("disable_shutter_sound", true);
	}

    public boolean isPreviewAnimationDisable() {
    	SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		return prefs.getBoolean("disable_preview_animation", true);
    }
    
    private boolean saveDataToFile(String filePath, byte[] data) {
        FileOutputStream f = null;
        try {
            f = new FileOutputStream(filePath);
            f.write(data);
        } catch (IOException e) {
            return false;
        } finally {
            MenuHelper.closeSilently(f);
        }
        return true;
    }

    private void setLastPictureThumb(byte[] data, int degree, Uri uri) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 16;
        Bitmap lastPictureThumb =
                BitmapFactory.decodeByteArray(data, 0, data.length, options);
        lastPictureThumb = Util.rotate(lastPictureThumb, degree);
        mThumbController.setData(uri, lastPictureThumb);
    }

    private String createName(long dateTaken) {
        Date date = new Date(dateTaken);
        SimpleDateFormat dateFormat = new SimpleDateFormat(
                getString(R.string.image_file_name_format));

        return dateFormat.format(date);
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        
        mAudioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        
        setContentView(R.layout.camera);
        mSurfaceView = (SurfaceView) findViewById(R.id.camera_preview);

        mPreferences = new ComboPreferences(this);
        CameraSettings.upgradeGlobalPreferences(mPreferences.getGlobal());
        mCameraId = CameraSettings.readPreferredCameraId(mPreferences);
        mPreferences.setLocalId(this, mCameraId);
        CameraSettings.upgradeLocalPreferences(mPreferences.getLocal());

        mNumberOfCameras = CameraHolder.instance().getNumberOfCameras();

        // we need to reset exposure for the preview
        resetExposureCompensation();
        /*
         * To reduce startup time, we start the preview in another thread.
         * We make sure the preview is started at the end of onCreate.
         */
        Thread startPreviewThread = new Thread(new Runnable() {
            public void run() {
                try {
                    mStartPreviewFail = false;
                    startPreview();
                } catch (CameraHardwareException e) {
                    // In eng build, we throw the exception so that test tool
                    // can detect it and report it
                    if ("eng".equals(Build.TYPE)) {
                        throw new RuntimeException(e);
                    }
                    mStartPreviewFail = true;
                }
            }
        });
        startPreviewThread.start();

        // don't set mSurfaceHolder here. We have it set ONLY within
        // surfaceChanged / surfaceDestroyed, other parts of the code
        // assume that when it is set, the surface is also set.
        SurfaceHolder holder = mSurfaceView.getHolder();
        holder.addCallback(this);
        holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

        mIsImageCaptureIntent = isImageCaptureIntent();
        if (mIsImageCaptureIntent) {
            setupCaptureParams();
        }
        
        mIsLightboxPhotosIntent = isLightboxIntent();

        //View attachControlBar = findViewById(R.id.attach_control_bar);
        //View controlBar = findViewById(R.id.control_bar);
        /*if (mIsImageCaptureIntent) {
        	controlBar.setVisibility(View.GONE);
            attachControlBar.findViewById(R.id.btn_cancel).setOnClickListener(this);
            attachControlBar.findViewById(R.id.btn_retake).setOnClickListener(this);
            attachControlBar.findViewById(R.id.btn_done).setOnClickListener(this);
        } else {*/
        	//attachControlBar.setVisibility(View.GONE);
            //mSwitcher = ((Switcher) findViewById(R.id.camera_switch));
            //mSwitcher.setOnSwitchListener(this);
            //mSwitcher.addTouchView(findViewById(R.id.camera_switch_set));
        //}

        // Make sure preview is started.
        try {
            startPreviewThread.join();
            if (mStartPreviewFail) {
                showCameraErrorAndFinish();
                return;
            }
        } catch (InterruptedException ex) {
            // ignore
        }
    }

    private void changeHeadUpDisplayState() {
        // If the camera resumes behind the lock screen, the orientation
        // will be portrait. That causes OOM when we try to allocation GPU
        // memory for the GLSurfaceView again when the orientation changes. So,
        // we delayed initialization of HeadUpDisplay until the orientation
        // becomes landscape.
        Configuration config = getResources().getConfiguration();
        if (config.orientation == Configuration.ORIENTATION_LANDSCAPE
                && !mPausing && mFirstTimeInitialized) {
            if (mGLRootView == null) attachHeadUpDisplay();
        } else if (mGLRootView != null) {
            detachHeadUpDisplay();
        }
    }

    private void overrideHudSettings(final String flashMode,
            final String whiteBalance, final String focusMode) {
        mHeadUpDisplay.overrideSettings(
                CameraSettings.KEY_FLASH_MODE, flashMode,
                CameraSettings.KEY_WHITE_BALANCE, whiteBalance,
                CameraSettings.KEY_FOCUS_MODE, focusMode);
    }

    private void updateSceneModeInHud() {
        // If scene mode is set, we cannot set flash mode, white balance, and
        // focus mode, instead, we read it from driver
        if (!Parameters.SCENE_MODE_AUTO.equals(mSceneMode)) {
            overrideHudSettings(mParameters.getFlashMode(),
                    mParameters.getWhiteBalance(), mParameters.getFocusMode());
        } else {
            overrideHudSettings(null, null, null);
        }
    }

    private void initializeHeadUpDisplay() {
        CameraSettings settings = new CameraSettings(this, mInitialParams,
                CameraHolder.instance());
        mHeadUpDisplay.initialize(this,
                settings.getPreferenceGroup(R.xml.camera_preferences),
                getZoomRatios(), mOrientationCompensation);
        if (isZoomSupported()) {
            mHeadUpDisplay.setZoomListener(new ZoomControllerListener() {
                public void onZoomChanged(
                        int index, float ratio, boolean isMoving) {
                    onZoomValueChanged(index);
                }
            });
        }
        updateSceneModeInHud();
        initControlButtons();
        if (CameraHolder.instance().isFrontFacing(mCameraId)) {
        	mCameraTypeButton.setImageResource(R.drawable.btn_camera_front);
        } else {
        	mCameraTypeButton.setImageResource(R.drawable.btn_camera_rear);
        }
    }
    
    private void initControlButtons() {
    	String flashMode = mParameters.getFlashMode();
    	List<String> supportedFlashModes = mParameters.getSupportedFlashModes();
    	if (mFlashButton == null) {
    		mFlashButton = (RotateImageView)findViewById(R.id.btn_flash);
    	}
        if (flashMode == null || supportedFlashModes == null) {
        	mFlashButton.setVisibility(View.GONE);
        } else if (supportedFlashModes.size() == 1) {
        	mParameters.setFlashMode(supportedFlashModes.get(0));
        	mFlashButton.setVisibility(View.GONE);
        } else {
        	mFlashButton.setVisibility(View.VISIBLE);
        	String[] flashModes = getResources().getStringArray(R.array.pref_camera_flashmode_entryvalues);
        	if (flashMode.equals(flashModes[0])) {
        		mFlashButton.setImageResource(R.drawable.btn_camera_flashauto);
        	} else if (flashMode.equals(flashModes[1])) {
        		mFlashButton.setImageResource(R.drawable.btn_camera_flashon);        		
        	} else if (flashMode.equals(flashModes[2])) {
        		mFlashButton.setImageResource(R.drawable.btn_camera_flashoff);        		
        	}
        }
        
        if (mCameraTypeButton == null) {
        	mCameraTypeButton = (RotateImageView)findViewById(R.id.btn_camera_type);
    	}
        
        if (CameraHolder.instance().getNumberOfCameras() > 1) {
        	mCameraTypeButton.setVisibility(View.VISIBLE);
        } else {
        	mCameraTypeButton.setVisibility(View.GONE);
        }
        
        setControlsPadding();
    }

    private void attachHeadUpDisplay() {
        mHeadUpDisplay.setOrientation(mOrientationCompensation);
        if (isZoomSupported()) {
            mHeadUpDisplay.setZoomIndex(mZoomValue);
        }
        FrameLayout frame = (FrameLayout) findViewById(R.id.frame);
        mGLRootView = new GLRootView(this);
        mGLRootView.setContentPane(mHeadUpDisplay);
        frame.addView(mGLRootView);
    }

    private void detachHeadUpDisplay() {
        mHeadUpDisplay.collapse();
        ((ViewGroup) mGLRootView.getParent()).removeView(mGLRootView);
        mGLRootView = null;
    }

    public static int roundOrientation(int orientation) {
        return ((orientation + 45) / 90 * 90) % 360;
    }
 
	private OrientationChangeListener mOrientationChangeListener = new OrientationChangeListener() {
		@Override
		public void onOrientationChanged(int orientation) {
			// We keep the last known orientation. So if the user first orient
			// the camera then point the camera to floor or sky, we still have
			// the correct orientation.
			if (orientation == android.view.OrientationEventListener.ORIENTATION_UNKNOWN) {
				return;
			}
			mOrientation = roundOrientation(orientation);
			// When the screen is unlocked, display rotation may change. Always
			// calculate the up-to-date orientationCompensation.
			int orientationCompensation = mOrientation
					+ Util.getDisplayRotation(Camera.this);
			if (mOrientationCompensation != orientationCompensation) {
				mOrientationCompensation = orientationCompensation;
				// if (!mIsImageCaptureIntent) {
				setOrientationIndicator(mOrientationCompensation);
				// }
				mHeadUpDisplay.setOrientation(mOrientationCompensation);
			}
		}
	};

	private void setInitialOrientation() {
		int orientation = ((CameraApplication)getApplication()).getLastKnownOrientation();
		if (orientation == android.view.OrientationEventListener.ORIENTATION_UNKNOWN) {
			return;
		}
		mOrientation = roundOrientation(orientation);
		// When the screen is unlocked, display rotation may change. Always
		// calculate the up-to-date orientationCompensation.
		int orientationCompensation = mOrientation
				+ Util.getDisplayRotation(Camera.this);
		if (mOrientationCompensation != orientationCompensation) {
			mOrientationCompensation = orientationCompensation;
			((RotateImageView) findViewById(
	                R.id.review_thumbnail)).setDegreeInstant(mOrientationCompensation);
	        ((RotateImageView) findViewById(
	                R.id.btn_flash)).setDegreeInstant(mOrientationCompensation);
	        ((RotateImageView) findViewById(
	                R.id.btn_camera_type)).setDegreeInstant(mOrientationCompensation);
			mHeadUpDisplay.setOrientation(mOrientationCompensation);
		}
	}
	
    private void setOrientationIndicator(int degree) {
        ((RotateImageView) findViewById(
                R.id.review_thumbnail)).setDegree(degree);
        ((RotateImageView) findViewById(
                R.id.btn_flash)).setDegree(degree);
        ((RotateImageView) findViewById(
                R.id.btn_camera_type)).setDegree(degree);
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mMediaProviderClient != null) {
            mMediaProviderClient.release();
            mMediaProviderClient = null;
        }
    }

    private void checkStorage() {
        calculatePicturesRemaining();
        updateStorageHint(mPicturesRemaining);
    }

    private void startGallery() {
		if (mIsLightboxPhotosIntent) {
			setResult(RESULT_CANCELED);
			finish();
		} else 	if (mThumbController.isUriValid()) { 
	    	// Open in the gallery
            Intent intent = new Intent(Util.REVIEW_ACTION, mThumbController.getUri());
            try {
                startActivity(intent);
            } catch (ActivityNotFoundException ex) {
                try {
                    intent = new Intent(Intent.ACTION_VIEW, mThumbController.getUri());
                    startActivity(intent);
                } catch (ActivityNotFoundException e) {
                    Log.e(TAG, "review image fail", e);
                }
            }
        } else {
            Log.e(TAG, "Can't view last image.");
        }
    }
    
    public void onClick(View v) {
        switch (v.getId()) {
            /*case R.id.btn_retake:
                hidePostCaptureAlert();
                restartPreview();
                break;*/
            case R.id.review_thumbnail:
                if (isCameraIdle()) {
                    //viewLastImage();
                	startGallery();
                }
                break;
            /*case R.id.btn_done:
                doAttach();
                break;
            case R.id.btn_cancel:
                doCancel();
                break;*/
            case R.id.btn_flash:
            	String flashMode = mParameters.getFlashMode();
            	String[] flashModes = getResources().getStringArray(R.array.pref_camera_flashmode_entryvalues);
            	Editor editor = mPreferences.edit();
            	if (flashMode.equals(flashModes[0])) {
                	editor.putString("pref_camera_flashmode_key", flashModes[1]);
            	} else if (flashMode.equals(flashModes[1])) {
                	editor.putString("pref_camera_flashmode_key", flashModes[2]);   		
            	} else if (flashMode.equals(flashModes[2])) {
                	editor.putString("pref_camera_flashmode_key", flashModes[0]); 		
            	};
            	editor.commit();
            	onSharedPreferenceChanged();
            	break;
            case R.id.btn_camera_type:
            	if (CameraHolder.instance().isFrontFacing(mCameraId)) {
            		int rearCamId = CameraHolder.instance().getRearFacingCameraId();
            		switchCameraId(rearCamId);
            	} else {
            		int frontCamId = CameraHolder.instance().getFrontFacingCameraId();
            		switchCameraId(frontCamId);
            	}
            	break;
        }
    }

    private Bitmap createCaptureBitmap(byte[] data) {
        // This is really stupid...we just want to read the orientation in
        // the jpeg header.
        String filepath = ImageManager.getTempJpegPath();
        int degree = 0;
        if (saveDataToFile(filepath, data)) {
            degree = ImageManager.getExifOrientation(filepath);
            new File(filepath).delete();
        }

        // Limit to 50k pixels so we can return it in the intent.
        Bitmap bitmap = Util.makeBitmap(data, 50 * 1024);
        bitmap = Util.rotate(bitmap, degree);
        return bitmap;
    }

    private void doAttach() {
        if (mPausing) {
            return;
        }

        byte[] data = mImageCapture.getLastCaptureData();

        if (mCropValue == null) {
            // First handle the no crop case -- just return the value.  If the
            // caller specifies a "save uri" then write the data to it's
            // stream. Otherwise, pass back a scaled down version of the bitmap
            // directly in the extras.
            if (mSaveUri != null) {
                OutputStream outputStream = null;
                try {
                    outputStream = mContentResolver.openOutputStream(mSaveUri);
                    outputStream.write(data);
                    outputStream.close();

                    setResult(RESULT_OK);
                    finish();
                } catch (IOException ex) {
                    // ignore exception
                } finally {
                    Util.closeSilently(outputStream);
                }
            } else {
            	//TODO need write to temp file and pass on to the filter activity
            	
                Bitmap bitmap = createCaptureBitmap(data);
                setResult(RESULT_OK,
                        new Intent("inline-data").putExtra("data", bitmap));
                finish();
            }
        } else {
            // Save the image to a temp file and invoke the cropper
            Uri tempUri = null;
            FileOutputStream tempStream = null;
            try {
                File path = getFileStreamPath(sTempCropFilename);
                path.delete();
                tempStream = openFileOutput(sTempCropFilename, 0);
                tempStream.write(data);
                tempStream.close();
                tempUri = Uri.fromFile(path);
            } catch (FileNotFoundException ex) {
                setResult(Activity.RESULT_CANCELED);
                finish();
                return;
            } catch (IOException ex) {
                setResult(Activity.RESULT_CANCELED);
                finish();
                return;
            } finally {
                Util.closeSilently(tempStream);
            }

            Bundle newExtras = new Bundle();
            if (mCropValue.equals("circle")) {
                newExtras.putString("circleCrop", "true");
            }
            if (mSaveUri != null) {
                newExtras.putParcelable(MediaStore.EXTRA_OUTPUT, mSaveUri);
            } else {
                newExtras.putBoolean("return-data", true);
            }

            Intent cropIntent = new Intent("com.android.camera.action.CROP");

            cropIntent.setData(tempUri);
            cropIntent.putExtras(newExtras);

            startActivityForResult(cropIntent, CROP_MSG);
        }
    }
    
    private void doCancel() {
        setResult(RESULT_CANCELED, new Intent());
        finish();
    }

    public void onShutterButtonFocus(ShutterButton button, boolean pressed) {
        if (mPausing) {
            return;
        }
        switch (button.getId()) {
            case R.id.shutter_button:
                doFocus(pressed);
                break;
        }
    }

    public void onShutterButtonClick(ShutterButton button) {
        if (mPausing) {
            return;
        }
        switch (button.getId()) {
            case R.id.shutter_button:
                doSnap();
                break;
        }
    }

    private OnScreenHint mStorageHint;

    private void updateStorageHint(int remaining) {
        String noStorageText = null;

        if (remaining == MenuHelper.NO_STORAGE_ERROR) {
            String state = Environment.getExternalStorageState();
            if (state == Environment.MEDIA_CHECKING) {
                noStorageText = getString(R.string.preparing_sd);
            } else {
                noStorageText = getString(R.string.no_storage);
            }
        } else if (remaining == MenuHelper.CANNOT_STAT_ERROR) {
            noStorageText = getString(R.string.access_sd_fail);
        } else if (remaining < 1) {
            noStorageText = getString(R.string.not_enough_space);
        }

        if (noStorageText != null) {
            if (mStorageHint == null) {
                mStorageHint = OnScreenHint.makeText(this, noStorageText);
            } else {
                mStorageHint.setText(noStorageText);
            }
            mStorageHint.show();
        } else if (mStorageHint != null) {
            mStorageHint.cancel();
            mStorageHint = null;
        }
    }

    private void installIntentFilter() {
        // install an intent filter to receive SD card related events.
        IntentFilter intentFilter =
                new IntentFilter(Intent.ACTION_MEDIA_MOUNTED);
        intentFilter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
        intentFilter.addAction(Intent.ACTION_MEDIA_SCANNER_FINISHED);
        intentFilter.addAction(Intent.ACTION_MEDIA_CHECKING);
        intentFilter.addDataScheme("file");
        registerReceiver(mReceiver, intentFilter);
        mDidRegister = true;
    }

    private void initializeFocusTone() {
        // Initialize focus tone generator.
        try {
            mFocusToneGenerator = new ToneGenerator(
                    AudioManager.STREAM_SYSTEM, FOCUS_BEEP_VOLUME);
        } catch (Throwable ex) {
            Log.w(TAG, "Exception caught while creating tone generator: ", ex);
            mFocusToneGenerator = null;
        }
    }

    public static final String SCREEN_BRIGHTNESS_MODE = "screen_brightness_mode";
    public static final int SCREEN_BRIGHTNESS_MODE_AUTOMATIC = 0x00000001;
    public static final int SCREEN_BRIGHTNESS_MODE_MANUAL = 0x00000000;
    private void initializeScreenBrightness() {
        Window win = getWindow();
        // Overright the brightness settings if it is automatic
        int mode;
        if (Build.VERSION.SDK_INT >= 0x00000008) {
	        mode = Settings.System.getInt(
	                getContentResolver(),
	                Settings.System.SCREEN_BRIGHTNESS_MODE,
	                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
	        if (mode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC) {
	            WindowManager.LayoutParams winParams = win.getAttributes();
	            winParams.screenBrightness = DEFAULT_CAMERA_BRIGHTNESS;
	            win.setAttributes(winParams);
	        }
        } else {
        	mode = Settings.System.getInt(
	                getContentResolver(),
	                SCREEN_BRIGHTNESS_MODE,
	                SCREEN_BRIGHTNESS_MODE_MANUAL);
	        if (mode == SCREEN_BRIGHTNESS_MODE_AUTOMATIC) {
	            WindowManager.LayoutParams winParams = win.getAttributes();
	            winParams.screenBrightness = DEFAULT_CAMERA_BRIGHTNESS;
	            win.setAttributes(winParams);
	        }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        ((CameraApplication)getApplication()).requestLocationUpdate(false);
        
        mPausing = false;
        mJpegPictureCallbackTime = 0;
        mZoomValue = 0;
        mImageCapture = new ImageCapture();

        // Start the preview if it is not started.
        if (!mPreviewing && !mStartPreviewFail) {
            resetExposureCompensation();
            if (!restartPreview()) return;
        }

        if (mSurfaceHolder != null) {
            // If first time initialization is not finished, put it in the
            // message queue.
            if (!mFirstTimeInitialized) {
                mHandler.sendEmptyMessage(FIRST_TIME_INIT);
            } else {
                initializeSecondTime();
            }
        }
        keepScreenOnAwhile();
    }

    @Override
    public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
        changeHeadUpDisplayState();
    }

    private static ImageManager.DataLocation dataLocation() {
        return ImageManager.DataLocation.EXTERNAL;
    }

    @Override
    protected void onPause() {
        mPausing = true;
        
        mAudioManager.setStreamMute(AudioManager.STREAM_SYSTEM, false);
        
        stopPreview();
        // Close the camera now because other activities may need to use it.
        closeCamera();
        resetScreenOn();
        changeHeadUpDisplayState();

        if (mFirstTimeInitialized) {
            if (!mIsImageCaptureIntent) {
            	String lastPhotoThumbPath = getLastPhotoThumbPath();
            	if (lastPhotoThumbPath != null) {
            		mThumbController.storeData(lastPhotoThumbPath);
            	}
            }
            //hidePostCaptureAlert();
        }

        if (mDidRegister) {
            unregisterReceiver(mReceiver);
            mDidRegister = false;
        }

        if (mFocusToneGenerator != null) {
            mFocusToneGenerator.release();
            mFocusToneGenerator = null;
        }

        if (mStorageHint != null) {
            mStorageHint.cancel();
            mStorageHint = null;
        }

        // If we are in an image capture intent and has taken
        // a picture, we just clear it in onPause.
        mImageCapture.clearLastData();
        mImageCapture = null;

        // Remove the messages in the event queue.
        mHandler.removeMessages(RESTART_PREVIEW);
        mHandler.removeMessages(FIRST_TIME_INIT);

        ((CameraApplication)getApplication()).deregisterOrientationChangeListener(mOrientationChangeListener);
        
        super.onPause();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    	if (requestCode == CROP_MSG) {
    		Intent intent = new Intent();
            if (data != null) {
                Bundle extras = data.getExtras();
                if (extras != null) {
                    intent.putExtras(extras);
                }
            }
            setResult(resultCode, intent);
            finish();

            File path = getFileStreamPath(sTempCropFilename);
            path.delete();
    	}
    }

    private boolean canTakePicture() {
        return isCameraIdle() && mPreviewing && (mPicturesRemaining > 0);
    }

    private void autoFocus() {
        // Initiate autofocus only when preview is started and snapshot is not
        // in progress.
        if (canTakePicture()) {
            mHeadUpDisplay.setEnabled(false);
            Log.v(TAG, "Start autofocus.");
            mFocusStartTime = System.currentTimeMillis();
            mFocusState = FOCUSING;
            updateFocusIndicator();
            mCameraDevice.autoFocus(mAutoFocusCallback);
        }
    }

    private void cancelAutoFocus() {
        // User releases half-pressed focus key.
        if (mStatus != SNAPSHOT_IN_PROGRESS && (mFocusState == FOCUSING
                || mFocusState == FOCUS_SUCCESS || mFocusState == FOCUS_FAIL)) {
            Log.v(TAG, "Cancel autofocus.");
            mHeadUpDisplay.setEnabled(true);
            mCameraDevice.cancelAutoFocus();
        }
        if (mFocusState != FOCUSING_SNAP_ON_FINISH) {
            clearFocusState();
        }
    }

    private void clearFocusState() {
        mFocusState = FOCUS_NOT_STARTED;
        updateFocusIndicator();
    }

    private void updateFocusIndicator() {
        if (mFocusRectangle == null) return;

        if (mFocusState == FOCUSING || mFocusState == FOCUSING_SNAP_ON_FINISH) {
            mFocusRectangle.showStart();
        } else if (mFocusState == FOCUS_SUCCESS) {
            mFocusRectangle.showSuccess();
        } else if (mFocusState == FOCUS_FAIL) {
            mFocusRectangle.showFail();
        } else {
            mFocusRectangle.clear();
        }
    }

    @Override
    public void onBackPressed() {
        if (!isCameraIdle()) {
            // ignore backs while we're taking a picture
            return;
        } else if (mHeadUpDisplay == null || !mHeadUpDisplay.collapse()) {
        	doCancel();
            super.onBackPressed();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_FOCUS:
                if (mFirstTimeInitialized && event.getRepeatCount() == 0) {
                    doFocus(true);
                }
                return true;
            case KeyEvent.KEYCODE_CAMERA:
                if (mFirstTimeInitialized && event.getRepeatCount() == 0) {
                    doSnap();
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
                // If we get a dpad center event without any focused view, move
                // the focus to the shutter button and press it.
                if (mFirstTimeInitialized && event.getRepeatCount() == 0) {
                    // Start auto-focus immediately to reduce shutter lag. After
                    // the shutter button gets the focus, doFocus() will be
                    // called again but it is fine.
                    if (mHeadUpDisplay.collapse()) return true;
                    doFocus(true);
                    if (mShutterButton.isInTouchMode()) {
                        mShutterButton.requestFocusFromTouch();
                    } else {
                        mShutterButton.requestFocus();
                    }
                    mShutterButton.setPressed(true);
                }
                return true;
        }

        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_FOCUS:
                if (mFirstTimeInitialized) {
                    doFocus(false);
                }
                return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    private void doSnap() {
        if (mHeadUpDisplay.collapse()) return;

        Log.v(TAG, "doSnap: mFocusState=" + mFocusState);
        // If the user has half-pressed the shutter and focus is completed, we
        // can take the photo right away. If the focus mode is infinity, we can
        // also take the photo.
        if (mFocusMode.equals(Parameters.FOCUS_MODE_INFINITY)
                || mFocusMode.equals(Parameters.FOCUS_MODE_FIXED)
                || mFocusMode.equals(ParameterUtils.FOCUS_MODE_EDOF)
                || (mFocusState == FOCUS_SUCCESS
                || mFocusState == FOCUS_FAIL)) {
            mImageCapture.onSnap();
        } else if (mFocusState == FOCUSING) {
            // Half pressing the shutter (i.e. the focus button event) will
            // already have requested AF for us, so just request capture on
            // focus here.
            mFocusState = FOCUSING_SNAP_ON_FINISH;
        } else if (mFocusState == FOCUS_NOT_STARTED) {
            // Focus key down event is dropped for some reasons. Just ignore.
        }
    }

    private void doFocus(boolean pressed) {
        // Do the focus if the mode is not infinity.
        if (mHeadUpDisplay.collapse()) return;
        if (!(mFocusMode.equals(Parameters.FOCUS_MODE_INFINITY)
                  || mFocusMode.equals(Parameters.FOCUS_MODE_FIXED)
                  || mFocusMode.equals(ParameterUtils.FOCUS_MODE_EDOF))) {
            if (pressed) {  // Focus key down.
                autoFocus();
            } else {  // Focus key up.
                cancelAutoFocus();
            }
        }
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
        // Make sure we have a surface in the holder before proceeding.
        if (holder.getSurface() == null) {
            return;
        }

        // We need to save the holder for later use, even when the mCameraDevice
        // is null. This could happen if onResume() is invoked after this
        // function.
        mSurfaceHolder = holder;

        // The mCameraDevice will be null if it fails to connect to the camera
        // hardware. In this case we will show a dialog and then finish the
        // activity, so it's OK to ignore it.
        if (mCameraDevice == null) return;

        // Sometimes surfaceChanged is called after onPause or before onResume.
        // Ignore it.
        if (mPausing || isFinishing()) return;
        
        if (mPreviewing && holder.isCreating()) {
            // Set preview display if the surface is being created and preview
            // was already started. That means preview display was set to null
            // and we need to set it now.
            setPreviewDisplay(holder);
        } else {
            // 1. Restart the preview if the size of surface was changed. The
            // framework may not support changing preview display on the fly.
            // 2. Start the preview now if surface was destroyed and preview
            // stopped.
        	// Set the preview frame aspect ratio according to the picture size.
        	//restartPreview();
            mHandler.sendEmptyMessageDelayed(RESTART_PREVIEW, 1000);
        }

        // If first time initialization is not finished, send a message to do
        // it later. We want to finish surfaceChanged as soon as possible to let
        // user see preview first.
        if (!mFirstTimeInitialized) {
            mHandler.sendEmptyMessage(FIRST_TIME_INIT);
        } else {
            initializeSecondTime();
        }
    }

    public void surfaceCreated(SurfaceHolder holder) {
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
        stopPreview();
        mSurfaceHolder = null;
    }

    private void closeCamera() {
        if (mCameraDevice != null) {
            CameraHolder.instance().release();
            if (Build.VERSION.SDK_INT >= 0x00000008) {
            	mCameraDevice.setZoomChangeListener(null);
            }
            mCameraDevice = null;
            mPreviewing = false;
        }
    }

    private void ensureCameraDevice() throws CameraHardwareException {
        if (mCameraDevice == null) {
            mCameraDevice = CameraHolder.instance().open(mCameraId);
            mInitialParams = mCameraDevice.getParameters();
        }
    }

    private void updateLastImage() {
    	final String lastPhotoThumbPath = getLastPhotoThumbPath();
    	if (lastPhotoThumbPath != null) {
    		Bitmap bitmap = BitmapFactory.decodeFile(lastPhotoThumbPath);
    		mThumbController.setData(Uri.fromFile(new File(lastPhotoThumbPath)), bitmap);   		
    	} else {
    		mThumbController.setData(null, null);
    	}
    	
       /* IImageList list = ImageManager.makeImageList(
            mContentResolver,
            dataLocation(),
            ImageManager.INCLUDE_IMAGES,
            ImageManager.SORT_ASCENDING,
            ImageManager.CAMERA_IMAGE_BUCKET_ID);
        int count = list.getCount();
        if (count > 0) {
            IImage image = list.getImageAt(count - 1);
            Uri uri = image.fullSizeImageUri();
            mThumbController.setData(uri, image.miniThumbBitmap());
        } else {
            mThumbController.setData(null, null);
        }
        list.close();*/
    }

    private void showCameraErrorAndFinish() {
        Resources ress = getResources();
        Util.showFatalErrorAndFinish(Camera.this,
                ress.getString(R.string.camera_error_title),
                ress.getString(R.string.cannot_connect_camera));
    }

    private boolean restartPreview() {
        try {
            startPreview();
        } catch (CameraHardwareException e) {
            showCameraErrorAndFinish();
            return false;
        }
        return true;
    }

    private boolean mDoAnimation = false;
    private boolean mAnimationDone = false;
    private void animatePreviewToThumb(byte[] data) {
    	if (!mDoAnimation || mLastPictureButton == null || data == null) {
    		return;
    	}
    	
    	mAnimationDone = false;
    	
		//mPreviewFrameData = new byte[data.length];
		//System.arraycopy(data, 0, mPreviewFrameData, 0, data.length);
		//Log.d(TAG, "data="+mPreviewFrameData);
		int width = mParameters.getPreviewSize().width;
		int height = mParameters.getPreviewSize().height;
		int[] pixels = new int[width*height];
		Util.decodeYUV(pixels, data, width,height);
		Bitmap bitmap = Bitmap.createBitmap(pixels, width, height, Config.RGB_565);
		if (CameraHolder.instance().isFrontFacing(mCameraId)) {
			bitmap = Util.flipHorizontally(bitmap);
		}
    	
    	final ImageView previewImage = (ImageView)findViewById(R.id.imageViewPreview);
    	
    	//if (!CameraHolder.instance().isFrontFacing(mCameraId)) {
        	//Disable animation for front facing camera for now,
        	//as we have an orientation issue. Should be fixed when using preview frame for bitmap
        	previewImage.setImageBitmap(bitmap);
        //}
    	
    	int[] origin = new int[2];
    	previewImage.getLocationInWindow(origin);
    	int[] destination = new int[2];
    	mLastPictureButton.getLocationInWindow(destination);
    	
    	ScaleAnimation scaleAnim = new ScaleAnimation(1f,
								    			(float)(mLastPictureButton.getWidth()+9)/previewImage.getWidth(),
								    			1f,
								    			(float)(mLastPictureButton.getHeight()-9)/previewImage.getHeight(),
								    			ScaleAnimation.ABSOLUTE,
								    			destination[0]+12+mLastPictureButton.getWidth(),
								    			ScaleAnimation.ABSOLUTE,
								    			destination[1]+12);
    	scaleAnim.setDuration(500);
    	scaleAnim.setStartOffset(0);
    	scaleAnim.setAnimationListener(new AnimationListener() {			
			@Override
			public void onAnimationStart(Animation animation) {
			}
			
			@Override
			public void onAnimationRepeat(Animation animation) {
			}
			
			@Override
			public void onAnimationEnd(Animation animation) {
				Drawable drawable = previewImage.getDrawable();
				if (drawable != null) {
					Bitmap bitmap = ((BitmapDrawable)drawable).getBitmap();
					mThumbController.updateThumb(bitmap, mOrientationCompensation, false);
				}
			}
		});
    	scaleAnim.setInterpolator(new DecelerateInterpolator(2.0f));
    	    	
    	AlphaAnimation alphaAnimation = new AlphaAnimation(1.0f, 0.0f);
    	alphaAnimation.setDuration(1100);
    	alphaAnimation.setStartOffset(500);    	
    	
    	AnimationSet animation = new AnimationSet(true);
    	animation.addAnimation(scaleAnim);
    	animation.addAnimation(alphaAnimation);
    	
    	animation.setAnimationListener(new AnimationListener() {			
			@Override
			public void onAnimationStart(Animation animation) {
			}
			
			@Override
			public void onAnimationRepeat(Animation animation) {
			}
			
			@Override
			public void onAnimationEnd(Animation animation) {
				mAnimationDone = true;
				previewImage.setVisibility(View.INVISIBLE);
				Drawable drawable = previewImage.getDrawable();
				if (drawable != null) {
					Bitmap bitmap = ((BitmapDrawable)drawable).getBitmap();
					previewImage.setImageBitmap(null);
					if (bitmap != null && !bitmap.isRecycled()) {
						bitmap.recycle();
					}
				}
			}
		});

        previewImage.setVisibility(View.VISIBLE);
    	previewImage.startAnimation(animation);
        
        mDoAnimation = false;
    }
    
    private void setPreviewDisplay(SurfaceHolder holder) {
        try {
            mCameraDevice.setPreviewDisplay(holder);
        } catch (Throwable ex) {
            closeCamera();
            throw new RuntimeException("setPreviewDisplay failed", ex);
        }
    }

    private void startPreview() throws CameraHardwareException {
        if (mPausing || isFinishing()) return;

        ensureCameraDevice();
        // If we're previewing already, stop the preview first (this will blank
        // the screen).
        if (mPreviewing) stopPreview();

        setPreviewDisplay(mSurfaceHolder);
        //Util.setCameraDisplayOrientation(this, mCameraId, mCameraDevice);
        setCameraParameters(UPDATE_PARAM_ALL);

        mCameraDevice.setErrorCallback(mErrorCallback);

        try {
            Log.v(TAG, "startPreview");
            mCameraDevice.startPreview();
        } catch (Throwable ex) {
            closeCamera();
            throw new RuntimeException("startPreview failed", ex);
        }
        mPreviewing = true;
        mZoomState = ZOOM_STOPPED;
        mStatus = IDLE;
    }

    private void stopPreview() {
        if (mCameraDevice != null && mPreviewing) {
            Log.v(TAG, "stopPreview");
            mCameraDevice.stopPreview();
        }
        mPreviewing = false;
        // If auto focus was in progress, it would have been canceled.
        clearFocusState();
    }

    private Size getOptimalPreviewSize(List<Size> sizes, double targetRatio) {
        final double ASPECT_TOLERANCE = 0.05;
        if (sizes == null) return null;

        Size optimalSize = null;
        double minDiff = Double.MAX_VALUE;

        // Because of bugs of overlay and layout, we sometimes will try to
        // layout the viewfinder in the portrait orientation and thus get the
        // wrong size of mSurfaceView. When we change the preview size, the
        // new overlay will be created before the old one closed, which causes
        // an exception. For now, just get the screen size

        Display display = getWindowManager().getDefaultDisplay();
        int targetHeight = Math.min(display.getHeight(), display.getWidth());

        if (targetHeight <= 0) {
            // We don't know the size of SurefaceView, use screen height
            WindowManager windowManager = (WindowManager)
                    getSystemService(Context.WINDOW_SERVICE);
            targetHeight = windowManager.getDefaultDisplay().getHeight();
        }

        // Try to find an size match aspect ratio and size
        for (Size size : sizes) {
            double ratio = (double) size.width / size.height;
            if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE) continue;
            if (Math.abs(size.height - targetHeight) < minDiff) {
                optimalSize = size;
                minDiff = Math.abs(size.height - targetHeight);
            }
        }

        // Cannot find the one match the aspect ratio, ignore the requirement
        if (optimalSize == null) {
            Log.v(TAG, "No preview size match the aspect ratio");
            minDiff = Double.MAX_VALUE;
            for (Size size : sizes) {
                if (Math.abs(size.height - targetHeight) < minDiff) {
                    optimalSize = size;
                    minDiff = Math.abs(size.height - targetHeight);
                }
            }
        }
        return optimalSize;
    }

    private static boolean isSupported(String value, List<String> supported) {
        return supported == null ? false : supported.indexOf(value) >= 0;
    }

    private void updateCameraParametersInitialize() {
        // Reset preview frame rate to the maximum because it may be lowered by
        // video camera application.
        List<Integer> frameRates = mParameters.getSupportedPreviewFrameRates();
        if (frameRates != null) {
            Integer max = Collections.max(frameRates);
            mParameters.setPreviewFrameRate(max);
        }

    }

    private void updateCameraParametersZoom() {
        // Set zoom.
        if (ParameterUtils.isZoomSupported(mParameters)) {
        	ParameterUtils.setZoom(mParameters, mZoomValue);
        }
    }

    private void updateCameraParametersPreference() {
        // Set picture size.
        String pictureSize = mPreferences.getString(
               CameraSettings.KEY_PICTURE_SIZE, null);
       if (pictureSize == null) {
            CameraSettings.initialCameraPictureSize(this, mParameters);
            /*if (!CameraHolder.instance().isFrontFacing(mCameraId)) {
            	Size pictureSize = mParameters.getPictureSize();
            	CameraApplication application = (CameraApplication)getApplication();
            	application.setMaxPhotoPixels(pictureSize.width*pictureSize.height); //Used by image processing service
            }*/
        } else {
            List<Size> supported = mParameters.getSupportedPictureSizes();
            CameraSettings.setCameraPictureSize(
                    pictureSize, supported, mParameters);
        }

        // Set the preview frame aspect ratio according to the picture size.
        PreviewFrameLayout frameLayout =
                (PreviewFrameLayout) findViewById(R.id.frame_layout);
        frameLayout.setAspectRatio(CameraHolder.instance().getAspectRatio());

        // Set a preview size that is closest to the viewfinder height and has
        // the right aspect ratio.
        List<Size> sizes = mParameters.getSupportedPreviewSizes();
        Size size = mParameters.getPictureSize();
        Size optimalSize = getOptimalPreviewSize(
                sizes, (double) size.width / size.height);
        if (optimalSize != null) {
            Size original = mParameters.getPreviewSize();
            if (!original.equals(optimalSize)) {
                mParameters.setPreviewSize(optimalSize.width, optimalSize.height);

                // Zoom related settings will be changed for different preview
                // sizes, so set and read the parameters to get lastest values
                mCameraDevice.setParameters(mParameters);
                mParameters = mCameraDevice.getParameters();
            }
        }

        // Since change scene mode may change supported values,
        // Set scene mode first,
        mSceneMode = mPreferences.getString(
                CameraSettings.KEY_SCENE_MODE,
                getString(R.string.pref_camera_scenemode_default));
        if (isSupported(mSceneMode, mParameters.getSupportedSceneModes())) {
            if (!mParameters.getSceneMode().equals(mSceneMode)) {
                mParameters.setSceneMode(mSceneMode);
                mCameraDevice.setParameters(mParameters);

                // Setting scene mode will change the settings of flash mode,
                // white balance, and focus mode. Here we read back the
                // parameters, so we can know those settings.
                mParameters = mCameraDevice.getParameters();
            }
        } else {
            mSceneMode = mParameters.getSceneMode();
            if (mSceneMode == null) {
                mSceneMode = Parameters.SCENE_MODE_AUTO;
            }
        }

        // Set JPEG quality.
        String jpegQuality = mPreferences.getString(
                CameraSettings.KEY_JPEG_QUALITY,
                getString(R.string.pref_camera_jpegquality_default));
        mParameters.setJpegQuality(JpegEncodingQualityMappings.getQualityNumber(jpegQuality));

        // For the following settings, we need to check if the settings are
        // still supported by latest driver, if not, ignore the settings.

        // Set color effect parameter.
        String colorEffect = mPreferences.getString(
                CameraSettings.KEY_COLOR_EFFECT,
                getString(R.string.pref_camera_coloreffect_default));
        if (isSupported(colorEffect, mParameters.getSupportedColorEffects())) {
            mParameters.setColorEffect(colorEffect);
        }

        // Set exposure compensation
        String exposure = mPreferences.getString(
                CameraSettings.KEY_EXPOSURE,
                getString(R.string.pref_exposure_default));
        try {
            int value = Integer.parseInt(exposure);
            int max = ParameterUtils.getMaxExposureCompensation(mParameters);
            int min = ParameterUtils.getMinExposureCompensation(mParameters);
            if (value >= min && value <= max) {
            	ParameterUtils.setExposureCompensation(mParameters, value);
            } else {
                Log.w(TAG, "invalid exposure range: " + exposure);
            }
        } catch (NumberFormatException e) {
            Log.w(TAG, "invalid exposure: " + exposure);
        }

        if (mHeadUpDisplay != null) updateSceneModeInHud();

        if (Parameters.SCENE_MODE_AUTO.equals(mSceneMode)) {
            // Set flash mode.
            String flashMode = mPreferences.getString(
                    CameraSettings.KEY_FLASH_MODE,
                    getString(R.string.pref_camera_flashmode_default));
            List<String> supportedFlash = mParameters.getSupportedFlashModes();
            if (isSupported(flashMode, supportedFlash)) {
                mParameters.setFlashMode(flashMode);
            } else {
                flashMode = mParameters.getFlashMode();
                if (flashMode == null) {
                    flashMode = getString(
                            R.string.pref_camera_flashmode_no_flash);
                }
            }

            // Set white balance parameter.
            String whiteBalance = mPreferences.getString(
                    CameraSettings.KEY_WHITE_BALANCE,
                    getString(R.string.pref_camera_whitebalance_default));
            if (isSupported(whiteBalance,
                    mParameters.getSupportedWhiteBalance())) {
                mParameters.setWhiteBalance(whiteBalance);
            } else {
                whiteBalance = mParameters.getWhiteBalance();
                if (whiteBalance == null) {
                    whiteBalance = Parameters.WHITE_BALANCE_AUTO;
                }
            }

            // Set focus mode.
            mFocusMode = mPreferences.getString(
                    CameraSettings.KEY_FOCUS_MODE,
                    getString(R.string.pref_camera_focusmode_default));
            if (isSupported(mFocusMode, mParameters.getSupportedFocusModes())) {
                mParameters.setFocusMode(mFocusMode);
            } else {
                mFocusMode = mParameters.getFocusMode();
                if (mFocusMode == null) {
                    mFocusMode = Parameters.FOCUS_MODE_AUTO;
                }
            }
        } else {
            mFocusMode = mParameters.getFocusMode();
        }
    }

    // We separate the parameters into several subsets, so we can update only
    // the subsets actually need updating. The PREFERENCE set needs extra
    // locking because the preference can be changed from GLThread as well.
    private void setCameraParameters(int updateSet) {
        mParameters = mCameraDevice.getParameters();

        if ((updateSet & UPDATE_PARAM_INITIALIZE) != 0) {
            updateCameraParametersInitialize();
        }

        if ((updateSet & UPDATE_PARAM_ZOOM) != 0) {
            updateCameraParametersZoom();
        }

        if ((updateSet & UPDATE_PARAM_PREFERENCE) != 0) {
            updateCameraParametersPreference();
        }

    	Parameters oldParameters = mCameraDevice.getParameters();
        try {
        	mCameraDevice.setParameters(mParameters);
        } catch (IllegalArgumentException e) {
        	mCameraDevice.setParameters(oldParameters);
        	mParameters = oldParameters;
        	mZoomValue = 0;
        	Log.w(TAG, e);
        }
        initControlButtons();
    }

    // If the Camera is idle, update the parameters immediately, otherwise
    // accumulate them in mUpdateSet and update later.
    private void setCameraParametersWhenIdle(int additionalUpdateSet) {
        mUpdateSet |= additionalUpdateSet;
        if (mCameraDevice == null) {
            // We will update all the parameters when we open the device, so
            // we don't need to do anything now.
            mUpdateSet = 0;
            return;
        } else if (isCameraIdle()) {
            setCameraParameters(mUpdateSet);
            mUpdateSet = 0;
        } else {
            if (!mHandler.hasMessages(SET_CAMERA_PARAMETERS_WHEN_IDLE)) {
                mHandler.sendEmptyMessageDelayed(
                        SET_CAMERA_PARAMETERS_WHEN_IDLE, 1000);
            }
        }
    }

    private void gotoGallery() {
        MenuHelper.gotoCameraImageGallery(this);
    }

    private void viewLastImage() {
        if (mThumbController.isUriValid()) {
            Intent intent = new Intent(Util.REVIEW_ACTION, mThumbController.getUri());
            try {
                startActivity(intent);
            } catch (ActivityNotFoundException ex) {
                try {
                    intent = new Intent(Intent.ACTION_VIEW, mThumbController.getUri());
                    startActivity(intent);
                } catch (ActivityNotFoundException e) {
                    Log.e(TAG, "review image fail", e);
                }
            }
        } else {
            Log.e(TAG, "Can't view last image.");
        }
    }

    private boolean isCameraIdle() {
        return mStatus == IDLE && mFocusState == FOCUS_NOT_STARTED;
    }

    private boolean isImageCaptureIntent() {
        String action = getIntent().getAction();
        return MediaStore.ACTION_IMAGE_CAPTURE.equals(action) ;
    }
    
    private boolean isLightboxIntent() {
    	Log.d(TAG, "isLightboxIntent: "+getIntent().getBooleanExtra("com.lightbox.android.photos.activities.TakePhotoActivity", false));
    	return getIntent().getBooleanExtra("com.lightbox.android.photos.activities.TakePhotoActivity", false);
    }

    private void setupCaptureParams() {
        Bundle myExtras = getIntent().getExtras();
        if (myExtras != null) {
            mSaveUri = (Uri) myExtras.getParcelable(MediaStore.EXTRA_OUTPUT);
            mCropValue = myExtras.getString("crop");
        }
    }

    /*private void showPostCaptureAlert() {
        if (mIsImageCaptureIntent) {
            findViewById(R.id.shutter_button).setVisibility(View.INVISIBLE);
            int[] pickIds = {R.id.btn_retake, R.id.btn_done};
            for (int id : pickIds) {
                View button = findViewById(id);
                ((View) button.getParent()).setVisibility(View.VISIBLE);
            }
        }
    }*/

    /*private void hidePostCaptureAlert() {
        if (mIsImageCaptureIntent) {
            findViewById(R.id.shutter_button).setVisibility(View.VISIBLE);
            int[] pickIds = {R.id.btn_retake, R.id.btn_done};
            for (int id : pickIds) {
                View button = findViewById(id);
                ((View) button.getParent()).setVisibility(View.GONE);
            }
        }
    }*/

    private int calculatePicturesRemaining() {
        mPicturesRemaining = MenuHelper.calculatePicturesRemaining();
        return mPicturesRemaining;
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
    	getMenuInflater().inflate(R.menu.menu, menu);
    	
    	return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        // Only show the menu when camera is idle.
        for (int i = 0; i < menu.size(); i++) {
            menu.getItem(i).setVisible(isCameraIdle());
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
    	switch (item.getItemId()) {
		case R.id.settings:
			startActivity(new Intent(this, com.lightbox.android.camera.activities.Settings.class));
			return true;
    	}
    	
    	return false;
    }
    
    private void switchCameraId(int cameraId) {
        if (mPausing || !isCameraIdle()) return;
        mCameraId = cameraId;
        CameraSettings.writePreferredCameraId(mPreferences, cameraId);

        if (CameraHolder.instance().isFrontFacing(cameraId)) {
        	mCameraTypeButton.setImageResource(R.drawable.btn_camera_front);
        } else {
        	mCameraTypeButton.setImageResource(R.drawable.btn_camera_rear);
        }
        
        stopPreview();
        closeCamera();

        // Remove the messages in the event queue.
        mHandler.removeMessages(RESTART_PREVIEW);

        // Reset variables
        mJpegPictureCallbackTime = 0;
        mZoomValue = 0;

        // Reload the preferences.
        mPreferences.setLocalId(this, mCameraId);
        CameraSettings.upgradeLocalPreferences(mPreferences.getLocal());

        // Restart the preview.
        resetExposureCompensation();
        if (!restartPreview()) return;

        initializeZoom();

        // Reload the UI.
        if (mFirstTimeInitialized) {
            initializeHeadUpDisplay();
        }
    }

    private boolean switchToVideoMode() {
        if (isFinishing() || !isCameraIdle()) return false;
        MenuHelper.gotoVideoMode(this);
        mHandler.removeMessages(FIRST_TIME_INIT);
        finish();
        return true;
    }

    public boolean onSwitchChanged(Switcher source, boolean onOff) {
        if (onOff == SWITCH_VIDEO) {
            return switchToVideoMode();
        } else {
            return true;
        }
    }

    private void onSharedPreferenceChanged() {
        // ignore the events after "onPause()"
        if (mPausing) return;
        
        int cameraId = CameraSettings.readPreferredCameraId(mPreferences);
        if (mCameraId != cameraId) {
            switchCameraId(cameraId);
        } else {
            setCameraParametersWhenIdle(UPDATE_PARAM_PREFERENCE);
        }
    }

    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
        keepScreenOnAwhile();
    }

    private void resetScreenOn() {
        mHandler.removeMessages(CLEAR_SCREEN_DELAY);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void keepScreenOnAwhile() {
        mHandler.removeMessages(CLEAR_SCREEN_DELAY);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        mHandler.sendEmptyMessageDelayed(CLEAR_SCREEN_DELAY, SCREEN_DELAY);
    }

    private class MyHeadUpDisplayListener implements HeadUpDisplay.Listener {

        public void onSharedPreferencesChanged() {
            Camera.this.onSharedPreferenceChanged();
        }

        public void onRestorePreferencesClicked() {
            Camera.this.onRestorePreferencesClicked();
        }

        public void onPopupWindowVisibilityChanged(int visibility) {
        }
    }

    protected void onRestorePreferencesClicked() {
        if (mPausing) return;
        Runnable runnable = new Runnable() {
            public void run() {
                mHeadUpDisplay.restorePreferences(mParameters);
            }
        };
        MenuHelper.confirmAction(this,
                getString(R.string.confirm_restore_title),
                getString(R.string.confirm_restore_message),
                runnable);
    }
}

/*
 * Provide a mapping for Jpeg encoding quality levels
 * from String representation to numeric representation.
 */
class JpegEncodingQualityMappings {
    private static final String TAG = "JpegEncodingQualityMappings";
    
    private static final String NORMAL = "normal";
    private static final String FINE = "fine";
    private static final String SUPERFINE = "superfine";
    
    private static final int DEFAULT_QUALITY = 85;
    private static HashMap<String, Integer> mHashMap =
            new HashMap<String, Integer>();

    static {
        mHashMap.put(NORMAL,    (Build.VERSION.SDK_INT >= 0x00000008) ? CameraProfile.QUALITY_LOW : 0x00000000);
        mHashMap.put(FINE,      (Build.VERSION.SDK_INT >= 0x00000008) ? CameraProfile.QUALITY_MEDIUM : 0x00000001);
        mHashMap.put(SUPERFINE, (Build.VERSION.SDK_INT >= 0x00000008) ? CameraProfile.QUALITY_HIGH : 0x00000002);
    }
    
    private static String[] mQualityStrings = {SUPERFINE, FINE, NORMAL};
    private static int[] mQualityNumbers = {85, 75, 65};

    // Retrieve and return the Jpeg encoding quality number
    // for the given quality level.
    public static int getQualityNumber(String jpegQuality) {
        Integer quality = mHashMap.get(jpegQuality);
        if (quality == null) {
            Log.w(TAG, "Unknown Jpeg quality: " + jpegQuality);
            return DEFAULT_QUALITY;
        }
        if (Build.VERSION.SDK_INT >= 0x00000008) {
        	return CameraProfile.getJpegEncodingQualityParameter(quality.intValue());
        } else {
        	// Find the index of the input string
            int index = Util.indexOf(mQualityStrings, jpegQuality);

            if (index == -1 || index > mQualityNumbers.length - 1) {
                return DEFAULT_QUALITY;
            }

            try {
                return mQualityNumbers[index];
            } catch (NumberFormatException ex) {
                return DEFAULT_QUALITY;
            }
        }
    }
}
