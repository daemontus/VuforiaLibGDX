package com.github.daemontus.ar.vuforia;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Point;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;

import com.vuforia.CameraCalibration;
import com.vuforia.CameraDevice;
import com.vuforia.Device;
import com.vuforia.GLTextureUnit;
import com.vuforia.Matrix34F;
import com.vuforia.Mesh;
import com.vuforia.Renderer;
import com.vuforia.RenderingPrimitives;
import com.vuforia.State;
import com.vuforia.Tool;
import com.vuforia.TrackableResult;
import com.vuforia.TrackerManager;
import com.vuforia.VIDEO_BACKGROUND_REFLECTION;
import com.vuforia.VIEW;
import com.vuforia.Vec2F;
import com.vuforia.Vec2I;
import com.vuforia.Vec4I;
import com.vuforia.VideoBackgroundConfig;
import com.vuforia.VideoMode;
import com.vuforia.ViewList;

import java.lang.ref.WeakReference;

public class AppRenderer {

    private static final String LOGTAG = "AppRenderer";

    private RenderingPrimitives mRenderingPrimitives = null;
    private RendererControl mRenderingInterface = null;
    private WeakReference<Activity> mActivityRef = null;

    private int mVideoMode = CameraDevice.MODE.MODE_DEFAULT;

    private Renderer mRenderer = null;
    private int currentView = VIEW.VIEW_SINGULAR;
    private float mNearPlane = -1.0f;
    private float mFarPlane = -1.0f;

    private GLTextureUnit videoBackgroundTex = null;

    // Shader user to render the video background on AR mode
    private int vbShaderProgramID = 0;
    private int vbTexSampler2DHandle = 0;
    private int vbVertexHandle = 0;
    private int vbTexCoordHandle = 0;
    private int vbProjectionMatrixHandle = 0;

    // Display size of the device:
    private int mScreenWidth = 0;
    private int mScreenHeight = 0;

    // Stores orientation
    private boolean mIsPortrait = false;
    private boolean mInitialized = false;

    interface RendererControl {
        // This method has to be implemented by the Renderer class which handles the content rendering
        // of the sample, this one is called from SampleAppRendering class for each view inside a loop
        TrackableResult[] renderFrame(State state, float[] projectionMatrix);
    }

    public AppRenderer(RendererControl renderingInterface, Activity activity, int deviceMode,
                       boolean stereo, float nearPlane, float farPlane)
    {
        this(renderingInterface, activity, deviceMode, CameraDevice.MODE.MODE_DEFAULT, stereo, nearPlane, farPlane);
    }

    public AppRenderer(RendererControl renderingInterface, Activity activity, int deviceMode, int videoMode,
                       boolean stereo, float nearPlane, float farPlane)
    {
        mActivityRef = new WeakReference<>(activity);

        mRenderingInterface = renderingInterface;
        mRenderer = Renderer.getInstance();

        if(farPlane < nearPlane)
        {
            Log.e(LOGTAG, "Far plane should be greater than near plane");
            throw new IllegalArgumentException();
        }

        setNearFarPlanes(nearPlane, farPlane);

        if(deviceMode != Device.MODE.MODE_AR && deviceMode != Device.MODE.MODE_VR)
        {
            Log.e(LOGTAG, "Device mode should be Device.MODE.MODE_AR or Device.MODE.MODE_VR");
            throw new IllegalArgumentException();
        }

        Device device = Device.getInstance();
        device.setViewerActive(stereo); // Indicates if the app will be using a viewer, stereo mode and initializes the rendering primitives
        device.setMode(deviceMode); // Select if we will be in AR or VR mode

        mVideoMode = videoMode;
    }

    public void onSurfaceCreated()
    {
        initRendering();
    }

    public void onConfigurationChanged(boolean isARActive)
    {
        if(mInitialized) { return; }

        updateActivityOrientation();
        storeScreenDimensions();

        if(isARActive)
            configureVideoBackground();

        updateRenderingPrimitives();

        mInitialized = true;
    }


    public synchronized void updateRenderingPrimitives()
    {
        mRenderingPrimitives = Device.getInstance().getRenderingPrimitives();
    }

