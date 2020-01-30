package com.lifecare.cordova.mediacapture;
import android.content.res.Configuration;
import android.util.Log;
import android.util.Size;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class ViewSizeCalculator {

    public Size selectedVideoSize;

    public void chooseVideoSize(Size[] choices) {

        List<Size> validSizes = new ArrayList<>();
        for (Size size : choices) {
            if (1920 == size.getWidth() && 1080 == size.getHeight()) {
                validSizes.add(size);
            }

            if (1280 == size.getWidth() && 720 == size.getHeight()) {
                validSizes.add(size);
            }
            if (720 == size.getWidth() && 480 == size.getHeight()) {
                validSizes.add(size);
            }

        }
        // no supported sizes lets choose something
        if (validSizes.isEmpty()) {
            selectedVideoSize = choices[choices.length - 1];
        }
        selectedVideoSize = Collections.max(validSizes, new CompareSizesByArea());
    }

    public  Size chooseOptimalSize(Size[] choices, int width, int height, int orientation) {
        // Collect the supported resolutions that are at least as big as the preview Surface
        List<Size> bigEnough = new ArrayList<>();
        int w = selectedVideoSize.getWidth();
        int h = selectedVideoSize.getHeight();
        //flip the width and height in portrait mode
        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            int temp = width;
            width = height;
            height = temp;
        }

        for (Size option : choices) {


            if (selectedVideoSize.getWidth() == option.getWidth() && selectedVideoSize.getHeight() == option.getHeight())
            {
                return option; //choose best match with video size we have found.
            }

            // try to find big enough to fill the preview screen
            if (option.getHeight() == option.getWidth() * h / w &&
                    option.getWidth() >= width && option.getHeight() >= height) {
                bigEnough.add(option);
            }
        }
        // Pick the smallest which can fill up the preview surface , assuming we found any
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else {
            Log.e("", "Couldn't find any suitable preview size");
            return choices[0];
        }
    }

    static class CompareSizesByArea implements Comparator<Size> {
        @Override
        public int compare(Size lhs, Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }
    }
}
