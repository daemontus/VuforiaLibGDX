package com.github.daemontus.ar.libgdx;

import android.util.Log;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.graphics.FPSLogger;

import com.github.daemontus.ar.vuforia.VuforiaRenderer;

/**
 * Instance of libgdx Game class responsible for rendering 3D content over augmented reality.
 */
public class Engine extends Game {

    private FPSLogger fps;
    private VuforiaRenderer vuforiaRenderer;

    private boolean isCreated = false;
    private int width = 0;
    private int height = 0;
    private Display mDisplay;

    @Override
    public void create () {
        fps = new FPSLogger();
        mDisplay = new Display();
        mDisplay.setVuforiaRenderer(vuforiaRenderer);
        setScreen(mDisplay);
        if (vuforiaRenderer != null) {
            vuforiaRenderer.onSurfaceCreated();
        }
        isCreated = true;
    }

    @Override
    public void resize(int width, int height) {
        super.resize(width, height);
        this.width = width;
        this.height = height;
        Log.d("ENGINE", "Resize: "+width+"x"+height);
        if (vuforiaRenderer != null) vuforiaRenderer.onSurfaceChanged(width, height);
    }

    @Override
    public void render () {
        super.render();
        fps.log();
    }

    public void setVuforiaRenderer(VuforiaRenderer vuforiaRenderer) {
        this.vuforiaRenderer = vuforiaRenderer;
        if (isCreated) {
            mDisplay.setVuforiaRenderer(vuforiaRenderer);
            vuforiaRenderer.onSurfaceCreated();
        }
        if (width != 0 && height != 0) resize(width, height);
    }
}