    private void initRendering()
    {
        vbShaderProgramID = SampleUtils.createProgramFromShaderSrc(BackgroundShader.VB_VERTEX_SHADER,
                BackgroundShader.VB_FRAGMENT_SHADER);

        // Rendering configuration for video background
        if (vbShaderProgramID > 0)
        {
            // Activate shader:
            GLES20.glUseProgram(vbShaderProgramID);

            // Retrieve handler for texture sampler shader uniform variable:
            vbTexSampler2DHandle = GLES20.glGetUniformLocation(vbShaderProgramID, "texSampler2D");

            // Retrieve handler for projection matrix shader uniform variable:
            vbProjectionMatrixHandle = GLES20.glGetUniformLocation(vbShaderProgramID, "projectionMatrix");

            vbVertexHandle = GLES20.glGetAttribLocation(vbShaderProgramID, "vertexPosition");
            vbTexCoordHandle = GLES20.glGetAttribLocation(vbShaderProgramID, "vertexTexCoord");
            vbProjectionMatrixHandle = GLES20.glGetUniformLocation(vbShaderProgramID, "projectionMatrix");
            vbTexSampler2DHandle = GLES20.glGetUniformLocation(vbShaderProgramID, "texSampler2D");

            // Stop using the program
            GLES20.glUseProgram(0);
        }

        videoBackgroundTex = new GLTextureUnit();
    }

    // Main rendering method
    // The method setup state for rendering, setup 3D transformations required for AR augmentation
    // and call any specific rendering method
    public TrackableResult[] render()
    {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        State state;
        // Get our current state
        state = TrackerManager.getInstance().getStateUpdater().updateState();
        mRenderer.begin(state);

        // We must detect if background reflection is active and adjust the
        // culling direction.
        // If the reflection is active, this means the post matrix has been
        // reflected as well,
        // therefore standard counter clockwise face culling will result in
        // "inside out" models.
        if (Renderer.getInstance().getVideoBackgroundConfig().getReflection() == VIDEO_BACKGROUND_REFLECTION.VIDEO_BACKGROUND_REFLECTION_ON)
            GLES20.glFrontFace(GLES20.GL_CW);  // Front camera
        else
            GLES20.glFrontFace(GLES20.GL_CCW);   // Back camera

        // We get a list of views which depend on the mode we are working on, for mono we have
        // only one view, in stereo we have three: left, right and postprocess
        ViewList viewList = mRenderingPrimitives.getRenderingViews();

        TrackableResult[] results = null;
        // Cycle through the view list
        for (int v = 0; v < viewList.getNumViews(); v++)
        {
            // Get the view id
            int viewID = viewList.getView(v);

            Vec4I viewport;
            // Get the viewport for that specific view
            viewport = mRenderingPrimitives.getViewport(viewID);

            // Set viewport for current view
            GLES20.glViewport(viewport.getData()[0], viewport.getData()[1], viewport.getData()[2], viewport.getData()[3]);

            // Set scissor
            GLES20.glScissor(viewport.getData()[0], viewport.getData()[1], viewport.getData()[2], viewport.getData()[3]);

            // Get projection matrix for the current view.
            Matrix34F projMatrix = mRenderingPrimitives.getProjectionMatrix(viewID,
                    state.getCameraCalibration());

            // Create GL matrix setting up the near and far planes
            float rawProjectionMatrixGL[] = Tool.convertPerspectiveProjection2GLMatrix(
                    projMatrix,
                    mNearPlane,
                    mFarPlane)
                    .getData();

            // Apply the appropriate eye adjustment to the raw projection matrix, and assign to the global variable
            float eyeAdjustmentGL[] = Tool.convert2GLMatrix(mRenderingPrimitives
                    .getEyeDisplayAdjustmentMatrix(viewID)).getData();

            float projectionMatrix[] = new float[16];
            // Apply the adjustment to the projection matrix
            Matrix.multiplyMM(projectionMatrix, 0, rawProjectionMatrixGL, 0, eyeAdjustmentGL, 0);

            currentView = viewID;

            // Call renderFrame from the app renderer class which implements SampleAppRendererControl
            // This will be called for MONO, LEFT and RIGHT views, POSTPROCESS will not render the
            // frame
            if(currentView != VIEW.VIEW_POSTPROCESS) {
                results = mRenderingInterface.renderFrame(state, projectionMatrix);
            }
        }

        mRenderer.end();

        return results;
    }

