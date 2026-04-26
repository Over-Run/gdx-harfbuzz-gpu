package io.github.overrun.gdxhbgpu.demo;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import io.github.overrun.gdxhbgpu.HBBlob;
import io.github.overrun.gdxhbgpu.HBBlobLoader;
import io.github.overrun.gdxhbgpu.HBFont;
import io.github.overrun.gdxhbgpu.HBTextRenderer;

public class Main extends ApplicationAdapter {
    private AssetManager assetManager;
    private HBBlob hbBlob;
    private HBFont hbFont;
    private SpriteBatch spriteBatch;
    private HBTextRenderer textRenderer;
    private final ScreenViewport viewport = new ScreenViewport();
    private float scale = 1;

    @Override
    public void create() {
        Gdx.graphics.setForegroundFPS(0);
        Gdx.input.setInputProcessor(new InputAdapter() {
            @Override
            public boolean scrolled(float amountX, float amountY) {
                scale -= amountY * 0.1f;
                return true;
            }
        });

        assetManager = new AssetManager();
        HBBlobLoader.register(assetManager);
        assetManager.load("font/font", HBBlob.class);
        assetManager.finishLoading();

        hbBlob = assetManager.get("font/font");
        hbFont = new HBFont(hbBlob, 0);

        spriteBatch = new SpriteBatch();
        textRenderer = new HBTextRenderer(hbFont);
    }

    @Override
    public void render() {
        ScreenUtils.clear(Color.CLEAR);

        viewport.apply();
        spriteBatch.setProjectionMatrix(viewport.getCamera().combined);
        spriteBatch.begin();
        textRenderer.drawText(spriteBatch, "你好世界！The quick brown fox jumps over the lazy dog.", 0, 32, 32 * scale);
        textRenderer.drawText(spriteBatch, "FPS: " + Gdx.graphics.getFramesPerSecond(), 0, Gdx.graphics.getHeight() - 32, 32);
        spriteBatch.end();
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, true);
    }

    @Override
    public void dispose() {
        hbFont.dispose();
        assetManager.dispose();
        spriteBatch.dispose();
        textRenderer.dispose();
    }
}
