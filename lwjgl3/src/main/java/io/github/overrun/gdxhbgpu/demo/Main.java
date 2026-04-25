package io.github.overrun.gdxhbgpu.demo;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.GL32;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import io.github.overrun.gdxhbgpu.*;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.List;

public class Main extends ApplicationAdapter {
    private AssetManager assetManager;
    private HBBlob hbBlob;
    private HBFont hbFont;
    private HBText hbText;
    private ShaderProgram shaderProgram;
    private GlyphMesh glyphMesh;
    private int offsetVbo;
    private int glyphPosVbo;
    private int glyphOriginVbo;
    private int glyphSizeVbo;
    private final ScreenViewport viewport = new ScreenViewport();
    private float scale = 1;

    @Override
    public void create() {
        Gdx.input.setInputProcessor(new InputAdapter() {
            @Override
            public boolean scrolled(float amountX, float amountY) {
                scale -= amountY * 0.1f;
                return true;
            }
        });

        assetManager = new AssetManager();
        assetManager.setLoader(HBBlob.class, new HBBlobLoader(assetManager.getFileHandleResolver()));
        assetManager.load("font/font", HBBlob.class);
        assetManager.finishLoading();

        hbBlob = assetManager.get("font/font");
        hbFont = new HBFont(hbBlob, 0);
        hbText = new HBText(hbFont);
        hbText.create("你好世界！The quick brown fox jumps over the lazy dog.");
        shaderProgram = HBRenderer.createShader();

        glyphMesh = new GlyphMesh();

        glyphMesh.mesh().bind(shaderProgram);
        List<GlyphInfo> glyphInfoList = hbText.glyphInfoList();

        IntBuffer glyphLocBuffer = BufferUtils.newIntBuffer(glyphInfoList.size());
        FloatBuffer posBuffer = BufferUtils.newFloatBuffer(glyphInfoList.size() * 2);
        FloatBuffer originBuffer = BufferUtils.newFloatBuffer(glyphInfoList.size() * 2);
        FloatBuffer sizeBuffer = BufferUtils.newFloatBuffer(glyphInfoList.size() * 2);
        float currentX = 0;
        float baselineY = 0;
        for (GlyphInfo glyphInfo : glyphInfoList) {
            float originX = glyphInfo.xBearing;
            float originY = glyphInfo.yBearing;
            float width = glyphInfo.width;
            float height = glyphInfo.height;

            glyphLocBuffer.put(glyphInfo.glyphLoc);

            posBuffer.put(currentX + glyphInfo.xOffset);
            posBuffer.put(baselineY + glyphInfo.yOffset);
            originBuffer.put(originX);
            originBuffer.put(originY);
            sizeBuffer.put(width);
            sizeBuffer.put(height);

            currentX += glyphInfo.xAdvance;
        }
        glyphLocBuffer.flip();
        posBuffer.flip();
        originBuffer.flip();
        sizeBuffer.flip();


        offsetVbo = Gdx.gl.glGenBuffer();
        Gdx.gl.glBindBuffer(GL20.GL_ARRAY_BUFFER, offsetVbo);
        Gdx.gl.glBufferData(GL20.GL_ARRAY_BUFFER, glyphLocBuffer.remaining() * 4, glyphLocBuffer, GL20.GL_STATIC_DRAW);

        shaderProgram.enableVertexAttribute("a_glyphLoc");
        Gdx.gl30.glVertexAttribIPointer(shaderProgram.getAttributeLocation("a_glyphLoc"), 1, GL20.GL_UNSIGNED_INT, 0, 0);
        Gdx.gl30.glVertexAttribDivisor(shaderProgram.getAttributeLocation("a_glyphLoc"), 1);


        glyphPosVbo = Gdx.gl.glGenBuffer();
        Gdx.gl.glBindBuffer(GL20.GL_ARRAY_BUFFER, glyphPosVbo);
        Gdx.gl.glBufferData(GL20.GL_ARRAY_BUFFER, posBuffer.remaining() * 4, posBuffer, GL20.GL_STATIC_DRAW);

        shaderProgram.enableVertexAttribute("a_glyphPos");
        shaderProgram.setVertexAttribute("a_glyphPos", 2, GL20.GL_FLOAT, false, 0, 0);
        Gdx.gl30.glVertexAttribDivisor(shaderProgram.getAttributeLocation("a_glyphPos"), 1);


        glyphOriginVbo = Gdx.gl.glGenBuffer();
        Gdx.gl.glBindBuffer(GL20.GL_ARRAY_BUFFER, glyphOriginVbo);
        Gdx.gl.glBufferData(GL20.GL_ARRAY_BUFFER, originBuffer.remaining() * 4, originBuffer, GL20.GL_STATIC_DRAW);

        shaderProgram.enableVertexAttribute("a_glyphOrigin");
        shaderProgram.setVertexAttribute("a_glyphOrigin", 2, GL20.GL_FLOAT, false, 0, 0);
        Gdx.gl30.glVertexAttribDivisor(shaderProgram.getAttributeLocation("a_glyphOrigin"), 1);


        glyphSizeVbo = Gdx.gl.glGenBuffer();
        Gdx.gl.glBindBuffer(GL20.GL_ARRAY_BUFFER, glyphSizeVbo);
        Gdx.gl.glBufferData(GL20.GL_ARRAY_BUFFER, sizeBuffer.remaining() * 4, sizeBuffer, GL20.GL_STATIC_DRAW);

        shaderProgram.enableVertexAttribute("a_glyphSize");
        shaderProgram.setVertexAttribute("a_glyphSize", 2, GL20.GL_FLOAT, false, 0, 0);
        Gdx.gl30.glVertexAttribDivisor(shaderProgram.getAttributeLocation("a_glyphSize"), 1);
    }

    @Override
    public void render() {
        ScreenUtils.clear(Color.CLEAR);

        shaderProgram.bind();
        shaderProgram.setUniformMatrix("u_projTrans", viewport.getCamera().combined);
        shaderProgram.setUniform4fv("u_color", new float[]{1.0f, 1.0f, 1.0f, 1.0f}, 0, 4);
        shaderProgram.setUniformf("u_upem", hbFont.upem());
        shaderProgram.setUniformf("u_fontSize", 32 * scale);

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFuncSeparate(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
        Gdx.gl.glBindTexture(GL32.GL_TEXTURE_BUFFER, hbText.texture());
        glyphMesh.mesh().bind(shaderProgram);
        Gdx.gl30.glDrawArraysInstanced(GL20.GL_TRIANGLES, 0, 6, hbText.glyphInfoList().size());
        glyphMesh.mesh().unbind(shaderProgram);
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, true);
    }

    @Override
    public void dispose() {
        hbText.dispose();
        hbFont.dispose();
        assetManager.dispose();
        shaderProgram.dispose();
        glyphMesh.dispose();
        Gdx.gl.glDeleteBuffer(offsetVbo);
        Gdx.gl.glDeleteBuffer(glyphPosVbo);
        Gdx.gl.glDeleteBuffer(glyphOriginVbo);
        Gdx.gl.glDeleteBuffer(glyphSizeVbo);
    }
}
