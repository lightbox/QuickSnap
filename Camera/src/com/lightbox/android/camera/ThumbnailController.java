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

package com.lightbox.android.camera;


import android.content.ContentResolver;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.view.ViewGroup.LayoutParams;
import android.widget.ImageView;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import com.lightbox.android.camera.MenuHelper;

/**
 * A controller shows thumbnail picture on a button. The thumbnail picture
 * corresponds to a URI of the original picture/video. The thumbnail bitmap
 * and the URI can be saved to a file (and later loaded from it).
 */
public class ThumbnailController {

    private static final String TAG = "ThumbnailController";
    private final ContentResolver mContentResolver;
    private Uri mUri;
    private Bitmap mThumb;
    private final ImageView mButton;
    private Drawable[] mThumbs;
    private TransitionDrawable mThumbTransition;
    private boolean mShouldAnimateThumb;
    private final Resources mResources;

    // The "frame" is a drawable we want to put on top of the thumbnail.
    public ThumbnailController(Resources resources,
            ImageView button, ContentResolver contentResolver) {
        mResources = resources;
        mButton = button;
        mContentResolver = contentResolver;
    }

    public void setData(Uri uri, Bitmap original) {
        // Make sure uri and original are consistently both null or both
        // non-null.
        if (uri == null || original == null) {
            uri = null;
            original = null;
        }
        mUri = uri;
        updateThumb(original);
    }

    public void setUri(Uri uri) {
    	mUri = uri;
    }
    
    public Uri getUri() {
        return mUri;
    }

    private static final int BUFSIZE = 4096;

    // Stores the data from the specified file.
    // Returns true for success.
    public boolean storeData(String filePath) {
        if (mUri == null) {
            return false;
        }

        FileOutputStream f = null;
        BufferedOutputStream b = null;
        DataOutputStream d = null;
        try {
            f = new FileOutputStream(filePath);
            b = new BufferedOutputStream(f, BUFSIZE);
            d = new DataOutputStream(b);
            d.writeUTF(mUri.toString());
            mThumb.compress(Bitmap.CompressFormat.PNG, 100, d);
            d.close();
        } catch (IOException e) {
            return false;
        } finally {
            MenuHelper.closeSilently(f);
            MenuHelper.closeSilently(b);
            MenuHelper.closeSilently(d);
        }
        return true;
    }

    // Loads the data from the specified file.
    // Returns true for success.
    public boolean loadData(String filePath) {
        FileInputStream f = null;
        BufferedInputStream b = null;
        DataInputStream d = null;
        try {
            f = new FileInputStream(filePath);
            b = new BufferedInputStream(f, BUFSIZE);
            d = new DataInputStream(b);
            Uri uri = Uri.parse(d.readUTF());
            Bitmap thumb = BitmapFactory.decodeStream(d);
            setData(uri, thumb);
            d.close();
        } catch (IOException e) {
            return false;
        } finally {
            MenuHelper.closeSilently(f);
            MenuHelper.closeSilently(b);
            MenuHelper.closeSilently(d);
        }
        return true;
    }

    public void updateDisplayIfNeeded(int duration) {
        if (mUri == null) {
            mButton.setImageDrawable(null);
            return;
        }

        if (mShouldAnimateThumb) {
        	//mThumbTransition.setLevel(2);
            mThumbTransition.startTransition(duration);
            mShouldAnimateThumb = false;
        }
    }

    public void updateThumb(Bitmap original) {
    	updateThumb(original, 0, true);
    }
    
    public void updateThumb(Bitmap original, int degrees, boolean useTransition) {
        if (original == null) {
            mThumb = null;
            mThumbs = null;
            return;
        }

        LayoutParams param = mButton.getLayoutParams();
        final int miniThumbWidth = param.width
                - mButton.getPaddingLeft() - mButton.getPaddingRight();
        final int miniThumbHeight = param.height
                - mButton.getPaddingTop() - mButton.getPaddingBottom();
        mThumb = extractThumbnail(
                original, miniThumbWidth, miniThumbHeight, degrees);
        Drawable drawable;
        if (mThumbs == null) {
            mThumbs = new Drawable[2];
            mThumbs[1] = new BitmapDrawable(mResources, mThumb);
            drawable = mThumbs[1];
            mShouldAnimateThumb = false;
        } else {
            mThumbs[0] = mThumbs[1];
            mThumbs[1] = new BitmapDrawable(mResources, mThumb);
            mThumbTransition = new TransitionDrawable(mThumbs);
            drawable = mThumbTransition;
            mShouldAnimateThumb = true;
        }
        if (useTransition) {
        	mButton.setImageDrawable(drawable);
        } else {
        	mButton.setImageBitmap(mThumb);
        }
    }

