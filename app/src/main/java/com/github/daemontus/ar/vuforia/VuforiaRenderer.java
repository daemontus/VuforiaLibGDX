package com.github.daemontus.ar.vuforia;

import android.opengl.GLES20;
import android.util.Log;

import com.github.daemontus.renderer.ArActivity;
import com.vuforia.CameraCalibration;
import com.vuforia.CameraDevice;
import com.vuforia.Device;
import com.vuforia.State;
import com.vuforia.TrackableResult;
import com.vuforia.Vec2F;
import com.vuforia.Vuforia;

/**
 * Vuforia renderer, responsible for video background rendering, tracking and position calculations
 */
public class VuforiaRenderer implements AppRenderer.RendererControl {

    private static final String LOGTAG = "VuforiaRenderer";

    private final AppSession vuforiaAppSession;
    private final AppRenderer mSampleAppRenderer;

    private boolean mIsActive = false;
    private float fieldOfViewRadians = 0.0f;
    private String lastTrackableName = null;

    public VuforiaRenderer(ArActivity activity, AppSession session)
    {
        vuforiaAppSession = session;
        // SampleAppRenderer used to encapsulate the use of RenderingPrimitives setting
        // the device mode AR/VR and stereo mode
        mSampleAppRenderer = new AppRenderer(this, activity, Device.MODE.MODE_AR,
                false, 0.01f , 5f);
    }

    public TrackableResult[] onDrawFrame()
    {
        if (!mIsActive)
            return null;

        // Call our function to render content from SampleAppRenderer class
        return mSampleAppRenderer.render();
    }

    public void onSurfaceCreated()
    {
        Log.d(LOGTAG, "GLRenderer.onSurfaceCreated");

        // Call Vuforia function to (re)initialize rendering after first use
        // or after OpenGL ES context was lost (e.g. after onPause/onResume):
        vuforiaAppSession.onSurfaceCreated();

        mSampleAppRenderer.onSurfaceCreated();
    }

    public void onSurfaceChanged(int width, int height) {
        Log.d(LOGTAG, "GLRenderer.onSurfaceChanged");

        // Call Vuforia function to handle render surface size changes:
        vuforiaAppSession.onSurfaceChanged(width, height);

        // RenderingPrimitives to be updated when some rendering change is done
        mSampleAppRenderer.onConfigurationChanged(mIsActive);

        GLES20.glClearColor(0.0f, 0.0f, 0.0f, Vuforia.requiresAlpha() ? 0.0f : 1.0f);
    }

    public void updateConfiguration() {
        mSampleAppRenderer.onConfigurationChanged(mIsActive);
    }

    @Override
    public TrackableResult[] renderFrame(State state, float[] projectionMatrix) {
        if (!mIsActive)
            return null;

        mSampleAppRenderer.renderVideoBackground(state);

        // did we find any trackables this frame?
        TrackableResult[] results = new TrackableResult[state.getNumTrackableResults()];
        for (int tIdx = 0; tIdx < state.getNumTrackableResults(); tIdx++)
        {
            //remember trackable
            TrackableResult result = state.getTrackableResult(tIdx);
            lastTrackableName = result.getTrackable().getName();
            results[tIdx] = result;

            //calculate filed of view
            CameraCalibration calibration = CameraDevice.getInstance().getCameraCalibration();
            Vec2F size = calibration.getSize();
            Vec2F focalLength = calibration.getFocalLength();
            fieldOfViewRadians = (float) (2 * Math.atan(0.5f * size.getData()[0] / focalLength.getData()[0]));
        }

        return results;
    }

    public boolean isActive() {
        return mIsActive;
    }

    public void setActive(boolean active)
    {
        mIsActive = active;

        if(mIsActive)
            mSampleAppRenderer.configureVideoBackground();
    }

    public float getFieldOfViewRadians() {
        return fieldOfViewRadians;
    }

    public String getLastTrackableName() {
        return lastTrackableName;
    }
}
