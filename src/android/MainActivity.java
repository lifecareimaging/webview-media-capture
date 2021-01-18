package com.lifecare.cordova.mediacapture;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.AudioManager;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Gravity;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Semaphore;

import android.widget.RelativeLayout;

import static android.media.MediaRecorder.OutputFormat.MPEG_4;

import android.content.Intent;
import java.util.Timer;
import java.util.TimerTask;
import android.view.MotionEvent;

import android.os.Build;
import android.content.pm.ActivityInfo;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.support.design.widget.FloatingActionButton;

import android.content.res.ColorStateList;


public class MainActivity extends FragmentActivity {

    private Size previewsize;

    private AutoFitTextureView textureView;
    private CameraDevice cameraDevice;
    private CaptureRequest.Builder previewBuilder;
    private CameraCaptureSession previewSession;
    private final int CAMERA_PERMISSIONS = 10001;
    private MediaRecorder recorder;
    private boolean isRecordingVideo = false;
    private boolean isRecordingPaused= false;
    private HandlerThread backgroundThread;
    private Handler backgroundHandler;
    private Integer sensorOrientation;
    private String lastRecordedFileUrl;
    private Timer myTimer;
    private FloatingActionButton recordVideoButton;
    private FloatingActionButton cancelButton;
    private FloatingActionButton recordAudioButton;
    private FloatingActionButton pauseRecordingButton;
    private FloatingActionButton stopRecordingButton;
    private int secondsElapsed=0;
    private int videoMaxLengthInSeconds;
    private ViewSizeCalculator viewSizeCalculator;
    private TextView recordLengthTextView;
    
    // ids of image resources for buttons
    int ic_mic_black_24dp;
    int ic_mic_off_white_24dp;
    int ic_pause_black_24dp;
    int ic_radio_button_checked_black_24dp;
    int ic_stop_black_24dp;    

    private Boolean isAudioMuted = false;