    private static final int OPTIONS_NONE = 0x0;
    private static final int OPTIONS_SCALE_UP = 0x1;
    public static Bitmap extractThumbnail(
            Bitmap source, int width, int height, int degrees) {
        return extractThumbnail(source, width, height, OPTIONS_NONE, degrees);
    }

    public static Bitmap extractThumbnail(
            Bitmap source, int width, int height, int options, int degrees) {
        if (source == null) {
            return null;
        }

        float scale;
        if (source.getWidth() < source.getHeight()) {
            scale = width / (float) source.getWidth();
        } else {
            scale = height / (float) source.getHeight();
        }
        
        Matrix matrix = new Matrix();
        matrix.setScale(scale, scale);
        
        Bitmap thumbnail = transform(matrix, source, width, height,
                OPTIONS_SCALE_UP | options);
        thumbnail = Util.rotate(thumbnail, degrees);
        return thumbnail;
    }
    
    /**
     * Transform source Bitmap to targeted width and height.
     */
    public static final int OPTIONS_RECYCLE_INPUT = 0x2;
    private static Bitmap transform(Matrix scaler,
            Bitmap source,
            int targetWidth,
            int targetHeight,
            int options) {
        boolean scaleUp = (options & OPTIONS_SCALE_UP) != 0;
        boolean recycle = (options & OPTIONS_RECYCLE_INPUT) != 0;

        int deltaX = source.getWidth() - targetWidth;
        int deltaY = source.getHeight() - targetHeight;
        if (!scaleUp && (deltaX < 0 || deltaY < 0)) {
            /*
            * In this case the bitmap is smaller, at least in one dimension,
            * than the target.  Transform it by placing as much of the image
            * as possible into the target and leaving the top/bottom or
            * left/right (or both) black.
            */
            Bitmap b2 = Bitmap.createBitmap(targetWidth, targetHeight,
            Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b2);

            int deltaXHalf = Math.max(0, deltaX / 2);
            int deltaYHalf = Math.max(0, deltaY / 2);
            Rect src = new Rect(
            deltaXHalf,
            deltaYHalf,
            deltaXHalf + Math.min(targetWidth, source.getWidth()),
            deltaYHalf + Math.min(targetHeight, source.getHeight()));
            int dstX = (targetWidth  - src.width())  / 2;
            int dstY = (targetHeight - src.height()) / 2;
            Rect dst = new Rect(
                    dstX,
                    dstY,
                    targetWidth - dstX,
                    targetHeight - dstY);
            c.drawBitmap(source, src, dst, null);
            if (recycle) {
                source.recycle();
            }
            return b2;
        }
        float bitmapWidthF = source.getWidth();
        float bitmapHeightF = source.getHeight();

        float bitmapAspect = bitmapWidthF / bitmapHeightF;
        float viewAspect   = (float) targetWidth / targetHeight;

        if (bitmapAspect > viewAspect) {
            float scale = targetHeight / bitmapHeightF;
            if (scale < .9F || scale > 1F) {
                scaler.setScale(scale, scale);
            } else {
                scaler = null;
            }
        } else {
            float scale = targetWidth / bitmapWidthF;
            if (scale < .9F || scale > 1F) {
                scaler.setScale(scale, scale);
            } else {
                scaler = null;
            }
        }

        Bitmap b1;
        if (scaler != null) {
            // this is used for minithumb and crop, so we want to filter here.
            b1 = Bitmap.createBitmap(source, 0, 0,
            source.getWidth(), source.getHeight(), scaler, true);
        } else {
            b1 = source;
        }

        if (recycle && b1 != source) {
            source.recycle();
        }

        int dx1 = Math.max(0, b1.getWidth() - targetWidth);
        int dy1 = Math.max(0, b1.getHeight() - targetHeight);

        Bitmap b2 = Bitmap.createBitmap(
                b1,
                dx1 / 2,
                dy1 / 2,
                targetWidth,
                targetHeight);

        if (b2 != b1) {
            if (recycle || b1 != source) {
                b1.recycle();
            }
        }

        return b2;
    }
    
    public boolean isUriValid() {
        if (mUri == null) {
            return false;
        }
        try {
            ParcelFileDescriptor pfd =
                    mContentResolver.openFileDescriptor(mUri, "r");
            if (pfd == null) {
                Log.e(TAG, "Fail to open URI.");
                return false;
            }
            pfd.close();
        } catch (IOException ex) {
            return false;
        }
        return true;
    }
}
