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

import java.io.IOException;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import android.content.Context;
import android.content.Intent;
import android.app.Activity;


@SuppressWarnings("deprecation")
public class MediaCapture extends CordovaPlugin {

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
            if (action.equals("show")) {
                cordova.getThreadPool().execute(new Runnable() {
                    public void run() {
                        show(callbackContext);
                    }
                });
                return true;
            }
            
            else if(action.equals("pausePreview")) {
                cordova.getThreadPool().execute(new Runnable() {
                    public void run() {
                        pausePreview(callbackContext);
                    }
                });
                return true;
            }
            
            else if(action.equals("resumePreview")) {
                cordova.getThreadPool().execute(new Runnable() {
                    public void run() {
                        resumePreview(callbackContext);
                    }
                });
                return true;
            }
            else if(action.equals("hide")) {
                cordova.getThreadPool().execute(new Runnable() {
                    public void run() {
                        hide(callbackContext);
                    }
                });
                return true;
            }
            
            else if (action.equals("prepare")) {
                cordova.getThreadPool().execute(new Runnable() {
                    public void run() {
                        cordova.getActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    currentCameraId = args.getInt(0);
                                } catch (JSONException e) {
                                }
                                prepare(callbackContext);
                            }
                        });
                    }
                });
                return true;
            }
            else if (action.equals("destroy")) {
                cordova.getThreadPool().execute(new Runnable() {
                    public void run() {
                        destroy(callbackContext);
                    }
                });
                return true;
            }
            else if (action.equals("getStatus")) {
                cordova.getThreadPool().execute(new Runnable() {
                    public void run() {
                        getStatus(callbackContext);
                    }
                });
                return true;
            } else if(action.equals("nativeCamera")) {

                /*cordova.getThreadPool().execute(new Runnable() {
                    public void run() {
                cordova.getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {*/
                        try {
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
            intent.putExtra("RECORD_LABEL", "Nauhoita");
            cordova.startActivityForResult((CordovaPlugin) this ,intent,VIDEO_URL);
        }

        
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

    @Override
    public void onPause(boolean multitasking) {
        if (previewing) {
            this.appPausedWithActivePreview = true;
            this.pausePreview(null);
        }
    }

    @Override
    public void onResume(boolean multitasking) {
        if (this.appPausedWithActivePreview) {
            this.appPausedWithActivePreview = false;
            this.resumePreview(null);
        }
    }

    

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
        if (requestCode == 33) {
            // for each permission check if the user granted/denied them
            // you may want to group the rationale in a single dialog,
            // this is just an example
            for (int i = 0; i < permissions.length; i++) {
                String permission = permissions[i];
                if (grantResults[i] == PackageManager.PERMISSION_DENIED) {
                    boolean showRationale = ActivityCompat.shouldShowRequestPermissionRationale(cordova.getActivity(), permission);
                    if (! showRationale) {
                        // user denied flagging NEVER ASK AGAIN
                        denied = true;
                        authorized = false;
                        callbackContext.error(MediaCaptureError.CAMERA_ACCESS_DENIED);
                        return;
                    } else {
                        authorized = false;
                        denied = false;
                        callbackContext.error(MediaCaptureError.CAMERA_ACCESS_DENIED);
                        return;
                    }
                } else if (grantResults[i] == PackageManager.PERMISSION_GRANTED){
                    authorized = true;
                    denied = false;
                    switch (requestCode) {
                        case 33:
                            Intent intent = new Intent(cordova.getActivity().getApplicationContext(), MainActivity.class);
                            intent.putExtra("RECORD_LABEL", "Nauhoita");
                            cordova.startActivityForResult((CordovaPlugin) this ,intent,VIDEO_URL);
                        break;
                    }
                }
                else {
                    authorized = false;
                    denied = false;
                    restricted = false;
                }
            }
        }
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

    private void closeCamera() {
        cameraClosing = true;
        cordova.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                /*if (mBarcodeView != null) {
                    mBarcodeView.pause();
                }*/

                cameraClosing = false;
            }
        });
    }

    private void makeOpaque() {
        this.cordova.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                webView.getView().setBackgroundColor(Color.TRANSPARENT);
            }
        });
        showing = false;
    }

    private boolean hasCamera() {
        if (this.cordova.getActivity().getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)){
            return true;
        } else {
            return false;
        }
    }

   
    private void setupCamera(CallbackContext callbackContext) {
        cordova.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // Create our Preview view and set it as the content of our activity.
                /*mBarcodeView = new BarcodeView(cordova.getActivity());

                //Configure the camera (front/back)
                CameraSettings settings = new CameraSettings();
                settings.setRequestedCameraId(getCurrentCameraId());
                mBarcodeView.setCameraSettings(settings);

                FrameLayout.LayoutParams cameraPreviewParams = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
                ((ViewGroup) webView.getView().getParent()).addView(mBarcodeView, cameraPreviewParams);

                cameraPreviewing = true;
                webView.getView().bringToFront();

                mBarcodeView.resume();*/
            }
        });
        prepared = true;
        previewing = true;
    }

    // ---- BEGIN EXTERNAL API ----
    private void prepare(final CallbackContext callbackContext) {
        /*if(!prepared) {
            if(currentCameraId == Camera.CameraInfo.CAMERA_FACING_BACK) {
                if (hasCamera()) {
                    if (!hasPermission()) {
                        requestPermission(33);
                    }
                    else {
                        setupCamera(callbackContext);
                        getStatus(callbackContext);
                    }
                }
                else {
                    callbackContext.error(MediaCaptureError.BACK_CAMERA_UNAVAILABLE);
                }
            }
            else if(currentCameraId == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                if (hasFrontCamera()) {
                    if (!hasPermission()) {
                        requestPermission(33);
                    }
                    else {
                        setupCamera(callbackContext);
                        getStatus(callbackContext);
                    }
                }
                else {
                    callbackContext.error(MediaCaptureError.FRONT_CAMERA_UNAVAILABLE);
                }
            }
            else {
                callbackContext.error(MediaCaptureError.CAMERA_UNAVAILABLE);
            }
        }
        else {
            prepared = false;
            this.cordova.getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mBarcodeView.pause();
                }
            });
            if(cameraPreviewing) {
                this.cordova.getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        ((ViewGroup) mBarcodeView.getParent()).removeView(mBarcodeView);
                        cameraPreviewing = false;
                    }
                });

                previewing = true;
                lightOn = false;
            }
            setupCamera(callbackContext);
            getStatus(callbackContext);
        }*/
    }
    
    private void show(final CallbackContext callbackContext) {
        this.cordova.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                webView.getView().setBackgroundColor(Color.argb(1, 0, 0, 0));
                showing = true;
                getStatus(callbackContext);
            }
        });
    }

    private void hide(final CallbackContext callbackContext) {
        makeOpaque();
        getStatus(callbackContext);
    }

    private void pausePreview(final CallbackContext callbackContext) {
        this.cordova.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                /*if(mBarcodeView != null) {
                    mBarcodeView.pause();
                    previewing = false;
                    
                }*/
                
                if (callbackContext != null)
                    getStatus(callbackContext);
            }
        });

    }

    private void resumePreview(final CallbackContext callbackContext) {
        this.cordova.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                /*if(mBarcodeView != null) {
                    mBarcodeView.resume();
                    previewing = true;
                    
                }*/
                
                if (callbackContext != null)
                    getStatus(callbackContext);
            }
        });
    }

    

    

    private void getStatus(CallbackContext callbackContext) {

        /*if(oneTime) {
            boolean authorizationStatus = hasPermission();

            authorized = false;
            if (authorizationStatus)
                authorized = true;

            if(keepDenied && !authorized)
                denied = true;
            else
                denied = false;

            //No applicable API
            restricted = false;
        }
        boolean canOpenSettings = true;

        HashMap status = new HashMap();
        status.put("authorized",boolToNumberString(authorized));
        status.put("denied",boolToNumberString(denied));
        status.put("restricted",boolToNumberString(restricted));
        status.put("prepared",boolToNumberString(prepared));
        status.put("previewing",boolToNumberString(previewing));
        status.put("showing",boolToNumberString(showing));
        status.put("canOpenSettings",boolToNumberString(canOpenSettings));
        status.put("currentCamera",Integer.toString(getCurrentCameraId()));
        status.put("recording",boolToNumberString(recording));
        status.put("muted",boolToNumberString(muted));
        status.put("paused",boolToNumberString(paused));


        JSONObject obj = new JSONObject(status);
        //PluginResult result = new PluginResult(PluginResult.Status.OK, obj);
        callbackContext.success(obj);*/
    }

    private void destroy(CallbackContext callbackContext) {
        /*prepared = false;
        makeOpaque();
        previewing = false;

        if(cameraPreviewing) {
            this.cordova.getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    ((ViewGroup) mBarcodeView.getParent()).removeView(mBarcodeView);
                    cameraPreviewing = false;
                }
            });
        }
       
        closeCamera();
        currentCameraId = 0;
        getStatus(callbackContext);*/
    }
}