    private void setNearFarPlanes(float near, float far)
    {
        mNearPlane = near;
        mFarPlane = far;
    }

    public void renderVideoBackground(State state)
    {
        if(currentView == VIEW.VIEW_POSTPROCESS)
            return;

        int vbVideoTextureUnit = 0;
        // Bind the video bg texture and get the Texture ID from Vuforia
        videoBackgroundTex.setTextureUnit(vbVideoTextureUnit);
        if (!mRenderer.updateVideoBackgroundTexture(videoBackgroundTex))
        {
            Log.e(LOGTAG, "Unable to update video background texture");
            return;
        }

        float[] vbProjectionMatrix = Tool.convert2GLMatrix(
                mRenderingPrimitives.getVideoBackgroundProjectionMatrix(currentView)).getData();

        // Apply the scene scale on video see-through eyewear, to scale the video background and augmentation
        // so that the display lines up with the real world
        // This should not be applied on optical see-through devices, as there is no video background,
        // and the calibration ensures that the augmentation matches the real world
        if (Device.getInstance().isViewerActive()) {
            float sceneScaleFactor = (float)getSceneScaleFactor(state.getCameraCalibration());
            Matrix.scaleM(vbProjectionMatrix, 0, sceneScaleFactor, sceneScaleFactor, 1.0f);
        }

        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        GLES20.glDisable(GLES20.GL_CULL_FACE);
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST);

        Mesh vbMesh = mRenderingPrimitives.getVideoBackgroundMesh(currentView);
        // Load the shader and upload the vertex/texcoord/index data
        GLES20.glUseProgram(vbShaderProgramID);
        GLES20.glVertexAttribPointer(vbVertexHandle, 3, GLES20.GL_FLOAT, false, 0, vbMesh.getPositions().asFloatBuffer());
        GLES20.glVertexAttribPointer(vbTexCoordHandle, 2, GLES20.GL_FLOAT, false, 0, vbMesh.getUVs().asFloatBuffer());

        GLES20.glUniform1i(vbTexSampler2DHandle, vbVideoTextureUnit);

        // Render the video background with the custom shader
        // First, we enable the vertex arrays
        GLES20.glEnableVertexAttribArray(vbVertexHandle);
        GLES20.glEnableVertexAttribArray(vbTexCoordHandle);

        // Pass the projection matrix to OpenGL
        GLES20.glUniformMatrix4fv(vbProjectionMatrixHandle, 1, false, vbProjectionMatrix, 0);

