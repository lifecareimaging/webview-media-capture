package com.lifecare.cordova.mediacapture;

import android.annotation.SuppressLint;
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
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.v4.app.FragmentActivity;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Gravity;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Semaphore;

import android.support.v4.app.ActivityCompat;
import android.content.pm.PackageManager;
import android.Manifest;
import android.widget.RelativeLayout;

import static android.media.MediaRecorder.OutputFormat.MPEG_4;
import static android.media.MediaRecorder.VideoEncoder.H264;

import android.content.Intent;
import java.util.Timer;
import java.util.TimerTask;
import android.view.MotionEvent;

import android.os.Build;
import android.content.pm.ActivityInfo;



public class MainActivity extends FragmentActivity {

    private Size previewsize;
    private Size jpegSizes[] = null;

    private AutoFitTextureView textureView;
    private CameraDevice cameraDevice;
    private CaptureRequest.Builder previewBuilder;
    private CameraCaptureSession previewSession;
    Button getpicture;
    Button stopvideo;
    private final int CAMERA_PERMISSIONS = 10001;
    private MediaRecorder recorder;
    private Surface mainsurface;
    private static final String VIDEO_PATH_NAME = "/Videos/sample.mp4";
    private static final String VIDEO_DIRECTORY_NAME = "SampleVideos";
    private static final String VIDEO_MAX_LENGTH  = "VIDEO_MAX_LENGTH";


    private boolean isRecordingVideo = false;

    private HandlerThread backgroundThread;
    private Handler backgroundHandler;

    private Integer sensorOrientation;
    private Size videoSize;
    private String lastRecordedFileUrl;
    private Timer myTimer;
    private Button recordVideoButton;
    private Button cancelButton;
    private int secondsElapsed=0;
    private int videoMaxLengthInSeconds;
    ViewSizeCalculator viewSizeCalculator;


    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

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

        RelativeLayout relLayout = new RelativeLayout(this);
        relLayout.setBackgroundColor(Color.BLACK);
        RelativeLayout.LayoutParams relLayoutParam = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT);
        relLayoutParam.addRule(RelativeLayout.CENTER_IN_PARENT, RelativeLayout.TRUE);
        textureView = new AutoFitTextureView(this);
        textureView.setLayoutParams(relLayoutParam);
        relLayout.addView(textureView);

        recordVideoButton = new Button(this);

        Intent intent = getIntent();
        videoMaxLengthInSeconds = intent.getIntExtra("VIDEO_MAX_LENGTH", 60);


        cancelButton = new Button(this);
        cancelButton.setText("Cancel");
        RelativeLayout.LayoutParams cancelButtonLayoutParams = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
        cancelButtonLayoutParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        cancelButtonLayoutParams.addRule((RelativeLayout.ALIGN_PARENT_LEFT));
        cancelButton.setLayoutParams(cancelButtonLayoutParams);

        recordVideoButton = new Button(this);
        recordVideoButton.setText("Record");
        RelativeLayout.LayoutParams recordButtonLayoutParams = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
        recordButtonLayoutParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        recordButtonLayoutParams.addRule((RelativeLayout.ALIGN_PARENT_RIGHT));

        recordButtonLayoutParams.addRule(RelativeLayout.RIGHT_OF, cancelButton.getId());

        recordVideoButton.setLayoutParams(recordButtonLayoutParams);


        Button stopRecordingButton = new Button(this);

        stopRecordingButton.setText("Stop recording");
        stopRecordingButton.setLayoutParams(new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.WRAP_CONTENT));

        relLayout.addView(recordVideoButton);
        relLayout.addView(cancelButton);
        relLayout.addView(stopRecordingButton);

        relLayout.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View v, MotionEvent event) {
                // ... Respond to touch events
                hideSystemViews();
                return true;
            }
        });


        setContentView(relLayout);
        textureView.setSurfaceTextureListener(surfaceTextureListener);

        recordVideoButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startRecordingVideo();
            }
        });

        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cancelActivity();
            }
        });



        stopRecordingButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopRecordingVideo();
            }
        });
    }

    void prepareMediaRecorder() {

        if(recorder==null)
            recorder=new MediaRecorder();
        recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        recorder.setOutputFormat(MPEG_4);

        try{
                        
            Intent intent = getIntent();
            lastRecordedFileUrl = intent.getStringExtra("NEXT_VIDEO_URL");  //createCaptureFile().getAbsolutePath();
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

        } catch (IOException e) {
            e.printStackTrace();

        }
    }

   
    private void startBackgroundThread() {
        backgroundThread = new HandlerThread("CameraBackground");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    public void cancelActivity() {

        if (isRecordingVideo) {
            File file = new File(lastRecordedFileUrl);
            file.delete();
        }
        finish();
    }
    public void startRecordingVideo() {
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
            /**
             * Surface for the camera preview set up
             */
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
                        // Start recording
                        recorder.start();
                        lockDeviceRotation(true);


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
        secondsElapsed++;
        MainActivity.this.runOnUiThread(Timer_Tick);
    }


    private Runnable Timer_Tick = new Runnable() {
        public void run() {
            recordVideoButton.setText(secondsToString(secondsElapsed)  +"/" +secondsToString(videoMaxLengthInSeconds));
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



    public void stopRecordingVideo() {
        // UI
        isRecordingVideo = false;
        try {
            previewSession.stopRepeating();
            previewSession.abortCaptures();
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        // Stop recording
        recorder.stop();
        recorder.reset();

        Intent intent = new Intent();
        intent.putExtra("video_url", lastRecordedFileUrl);
        setResult(RESULT_OK, intent);
        finish();
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
        }
    }

    private void setUpCaptureRequestBuilder(CaptureRequest.Builder builder) {
        builder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
    }

    public void openCamera(int width, int height) {
        try {
           
            try {
                CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
                String[] cameraIds = manager.getCameraIdList();

                String camerId = manager.getCameraIdList()[0];
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(camerId);
                StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                //previewsize = map.getOutputSizes(SurfaceTexture.class)[0];

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
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        } catch (Exception e) {
            Log.d("MyCameraApp", e.getMessage());
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

        closeCamera();
        stopBackgroundThread();
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
        //requestPermission();
    }

    @Override
    public void onBackPressed() {
    // TODO Auto-generated method stub
   // do your stuff  here
    cancelActivity();
    super.onBackPressed();
}

    void  startCamera() {


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

    public void lockDeviceRotation(boolean value) {
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