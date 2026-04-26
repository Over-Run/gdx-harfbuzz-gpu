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
                if (scale < 0) scale = 0;
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
        int width = Gdx.graphics.getWidth();
        int height = Gdx.graphics.getHeight();
        ScreenUtils.clear(Color.CLEAR);

        float fontSize = 32 * scale;
        float lineHeight = hbFont.getLineHeight(fontSize);
        float descender = hbFont.unitsToPixels(hbFont.hDescender(), fontSize);

        viewport.apply();
        spriteBatch.setProjectionMatrix(viewport.getCamera().combined);
        spriteBatch.begin();
        textRenderer.drawText(spriteBatch, "FPS: " + Gdx.graphics.getFramesPerSecond(), 0, height - lineHeight, fontSize, Color.GREEN);
        textRenderer.drawText(spriteBatch, "Scale: " + scale, 0, height - 2 * lineHeight, fontSize, Color.YELLOW);
        textRenderer.drawMultilineText(spriteBatch, "多行文本测试 Multiline text test\n" +
            "第一行 Line 1\n" +
            "第二行 Line 2", 0, height - 3 * lineHeight, fontSize);
        textRenderer.drawWrappedText(spriteBatch, "天地玄黄，宇宙洪荒。日月盈昃，辰宿列张。The quick brown fox jumps over the lazy dog.", 0, -descender, fontSize, width, true);
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
