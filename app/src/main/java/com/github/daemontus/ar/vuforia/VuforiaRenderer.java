package com.github.daemontus.ar.vuforia;

import android.opengl.GLES20;
import android.util.Log;

import com.badlogic.gdx.graphics.GL10;
import com.qualcomm.vuforia.CameraCalibration;
import com.qualcomm.vuforia.CameraDevice;
import com.qualcomm.vuforia.Renderer;
import com.qualcomm.vuforia.State;
import com.qualcomm.vuforia.TrackableResult;
import com.qualcomm.vuforia.Vec2F;
import com.qualcomm.vuforia.Vuforia;

/**
 * Vuforia renderer, responsible for video background rendering, tracking and position calculations
 */
public class VuforiaRenderer {

    private static final String LOGTAG = "VuforiaRenderer";

    public static String lastTrackableName = "";

    private AppSession vuforiaAppSession;

    private Renderer mRenderer;

    public boolean mIsActive = false;

    public float fieldOfViewRadians;


    public VuforiaRenderer(AppSession session)
    {
        vuforiaAppSession = session;
    }


    // Called when the surface changed size.
    public void onSurfaceChanged(GL10 gl, int width, int height)
    {
        Log.d(LOGTAG, "GLRenderer.onSurfaceChanged");

        // Call Vuforia function to handle render surface size changes:
        vuforiaAppSession.onSurfaceChanged(width, height);
    }


    // Function for initializing the renderer.
    public void initRendering()
    {
        Log.d(LOGTAG, "GLRenderer.initRendering");

        mRenderer = Renderer.getInstance();

        GLES20.glClearColor(0.0f, 0.0f, 0.0f, Vuforia.requiresAlpha() ? 0.0f
                : 1.0f);


        // Call Vuforia function to (re)initialize rendering after first use
        // or after OpenGL ES context was lost (e.g. after onPause/onResume):
        vuforiaAppSession.onSurfaceCreated();
    }


    // The render function.
    public TrackableResult[] processFrame()
    {
        if (!mIsActive)
            return null;

        State state = mRenderer.begin();
        mRenderer.drawVideoBackground();


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

        mRenderer.end();

        return results;
    }

}
