package com.lifecare.cordova.mediacapture;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.apache.cordova.PermissionHelper;
import org.json.JSONArray;
import org.json.JSONException;
import androidx.core.app.ActivityCompat;

import java.io.File;
import android.content.Context;
import android.app.Activity;
import android.util.Log;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import android.os.Environment;


@SuppressWarnings("deprecation")
public class MediaCapture extends CordovaPlugin {

    private static final int CAMERA_PERMISSIONS = 33;
    private CallbackContext callbackContext;
    private boolean cameraClosing;
    private String[] permissions = {Manifest.permission.CAMERA, Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.RECORD_AUDIO, Manifest.permission.MODIFY_AUDIO_SETTINGS};

    private Exception lastException = null;
    private int lengthInSeconds = 60;


    static class MediaCaptureError {
        private static final int UNEXPECTED_ERROR = 0,
                CAMERA_ACCESS_DENIED = 1,
                CAMERA_ACCESS_RESTRICTED = 2;
    }
    private static final int VIDEO_URL = 1001;

    @Override
    public boolean execute(final String action, final JSONArray args, final CallbackContext callbackContext) throws JSONException {
        this.callbackContext = callbackContext;
        Context context = cordova.getActivity().getApplicationContext();
        try {

            if(action.equals("nativeCamera")) {
                try {
                    lengthInSeconds = args.getInt(0) != 0 ? args.getInt(0) : 60;
                    openNewActivity(context);
                } catch(Exception e)
                {
                    lastException = e;
                    callbackContext.error(MediaCaptureError.UNEXPECTED_ERROR);
                }
                return true;
            } else if (action.equals("getLastError")) {
                callbackContext.success(lastException.getMessage());
                return true;
            }
            else {
                return false;
            }
        } catch (Exception e) {
            callbackContext.error(MediaCaptureError.UNEXPECTED_ERROR);
            return false;
        }
    }

    private void openNewActivity(Context context) {

        if (!hasPermission()) {
            requestPermission(33);
            return;
        } else {
            Intent intent = new Intent(context, MainActivity.class);
            intent.putExtra("VIDEO_MAX_LENGTH", lengthInSeconds);
            intent.putExtra("NEXT_VIDEO_URL", createCaptureFile().getAbsolutePath());
            cordova.startActivityForResult((CordovaPlugin) this, intent, VIDEO_URL);
        }
    }

    private File createCaptureFile() {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        return new File(getTempDirectoryPath(),
                "vid_" + timeStamp + ".mp4");
    }

    private String getTempDirectoryPath() {
        File cache = null;

        // SD Card Mounted
        if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            cache = cordova.getActivity().getExternalCacheDir();
        }
        // Use internal storage
        else {
            cache = cordova.getActivity().getCacheDir();
        }

        // Create the cache directory if it doesn't exist
        cache.mkdirs();
        return cache.getAbsolutePath();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {

        if (requestCode == VIDEO_URL) {
            if(resultCode == Activity.RESULT_OK){
                String result=data.getStringExtra("video_url");
                callbackContext.success(result);
            }
            if (resultCode == Activity.RESULT_CANCELED) {
                //Write your code if there's no result
            }
        }
    }//onActivityResult

    private String boolToNumberString(Boolean bool) {
        if(bool)
            return "1";
        else
            return "0";
    }

    public void onRequestPermissionResult(int requestCode, String[] permissions,
                                          int[] grantResults) throws JSONException {
        if (requestCode == CAMERA_PERMISSIONS) {
            // for each permission check if the user granted/denied them
            // you may want to group the rationale in a single dialog,
            // this is just an example
            for (int i = 0; i < permissions.length; i++) {
                String permission = permissions[i];
                if (grantResults[i] == PackageManager.PERMISSION_DENIED) {
                    boolean showRationale = ActivityCompat.shouldShowRequestPermissionRationale(cordova.getActivity(), permission);
                    if (! showRationale) {
                        // user denied flagging NEVER ASK AGAIN
                        callbackContext.error(MediaCaptureError.CAMERA_ACCESS_DENIED);
                        return;
                    } else {
                        callbackContext.error(MediaCaptureError.CAMERA_ACCESS_DENIED);
                        return;
                    }
                }
            }
            if (areAllPermissionsGranted(grantResults)) {

                Intent intent = new Intent(cordova.getActivity().getApplicationContext(), MainActivity.class);
                intent.putExtra("VIDEO_MAX_LENGTH", lengthInSeconds);
                intent.putExtra("NEXT_VIDEO_URL", createCaptureFile().getAbsolutePath());
                cordova.startActivityForResult((CordovaPlugin) this ,intent,VIDEO_URL);
            }
        }
    }

    public static boolean areAllPermissionsGranted(int[] grantResults)
    {
        for(int grantResult : grantResults) if(grantResult != PackageManager.PERMISSION_GRANTED) return false;
        return true;
    }

    public boolean hasPermission() {
        for(String p : permissions)
        {
            if(!PermissionHelper.hasPermission(this, p))
            {
                return false;
            }
        }
        return true;
    }

    private void requestPermission(int requestCode) {
        PermissionHelper.requestPermissions(this, requestCode, permissions);
    }

}
