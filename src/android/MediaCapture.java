package com.lifecare.cordova.mediacapture;

import android.Manifest;
import android.content.Intent;
import android.content.pm.FeatureInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.camera2.CameraAccessException;
import android.net.Uri;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.apache.cordova.PermissionHelper;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import android.hardware.Camera;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import android.content.Context;
import android.content.Intent;
import android.app.Activity;
import java.text.SimpleDateFormat;
import java.util.Date;
import android.os.Environment;


@SuppressWarnings("deprecation")
public class MediaCapture extends CordovaPlugin {

    /**
     *
     */
    private static final int CAMERA_PERMISSIONS = 33;
    private CallbackContext callbackContext;
    private boolean cameraClosing;
    private static Boolean flashAvailable;
    private boolean lightOn = false;
    private boolean showing = false;
    private boolean prepared = false;
    private int currentCameraId = Camera.CameraInfo.CAMERA_FACING_BACK;
    private String[] permissions = {Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.RECORD_AUDIO};
    //Preview started or paused
    private boolean previewing = false;
    //private BarcodeView  mBarcodeView;
   
    private boolean cameraPreviewing;
    private boolean denied;
    private boolean authorized;
    private boolean restricted;
    private boolean oneTime = true;
    private boolean keepDenied = false;
    private boolean appPausedWithActivePreview = false;
    private boolean recording = false;
    private boolean muted= false;
    private boolean paused= false;
    private Exception lastException =null;
    private int lengthInSeconds = 60;

    
    static class MediaCaptureError {
        private static final int UNEXPECTED_ERROR = 0,
                CAMERA_ACCESS_DENIED = 1,
                CAMERA_ACCESS_RESTRICTED = 2,
                BACK_CAMERA_UNAVAILABLE = 3,
                FRONT_CAMERA_UNAVAILABLE = 4,
                CAMERA_UNAVAILABLE = 5,
                LIGHT_UNAVAILABLE = 6,
                OPEN_SETTINGS_UNAVAILABLE = 7;
    }
    private static final int VIDEO_URL = 1001;

    @Override
    public boolean execute(final String action, final JSONArray args, final CallbackContext callbackContext) throws JSONException {
        this.callbackContext = callbackContext;
        Context context = cordova.getActivity().getApplicationContext();
        try {
            
             if(action.equals("nativeCamera")) {

                /*cordova.getThreadPool().execute(new Runnable() {
                    public void run() {
                cordova.getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {*/
                        try {
                            lengthInSeconds = args.getInt(0);
                            openNewActivity(context);
                        } catch(Exception e) 
                        {
                            lastException= e;
                            callbackContext.error(MediaCaptureError.UNEXPECTED_ERROR);
                        }
                //    }});
                //}});
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
            cordova.startActivityForResult((CordovaPlugin) this ,intent,VIDEO_URL);
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

    public int getCurrentCameraId() {
        return this.currentCameraId;
    }
   
    public void onRequestPermissionResult(int requestCode, String[] permissions,
                                          int[] grantResults) throws JSONException {
        oneTime = false;
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
                        //denied = true;
                        //authorized = false;
                        callbackContext.error(MediaCaptureError.CAMERA_ACCESS_DENIED);
                        return;
                    } else {
                        //authorized = false;
                        //denied = false;
                        callbackContext.error(MediaCaptureError.CAMERA_ACCESS_DENIED);
                        return;
                    }
                } else if (grantResults[i] == PackageManager.PERMISSION_GRANTED){
                    //authorized = true;
                    //denied = false;
                   
                }
                else {
                    authorized = false;
                    denied = false;
                    restricted = false;
                }
            }
            if (areAllPermissionsGranted(grantResults)) {
                            
                Intent intent = new Intent(cordova.getActivity().getApplicationContext(), MainActivity.class);
                intent.putExtra("RECORD_LABEL", "Nauhoita");
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

    private boolean hasCamera() {
        if (this.cordova.getActivity().getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)){
            return true;
        } else {
            return false;
        }
    }
    
}