        // Then, we issue the render call
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, vbMesh.getNumTriangles() * 3, GLES20.GL_UNSIGNED_SHORT,
                vbMesh.getTriangles().asShortBuffer());

        // Finally, we disable the vertex arrays
        GLES20.glDisableVertexAttribArray(vbVertexHandle);
        GLES20.glDisableVertexAttribArray(vbTexCoordHandle);

        SampleUtils.checkGLError("Rendering of the video background failed");
    }


    private static final float VIRTUAL_FOV_Y_DEGS = 85.0f;
    private static final float M_PI = 3.14159f;

    private double getSceneScaleFactor(CameraCalibration cameraCalib)
    {
        if (cameraCalib == null)
        {
            Log.e(LOGTAG, "Cannot compute scene scale factor, camera calibration is invalid");
            return 0.0;
        }

        // Get the y-dimension of the physical camera field of view
        Vec2F fovVector = cameraCalib.getFieldOfViewRads();
        float cameraFovYRads = fovVector.getData()[1];

        // Get the y-dimension of the virtual camera field of view
        float virtualFovYRads = VIRTUAL_FOV_Y_DEGS * M_PI / 180;

        // The scene-scale factor represents the proportion of the viewport that is filled by
        // the video background when projected onto the same plane.
        // In order to calculate this, let 'd' be the distance between the cameras and the plane.
        // The height of the projected image 'h' on this plane can then be calculated:
        //   tan(fov/2) = h/2d
        // which rearranges to:
        //   2d = h/tan(fov/2)
        // Since 'd' is the same for both cameras, we can combine the equations for the two cameras:
        //   hPhysical/tan(fovPhysical/2) = hVirtual/tan(fovVirtual/2)
        // Which rearranges to:
        //   hPhysical/hVirtual = tan(fovPhysical/2)/tan(fovVirtual/2)
        // ... which is the scene-scale factor
        return Math.tan(cameraFovYRads / 2) / Math.tan(virtualFovYRads / 2);
    }

    // Configures the video mode and sets offsets for the camera's image
    public void configureVideoBackground()
    {
        CameraDevice cameraDevice = CameraDevice.getInstance();
        VideoMode vm = cameraDevice.getVideoMode(mVideoMode);

        VideoBackgroundConfig config = new VideoBackgroundConfig();
        config.setPosition(new Vec2I(0, 0));

        int xSize, ySize;
        // We keep the aspect ratio to keep the video correctly rendered. If it is portrait we
        // preserve the height and scale width and vice versa if it is landscape, we preserve
        // the width and we check if the selected values fill the screen, otherwise we invert
        // the selection
        if (mIsPortrait)
        {
            xSize = (int) (vm.getHeight() * (mScreenHeight / (float) vm
                    .getWidth()));
            ySize = mScreenHeight;

            if (xSize < mScreenWidth)
            {
                xSize = mScreenWidth;
                ySize = (int) (mScreenWidth * (vm.getWidth() / (float) vm
                        .getHeight()));
            }
        } else
        {
            xSize = mScreenWidth;
            ySize = (int) (vm.getHeight() * (mScreenWidth / (float) vm
                    .getWidth()));

            if (ySize < mScreenHeight)
            {
                xSize = (int) (mScreenHeight * (vm.getWidth() / (float) vm
                        .getHeight()));
                ySize = mScreenHeight;
            }
        }

        config.setSize(new Vec2I(xSize, ySize));

        Log.i(LOGTAG, "Configure Video Background : Video (" + vm.getWidth()
                + " , " + vm.getHeight() + "), Screen (" + mScreenWidth + " , "
                + mScreenHeight + "), mSize (" + xSize + " , " + ySize + ")");

        Renderer.getInstance().setVideoBackgroundConfig(config);

    }


    // Stores screen dimensions
    private void storeScreenDimensions()
    {
        // Query display dimensions:
        Point size = new Point();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1)
        {
            mActivityRef.get().getWindowManager().getDefaultDisplay().getRealSize(size);
        }
        else
        {
            WindowManager windowManager = (WindowManager) mActivityRef.get().getSystemService(Context.WINDOW_SERVICE);

            if (windowManager != null)
            {
                DisplayMetrics metrics = new DisplayMetrics();
                Display display = windowManager.getDefaultDisplay();
                display.getMetrics(metrics);

                size.x = metrics.widthPixels;
                size.y = metrics.heightPixels;
            }
            else
            {
                Log.e(LOGTAG, "Could not get display metrics!");
                size.x = 0;
                size.y = 0;
            }
        }

        mScreenWidth = size.x;
        mScreenHeight = size.y;
    }


    // Stores the orientation depending on the current resources configuration
    private void updateActivityOrientation()
    {
        Configuration config = mActivityRef.get().getResources().getConfiguration();

        switch (config.orientation)
        {
            case Configuration.ORIENTATION_PORTRAIT:
                mIsPortrait = true;
                break;
            case Configuration.ORIENTATION_LANDSCAPE:
                mIsPortrait = false;
                break;
            case Configuration.ORIENTATION_UNDEFINED:
            default:
                break;
        }

        Log.i(LOGTAG, "Activity is in "
                + (mIsPortrait ? "PORTRAIT" : "LANDSCAPE"));
    }
}
