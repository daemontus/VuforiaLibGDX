package com.github.daemontus.ar.libgdx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.GLCommon;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.qualcomm.vuforia.Matrix44F;
import com.qualcomm.vuforia.Tool;
import com.qualcomm.vuforia.TrackableResult;
import com.qualcomm.vuforia.VIDEO_BACKGROUND_REFLECTION;

import com.github.daemontus.ar.vuforia.SampleMath;
import com.github.daemontus.ar.vuforia.VuforiaRenderer;

/**
 * Class responsible for rendering and scene transformations.
 */
public class Renderer {

    private static final String LOG = "RENDERER";

    private static final float MODEL_SCALE = 0.2f;

    private PerspectiveCamera camera;
    private Environment lights;
    private ModelBatch modelBatch;
    private VuforiaRenderer vuforiaRenderer;

    public Renderer(VuforiaRenderer arRenderer) {

        lights = new Environment();
        lights.set(new ColorAttribute(ColorAttribute.AmbientLight, Color.WHITE));

        camera = new PerspectiveCamera(60, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        camera.near = 1.0F;
        camera.far = 1000.0F;
        //set camera into "Vuforia - style" direction
        camera.position.set(new Vector3(0,0,0));
        camera.lookAt(new Vector3(0,0,1));

        this.vuforiaRenderer = arRenderer;

        modelBatch = new ModelBatch();
    }

    public void render(Display display, float delta) {

        GLCommon gl = Gdx.gl;

        gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);

        TrackableResult[] results = null;

        if (vuforiaRenderer.mIsActive) {
            //render camera background and find targets
            results = vuforiaRenderer.processFrame();
        }

        gl.glEnable(GL20.GL_DEPTH_TEST);
        gl.glEnable(GL20.GL_CULL_FACE);


        setProjectionAndCamera(display, results, (float) Math.toDegrees(vuforiaRenderer.fieldOfViewRadians));
        modelBatch.begin(camera);

        gl.glDepthMask(true);
        modelBatch.render(display.modelInstance, lights);

        modelBatch.end();

        gl.glDisable(GL20.GL_CULL_FACE);
        gl.glDisable(GL20.GL_DEPTH_TEST);
        gl.glDisable(GL20.GL_BLEND);
    }

    private void setProjectionAndCamera(Display contentProvider, TrackableResult[] trackables, float filedOfView) {

        ModelInstance model = contentProvider.modelInstance;

        if (trackables != null && trackables.length > 0) {
            //transform all content
            TrackableResult trackable = trackables[0];

            Matrix44F modelViewMatrix = Tool.convertPose2GLMatrix(trackable.getPose());
            float[] raw = modelViewMatrix.getData();

            float[] rotated;
            //switch axis and rotate to compensate coordinates change
            if (com.qualcomm.vuforia.Renderer.getInstance().getVideoBackgroundConfig().getReflection() == VIDEO_BACKGROUND_REFLECTION.VIDEO_BACKGROUND_REFLECTION_ON) {
                // Front camera
                rotated = new float[]{
                        raw[1], raw[0], raw[2], raw[3],
                        raw[5], raw[4], raw[6], raw[7],
                        raw[9], raw[8], raw[10], raw[11],
                        raw[13], raw[12], raw[14], raw[15]
                };
            } else {
                // Back camera
                rotated = new float[]{
                        raw[1], -raw[0], raw[2], raw[3],
                        raw[5], -raw[4], raw[6], raw[7],
                        raw[9], -raw[8], raw[10], raw[11],
                        raw[13], -raw[12], raw[14], raw[15]
                };
            }
            Matrix44F rot = new Matrix44F();
            rot.setData(rotated);
            Matrix44F inverse = SampleMath.Matrix44FInverse(rot);
            Matrix44F transp = SampleMath.Matrix44FTranspose(inverse);

            float[] data = transp.getData();
            camera.position.set(data[12], data[13], data[14]);
            camera.up.set(data[4], data[5], data[6]);
            camera.direction.set(data[8], data[9], data[10]);
            //update filed of view
            camera.fieldOfView = filedOfView;

        } else {
            camera.position.set(100, 100, 100);
            camera.lookAt(1000,1000,1000);
        }

        model.transform.set(new Matrix4());
        //the model is rotated
        model.transform.setToRotation(1.0F, 0.0F, 0.0F, 90.0F);
        model.transform.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);

        camera.update();
    }

    public void dispose() {
        modelBatch.dispose();
    }

}