    private static final int SENSOR_ORIENTATION_INVERSE_DEGREES = 270;
    private static final int SENSOR_ORIENTATION_DEFAULT_DEGREES = 90;
    private static final SparseIntArray INVERSE_ORIENTATIONS = new SparseIntArray();
    private static final SparseIntArray DEFAULT_ORIENTATIONS = new SparseIntArray();
    static {
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_270, 0);
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_180, 90);
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_90, 180);
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_0, 270);
    }
    static {
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_90, 0);
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_0, 90);
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_270, 180);
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_180, 270);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        hideSystemViews();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        viewSizeCalculator = new ViewSizeCalculator();

        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);

        hideSystemViews();

        Intent intent = getIntent();
        videoMaxLengthInSeconds = intent.getIntExtra("VIDEO_MAX_LENGTH", 60);
        CreateLayout();
        
    }

    private void CreateLayout() {

        RelativeLayout relLayout = new RelativeLayout(this);
        relLayout.setBackgroundColor(Color.BLACK);
        RelativeLayout.LayoutParams relLayoutParam = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT);
        relLayoutParam.addRule(RelativeLayout.CENTER_IN_PARENT, RelativeLayout.TRUE);
        textureView = new AutoFitTextureView(this);
        textureView.setLayoutParams(relLayoutParam);
        relLayout.addView(textureView);

        relLayout.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View v, MotionEvent event) {
                // ... Respond to touch events
                hideSystemViews();
                return true;
            }
        });

        LinearLayout controlPanel = new LinearLayout(this);
        RelativeLayout.LayoutParams controlPanelParams = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
        controlPanelParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        controlPanelParams.bottomMargin = 65;
        controlPanel.setOrientation(LinearLayout.VERTICAL);
        controlPanel.setLayoutParams(controlPanelParams);
        controlPanel.setGravity(Gravity.CENTER_HORIZONTAL);

        LinearLayout buttonContainer = new LinearLayout(this);
        LinearLayout.LayoutParams containerParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        buttonContainer.setGravity(Gravity.CENTER);
        buttonContainer.setLayoutParams(containerParams);

        int video_cancel_btn = getResources().getIdentifier("video_cancel_btn","id", getPackageName());
        int video_record_btn = getResources().getIdentifier("video_record_btn","id", getPackageName());
        int video_record_audio_btn = getResources().getIdentifier("video_record_audio_btn","id", getPackageName());
        int video_pause_btn = getResources().getIdentifier("video_pause_btn","id", getPackageName());
        int video_stop_btn = getResources().getIdentifier("video_stop_btn","id", getPackageName());

        ic_mic_black_24dp = getResources().getIdentifier("ic_mic_black_24dp", "drawable", getPackageName());
        ic_mic_off_white_24dp = getResources().getIdentifier("ic_mic_off_white_24dp", "drawable", getPackageName());
        ic_pause_black_24dp = getResources().getIdentifier("ic_pause_black_24dp", "drawable", getPackageName());
        ic_radio_button_checked_black_24dp = getResources().getIdentifier("ic_radio_button_checked_black_24dp", "drawable", getPackageName());
        ic_stop_black_24dp = getResources().getIdentifier("ic_stop_black_24dp", "drawable", getPackageName());

        cancelButton = new FloatingActionButton(this);
        cancelButton.setAlpha(0.66f);
        cancelButton.setSize(1);
        cancelButton.setId(video_cancel_btn);
        cancelButton.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
        cancelButton.setBackgroundTintList(ColorStateList.valueOf(Color.argb(75,30,30,30)));
        RelativeLayout.LayoutParams cancelButtonLayoutParams = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
        cancelButtonLayoutParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
        cancelButtonLayoutParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        cancelButton.setLayoutParams(cancelButtonLayoutParams);
        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cancelActivity();
            }
        });

        recordVideoButton = new FloatingActionButton(this);
        recordVideoButton.setAlpha(0.66f);
        recordVideoButton.setSize(1);
        recordVideoButton.setId(video_record_btn);
        recordVideoButton.setImageResource(ic_radio_button_checked_black_24dp);
        recordVideoButton.setBackgroundTintList(ColorStateList.valueOf(Color.argb(75,30,30,30)));
        RelativeLayout.LayoutParams recordButtonLayoutParams = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
        recordButtonLayoutParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        recordButtonLayoutParams.addRule(RelativeLayout.RIGHT_OF, cancelButton.getId());
        recordButtonLayoutParams.setMarginStart(250);
        recordVideoButton.setLayoutParams(recordButtonLayoutParams);

        recordVideoButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    startRecordingVideo();
                } catch (Exception e) {
                    e.printStackTrace();
                    finish();
                }
            }
        });

        recordAudioButton = new FloatingActionButton(this);
        recordAudioButton.setAlpha(0.66f);
        recordAudioButton.setSize(1);
        recordAudioButton.setId(video_record_audio_btn);
        recordAudioButton.setImageResource(ic_mic_black_24dp);
        recordAudioButton.setBackgroundTintList(ColorStateList.valueOf(Color.argb(75,30,30,30)));
        RelativeLayout.LayoutParams recordAudioButtonLayoutParams = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
        recordAudioButtonLayoutParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        recordAudioButtonLayoutParams.addRule(RelativeLayout.RIGHT_OF, recordVideoButton.getId());
        recordAudioButtonLayoutParams.setMarginStart(250);
        recordAudioButton.setLayoutParams(recordAudioButtonLayoutParams);

        recordAudioButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isAudioMuted = !isAudioMuted;
                if (isAudioMuted) {
                    recordAudioButton.setImageResource(ic_mic_off_white_24dp);
                }
                else{
                    recordAudioButton.setImageResource(ic_mic_black_24dp);
                }
                setMicMuted(isAudioMuted);
            }
        });

        pauseRecordingButton = new FloatingActionButton(this);
        pauseRecordingButton.setAlpha(0.66f);
        pauseRecordingButton.setSize(1);
        pauseRecordingButton.setId(video_pause_btn);
        pauseRecordingButton.setBackgroundTintList(ColorStateList.valueOf(Color.argb(75,30,30,30)));
        RelativeLayout.LayoutParams pauseRecordingButtonParams = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
        pauseRecordingButtonParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        pauseRecordingButtonParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
        pauseRecordingButton.setImageResource(ic_pause_black_24dp);
        pauseRecordingButton.hide();
        //pauseRecordingButtonParams.setMarginStart(300);
        pauseRecordingButton.setLayoutParams(pauseRecordingButtonParams);

        pauseRecordingButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isRecordingPaused){
                    resumeRecordingVideo();
                } else {
                    pauseRecordingVideo();
                }
            }
        });

        stopRecordingButton = new FloatingActionButton(this);
        stopRecordingButton.setAlpha(0.66f);
        stopRecordingButton.setSize(1);
        stopRecordingButton.setId(video_stop_btn);
        stopRecordingButton.setBackgroundTintList(ColorStateList.valueOf(Color.argb(75,30,30,30)));
        RelativeLayout.LayoutParams stopRecordingButtonParams = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
        stopRecordingButtonParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        stopRecordingButtonParams.addRule(RelativeLayout.RIGHT_OF, pauseRecordingButton.getId());
        stopRecordingButton.setImageResource(ic_stop_black_24dp);
        stopRecordingButton.hide();
        stopRecordingButtonParams.setMarginStart(250);
        stopRecordingButton.setLayoutParams(stopRecordingButtonParams);

        stopRecordingButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopRecordingVideo();
            }
        });
        
        buttonContainer.addView(cancelButton);
        buttonContainer.addView(recordVideoButton);
        buttonContainer.addView(pauseRecordingButton);
        buttonContainer.addView(stopRecordingButton);
        buttonContainer.addView(recordAudioButton);


        recordLengthTextView = new TextView(this);
        recordLengthTextView.setTextColor(Color.WHITE);
        recordLengthTextView.setTextSize(20);
        recordLengthTextView.setText("00:00/"+ secondsToString(videoMaxLengthInSeconds));

        RelativeLayout.LayoutParams  textParams= new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
        textParams.bottomMargin = 65;
        recordLengthTextView.setLayoutParams(textParams);
       
        controlPanel.addView(recordLengthTextView);
        controlPanel.addView(buttonContainer);
        relLayout.addView(controlPanel);

        setContentView(relLayout);
        textureView.setSurfaceTextureListener(surfaceTextureListener);
        
    }

    private void setMicMuted(boolean state){
        AudioManager myAudioManager = (AudioManager)getApplicationContext().getSystemService(Context.AUDIO_SERVICE);

        int workingAudioMode = myAudioManager.getMode();

        myAudioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
        myAudioManager.setMicrophoneMute(state);
        myAudioManager.setMode(workingAudioMode);
    }

    private void prepareMediaRecorder() throws Exception {

        if (recorder==null)
            recorder=new MediaRecorder();
        recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        recorder.setOutputFormat(MPEG_4);

        try {

            Intent intent = getIntent();
            lastRecordedFileUrl = intent.getStringExtra("NEXT_VIDEO_URL");  
            recorder.setOutputFile(lastRecordedFileUrl);

            //always default to full hd even thou the videosize might be different
            CamcorderProfile profile =CamcorderProfile.get(CamcorderProfile.QUALITY_1080P);;
            if (viewSizeCalculator.selectedVideoSize.getHeight() ==720) {
                profile = CamcorderProfile.get(CamcorderProfile.QUALITY_720P);
            }
            else if (viewSizeCalculator.selectedVideoSize.getHeight() ==480) {
                profile = CamcorderProfile.get(CamcorderProfile.QUALITY_480P);
            }
            recorder.setVideoFrameRate(profile.videoFrameRate);
            recorder.setVideoSize(profile.videoFrameWidth, profile.videoFrameHeight);
            recorder.setVideoEncodingBitRate(profile.videoBitRate);
            recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setAudioEncodingBitRate(profile.audioBitRate);
            recorder.setAudioSamplingRate(profile.audioSampleRate);
            int rotation = this.getWindowManager().getDefaultDisplay().getRotation();
            switch (sensorOrientation) {
                case SENSOR_ORIENTATION_DEFAULT_DEGREES:
                    recorder.setOrientationHint(DEFAULT_ORIENTATIONS.get(rotation));
                    break;
                case SENSOR_ORIENTATION_INVERSE_DEGREES:
                    recorder.setOrientationHint(INVERSE_ORIENTATIONS.get(rotation));
                    break;
            }

            recorder.prepare();

        } catch (Exception e) {
            e.printStackTrace();
            throw e;    
        }
    }


    private void startBackgroundThread() {
        backgroundThread = new HandlerThread("CameraBackground");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    private void cancelActivity() {

        if (isRecordingVideo) {
            File file = new File(lastRecordedFileUrl);
            file.delete();
        }
        finish();
    }
    private void startRecordingVideo() throws Exception {
        if (isRecordingVideo) {
            return;
        }

        if (null == cameraDevice || !textureView.isAvailable() || null == previewsize) {
            return;
        }
        try {
            closePreviewSession();
            prepareMediaRecorder();
            SurfaceTexture texture = textureView.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(previewsize.getWidth(), previewsize.getHeight());
            previewBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            List<Surface> surfaces = new ArrayList<>();

            Surface previewSurface = new Surface(texture);
            surfaces.add(previewSurface);
            previewBuilder.addTarget(previewSurface);
            //MediaRecorder setup for surface
            Surface recorderSurface = recorder.getSurface();
            surfaces.add(recorderSurface);
            previewBuilder.addTarget(recorderSurface);
            // Start a capture session
            cameraDevice.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured( CameraCaptureSession cameraCaptureSession) {
                    previewSession = cameraCaptureSession;
                    updatePreview();

                    MainActivity.this.runOnUiThread(() -> {
                        isRecordingVideo = true;
                        lockDeviceRotation(true);
                        // Start recording
                        recorder.start();
                        recordVideoButton.hide();
                        cancelButton.hide();
                        stopRecordingButton.show();
                        pauseRecordingButton.show();
                        stopRecordingButton.setAlpha(0.66f);


                        myTimer = new Timer();
                        myTimer.schedule(new TimerTask() {
                            @Override
                            public void run() {
                                TimerMethod();
                            }
                        }, 0, 1000);
                    });

                }
                @Override
                public void onConfigureFailed( CameraCaptureSession cameraCaptureSession) {
                    Log.e("", "onConfigureFailed: Failed");
                }
            }, backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }


    private void closePreviewSession() {
        if (previewSession != null) {
            previewSession.close();
            previewSession = null;
        }
    }


    private void TimerMethod()
    {
        Log.e("videoMaxLengthInSeconds", Integer.toString(videoMaxLengthInSeconds));
        if (!isRecordingVideo || isRecordingPaused) { return; }
        secondsElapsed++;
        if (secondsElapsed >= videoMaxLengthInSeconds) {
            stopRecordingVideo();
        }
        MainActivity.this.runOnUiThread(Timer_Tick);
    }


    private Runnable Timer_Tick = new Runnable() {
        public void run() {
            recordLengthTextView.setText(secondsToString(secondsElapsed)  +"/" +secondsToString(videoMaxLengthInSeconds));
        }
    };


    private String secondsToString(int seconds) {
        return String.format("%02d:%02d", seconds / 60, seconds % 60);
    }


    private void hideSystemViews() {
        View decorView = getWindow().getDecorView();
        // Hide both the navigation bar and the status bar.
        // SYSTEM_UI_FLAG_FULLSCREEN is only available on Android 4.1 and higher, but as
        // a general rule, you should design your app to hide the status bar whenever you
        // hide the navigation bar.
        int uiOptions = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN |  View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE;
        decorView.setSystemUiVisibility(uiOptions);
    }

    private void pauseRecordingVideo(){
        if (isRecordingPaused){
            return;
        }


        try{
            recorder.pause();
            isRecordingPaused = true;
            pauseRecordingButton.setImageResource(ic_radio_button_checked_black_24dp);;
        }
        catch(Exception e){
            Log.e("MyCameraApp", e.getStackTrace().toString());
            e.printStackTrace();
        }
    }

    private void resumeRecordingVideo(){
        if (!isRecordingPaused){
            return;
        }

        try{
            recorder.resume();
            isRecordingPaused = false;
            pauseRecordingButton.setImageResource(ic_pause_black_24dp);;
        }
        catch(Exception e){
            Log.e("MyCameraApp", e.getStackTrace().toString());
            e.printStackTrace();
        }
    }

    private void stopRecordingVideo() {
        // UI
        isRecordingVideo = false;
        myTimer.cancel();
        try {
            previewSession.stopRepeating();
            previewSession.abortCaptures();
        } catch (CameraAccessException e) {
            Log.e("MyCameraApp", e.getStackTrace().toString());
            e.printStackTrace();
        }

        setMicMuted(false);

        try {
            // Stop recording
            recorder.stop();
            recorder.reset();

            Intent intent = new Intent();
            intent.putExtra("video_url", lastRecordedFileUrl);
            setResult(RESULT_OK, intent);
            finish();
        }
        catch (RuntimeException e) {
            Log.e("MyCameraApp", e.getStackTrace().toString());
            e.printStackTrace();
        }
    }

    private void updatePreview() {
        if (null == cameraDevice) {
            return;
        }
        try {
            setUpCaptureRequestBuilder(previewBuilder);
            HandlerThread thread = new HandlerThread("CameraPreview");
            thread.start();
            previewSession.setRepeatingRequest(previewBuilder.build(), null, backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
            Log.e("MyCameraApp", e.getStackTrace().toString());
        }
    }

    private void setUpCaptureRequestBuilder(CaptureRequest.Builder builder) {
        builder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
    }

    private void openCamera(int width, int height) {
       
            try {
                CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
                String[] cameraIds = manager.getCameraIdList();

                String camerId = manager.getCameraIdList()[0];
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(camerId);
                StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

                sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
                if (map == null) {
                    throw new RuntimeException("Cannot get available preview/video sizes");
                }
                viewSizeCalculator.chooseVideoSize(map.getOutputSizes(MediaRecorder.class));
                int orientation = getResources().getConfiguration().orientation;

                previewsize = viewSizeCalculator.chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),
                        width, height, orientation);
                if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    textureView.setAspectRatio(previewsize.getWidth(), previewsize.getHeight());
                } else {
                    textureView.setAspectRatio(previewsize.getHeight(), previewsize.getWidth());
                }
                configureTransform(width, height);
                recorder = new MediaRecorder();

                manager.openCamera(camerId, stateCallback, null);
            } catch (CameraAccessException ce) {
                ce.printStackTrace();
                //to be define later
            }
            catch (Exception e) {
                Log.d("MyCameraApp", e.getMessage());
                throw e;
            }

    }

    private void configureTransform(int viewWidth, int viewHeight) {
        if (null == textureView || null == previewsize ) {
            return;
        }
        int rotation = this.getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, previewsize.getHeight(), previewsize.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) viewHeight / previewsize.getHeight(),
                    (float) viewWidth / previewsize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        }
        textureView.setTransform(matrix);
    }


    private TextureView.SurfaceTextureListener surfaceTextureListener=new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            openCamera(width, height);
            //mainsurface = new Surface(surface);
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
            configureTransform(width, height);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {

        }
    };

    private Semaphore mCameraOpenCloseLock = new Semaphore(1);
    private CameraDevice.StateCallback stateCallback=new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice camera) {
            cameraDevice=camera;
            startCamera();
            mCameraOpenCloseLock.release();
            if (null != textureView) {
                configureTransform(textureView.getWidth(), textureView.getHeight());
            }

        }

        @Override
        public void onDisconnected(CameraDevice camera) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            cameraDevice = null;
            cancelActivity();

        }

        @Override
        public void onError(CameraDevice camera, int error) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            cameraDevice = null;
            cancelActivity();

        }
    };

    private void closeCamera() {
        try {
            mCameraOpenCloseLock.acquire();
            closePreviewSession();
            if (null != cameraDevice) {
                cameraDevice.close();
                cameraDevice = null;
            }
            if (null != recorder) {
                recorder.release();
                recorder = null;
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera closing.");
        } finally {
            mCameraOpenCloseLock.release();
        }
    }


    @Override
    protected void onPause() {
        if (isRecordingVideo) {
            stopRecordingVideo();
        }
        //closeCamera();
        stopBackgroundThread();
        //cancelActivity();
        super.onPause();
    }

    private void stopBackgroundThread() {
        backgroundThread.quitSafely();
        try {
            backgroundThread.join();
            backgroundThread = null;
            backgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }


    @Override
    public void onResume() {
        super.onResume();
        startBackgroundThread();
        hideSystemViews();
    }

    @Override
    public void onBackPressed() {
        cancelActivity();
        super.onBackPressed();
    }

    private void startCamera() {

        if (null == cameraDevice || !textureView.isAvailable() || null == previewsize) {
            return;
        }
        try {
            closePreviewSession();
            SurfaceTexture texture = textureView.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(previewsize.getWidth(), previewsize.getHeight());
            previewBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            Surface previewSurface = new Surface(texture);
            previewBuilder.addTarget(previewSurface);
            cameraDevice.createCaptureSession(Collections.singletonList(previewSurface),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(CameraCaptureSession session) {
                            previewSession = session;
                            updatePreview();
                        }

                        @Override
                        public void onConfigureFailed(CameraCaptureSession session) {
                            Log.e("", "onConfigureFailed: Failed ");
                        }
                    }, backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();

        }
    }

    private void lockDeviceRotation(boolean value) {
        if (value) {
            int currentOrientation = getResources().getConfiguration().orientation;
            if (currentOrientation == Configuration.ORIENTATION_LANDSCAPE) {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
            } else {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
            }
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_USER);
            } else {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);
            }
        }
    }

}