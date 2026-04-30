package io.github.overrun.gdxhbgpu.demo;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Graphics;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.gl3.ImGuiImplGl3;
import imgui.glfw.ImGuiImplGlfw;
import io.github.overrun.gdxhbgpu.*;

public class Main extends ApplicationAdapter {
    private AssetManager assetManager;
    private HBBlob hbBlob;
    private HBFont hbFont;
    private SpriteBatch spriteBatch;
    private HBTextRenderer textRenderer;
    private final ScreenViewport viewport = new ScreenViewport();
    private final float[] scale = {1};
    private HBLayout layout;

    private static ImGuiImplGlfw imGuiGlfw;
    private static ImGuiImplGl3 imGuiGl3;

    private static InputProcessor tmpProcessor;

    public static void initImGui() {
        imGuiGlfw = new ImGuiImplGlfw();
        imGuiGl3 = new ImGuiImplGl3();
        long windowHandle = ((Lwjgl3Graphics) Gdx.graphics).getWindow().getWindowHandle();
        ImGui.createContext();
        ImGuiIO io = ImGui.getIO();
        io.setIniFilename(null); //Optional. Disables saving window layouts between sessions
        io.getFonts().addFontDefault();
        io.getFonts().build();
        imGuiGlfw.init(windowHandle, true);
        imGuiGl3.init("#version 150");
    }

    public static void startImGui() {
        if (tmpProcessor != null) { // Restore the input processor after ImGui caught all inputs, see #end()
            Gdx.input.setInputProcessor(tmpProcessor);
            tmpProcessor = null;
        }

        imGuiGl3.newFrame();
        imGuiGlfw.newFrame();
        ImGui.newFrame();
    }

    public static void endImGui() {
        ImGui.render();
        imGuiGl3.renderDrawData(ImGui.getDrawData());

        // If ImGui wants to capture the input, disable libGDX's input processor
        if (ImGui.getIO().getWantCaptureKeyboard() || ImGui.getIO().getWantCaptureMouse()) {
            tmpProcessor = Gdx.input.getInputProcessor();
            Gdx.input.setInputProcessor(null);
        }
    }

    public static void disposeImGui() {
        imGuiGl3.shutdown();
        imGuiGl3 = null;
        imGuiGlfw.shutdown();
        imGuiGlfw = null;
        ImGui.destroyContext();
    }

    @Override
    public void create() {
        Gdx.graphics.setForegroundFPS(0);

        assetManager = new AssetManager();
        HBBlobLoader.register(assetManager);
        assetManager.load("font/font", HBBlob.class);
        assetManager.finishLoading();

        hbBlob = assetManager.get("font/font");
        hbFont = new HBFont(hbBlob, 0);

        spriteBatch = new SpriteBatch();
        textRenderer = new HBTextRenderer(hbFont);

        layout = new HBLayout(
            hbFont,
            "天地玄黄，宇宙洪荒。日月盈昃，辰宿列张。The quick brown fox jumps over the lazy dog.",
            32
        );

        initImGui();
    }

    @Override
    public void render() {
        int width = Gdx.graphics.getWidth();
        int height = Gdx.graphics.getHeight();
        ScreenUtils.clear(Color.CLEAR);

        float fontSize = 32 * scale[0];
        float lineHeight = hbFont.getLineHeight(fontSize);
        float descender = hbFont.unitsToPixels(hbFont.hDescender(), fontSize);

        viewport.apply();
        spriteBatch.setProjectionMatrix(viewport.getCamera().combined);
        spriteBatch.begin();
        textRenderer.setColor(Color.GREEN);
        textRenderer.drawText(spriteBatch, "FPS: " + Gdx.graphics.getFramesPerSecond(), 0, height - lineHeight, fontSize);
        textRenderer.setColor(Color.YELLOW);
        textRenderer.drawText(spriteBatch, "Scale: " + scale[0], 0, height - 2 * lineHeight, fontSize);
        textRenderer.setColor(Color.WHITE);
        textRenderer.drawText(spriteBatch, "多行文本测试 Multiline text test\n" +
            "\n" +
            "第一行 Line 1\n" +
            "第二行 Line 2", 0, height - 3 * lineHeight, fontSize);
        layout.setFontSize(fontSize);
        layout.setMaxWidth(width);
        textRenderer.drawLayout(spriteBatch, layout, 0, -descender, false);
        spriteBatch.end();

        startImGui();
        ImGui.sliderFloat("Scale", scale, 0, 3);
        endImGui();
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
        disposeImGui();
    }
}
