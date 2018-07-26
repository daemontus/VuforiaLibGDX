package com.github.daemontus.ar.libgdx;

import com.badlogic.gdx.Screen;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.github.daemontus.ar.vuforia.VuforiaRenderer;

/**
 * Screen implementation responsible for model loading and calling renderer properly.
 */
public class Display implements Screen {

    public ModelInstance modelInstance;
    public Model model;

    private Renderer mRenderer;

    public Display() {

        mRenderer = new Renderer();

        AssetManager assets = new AssetManager();
        assets.load("jet.g3db", Model.class);
        assets.finishLoading();

        model = assets.get("jet.g3db", Model.class);

        modelInstance = new ModelInstance(model);

    }

    @Override
    public void render(float delta) {
        mRenderer.render(this, delta);
    }

    @Override
    public void dispose() {
        mRenderer.dispose();
    }


    @Override
    public void resize(int i, int i2) {

    }

    @Override
    public void show() {

    }

    @Override
    public void hide() {

    }

    @Override
    public void pause() {

    }

    @Override
    public void resume() {

    }

    public void setVuforiaRenderer(VuforiaRenderer renderer) {
        mRenderer.setVuforiaRenderer(renderer);
    }
}
