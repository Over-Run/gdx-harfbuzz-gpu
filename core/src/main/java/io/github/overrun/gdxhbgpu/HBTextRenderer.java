package io.github.overrun.gdxhbgpu;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Disposable;

import java.nio.ByteBuffer;

import static org.lwjgl.system.MemoryUtil.*;
import static org.lwjgl.util.harfbuzz.HarfBuzz.hb_buffer_create;
import static org.lwjgl.util.harfbuzz.HarfBuzz.hb_buffer_destroy;
import static org.lwjgl.util.harfbuzz.HarfBuzzGPU.*;

public class HBTextRenderer implements Disposable {
    /**
     * <pre>{@code
     * struct GlyphData {
     *     int glyphLoc;
     *     float glyphPos[2];
     *     float glyphOrigin[2];
     *     float glyphSize[2];
     * }
     * }</pre>
     */
    private static final int GLYPH_DATA_BYTES = 4 + 8 + 8 + 8;
    private static final int OFFSET_glyphLoc = 0;
    private static final int OFFSET_glyphPos = 4;
    private static final int OFFSET_glyphOrigin = 4 + 8;
    private static final int OFFSET_glyphSize = 4 + 8 + 8;
    private static final float[] vertices = {
        0, 0,
        1, 0,
        1, 1,
        0, 0,
        1, 1,
        0, 1,
    };
    private final HBFont font;
    private final long hbBuffer;
    private final GlyphCache glyphCache;
    private final ShaderProgram shader = createShader();
    private final Mesh mesh;
    private final int glyphVbo;
    private ByteBuffer glyphBuffer;
    private int bufferCapacity = 1024;
    private boolean bufferResized = true;
    private final float[] color = {1, 1, 1, 1};
    private final HBLayout layout;

    public HBTextRenderer(HBFont font) {
        this.font = font;
        hbBuffer = hb_buffer_create();
        glyphCache = new GlyphCache(font.font(), 65536);
        glyphVbo = Gdx.gl.glGenBuffer();
        glyphBuffer = memAlloc(bufferCapacity * GLYPH_DATA_BYTES);

        mesh = new Mesh(
            true,
            6,
            0,
            new VertexAttribute(VertexAttributes.Usage.TextureCoordinates, 2, ShaderProgram.TEXCOORD_ATTRIBUTE)
        );
        mesh.setVertices(vertices);

        shader.bind();
        shader.setUniformf("u_upem", font.upem());
        shader.setUniformi("hb_gpu_atlas", 0);

        layout = new HBLayout(font, null, 0);
    }

    private static final class ShaderSource {
        static final String VERTEX = hb_gpu_shader_source(HB_GPU_SHADER_STAGE_VERTEX, HB_GPU_SHADER_LANG_GLSL);
        static final String FRAGMENT = hb_gpu_shader_source(HB_GPU_SHADER_STAGE_FRAGMENT, HB_GPU_SHADER_LANG_GLSL);
        static final String VERTEX_DRAW = hb_gpu_draw_shader_source(HB_GPU_SHADER_STAGE_VERTEX, HB_GPU_SHADER_LANG_GLSL);
        static final String FRAGMENT_DRAW = hb_gpu_draw_shader_source(HB_GPU_SHADER_STAGE_FRAGMENT, HB_GPU_SHADER_LANG_GLSL);
    }

    public static ShaderProgram createShader() {
        String vertexShader = "#version 330 core\n" +
            ShaderSource.VERTEX + "\n" +
            ShaderSource.VERTEX_DRAW + "\n" +
            "in vec2 " + ShaderProgram.TEXCOORD_ATTRIBUTE + ";\n" +
            "in uint a_glyphLoc;\n" +
            "in vec2 a_glyphPos;\n" +
            "in vec2 a_glyphOrigin;\n" +
            "in vec2 a_glyphSize;\n" +
            "out vec2 v_renderCoord;\n" +
            "flat out uint v_glyphLoc;\n" +
            "uniform mat4 u_projTrans;\n" +
            "uniform float u_upem;\n" +
            "uniform float u_fontSize;\n" +
            "void main() {\n" +
            "    vec2 upemCoord = a_glyphOrigin + " + ShaderProgram.TEXCOORD_ATTRIBUTE + " * a_glyphSize;\n" +
            "    v_renderCoord = upemCoord;\n" +
            "    v_glyphLoc = a_glyphLoc;\n" +
            "    vec2 pixelPos = a_glyphPos + upemCoord * (u_fontSize / u_upem);\n" +
            "    gl_Position =  u_projTrans * vec4(pixelPos, 0.0, 1.0);\n" +
            "}";
        String fragmentShader = "#version 330 core\n" +
            ShaderSource.FRAGMENT + "\n" +
            ShaderSource.FRAGMENT_DRAW + "\n" +
            "in vec2 v_renderCoord;\n" +
            "flat in uint v_glyphLoc;\n" +
            "out vec4 FragColor;\n" +
            "uniform vec4 u_color;\n" +
            "void main() {\n" +
            "    float coverage = hb_gpu_draw(v_renderCoord, v_glyphLoc);\n" +
            "    FragColor = vec4(u_color.rgb, u_color.a * coverage);\n" +
            "}";
        ShaderProgram shaderProgram = new ShaderProgram(vertexShader, fragmentShader);
        if (!shaderProgram.isCompiled()) {
            throw new IllegalArgumentException("Error compiling shader: " + shaderProgram.getLog());
        }
        return shaderProgram;
    }

    public void drawText(Batch batch, String text, float x, float y, float fontSize) {
        drawText(batch, text, x, y, fontSize, 0);
    }

    public void drawText(Batch batch, String text, float x, float y, float fontSize, float maxWidth) {
        drawText(batch, text, x, y, fontSize, maxWidth, Align.left);
    }

    public void drawText(Batch batch, String text, float x, float y, float fontSize, float maxWidth, int alignment) {
        if (text.isEmpty()) return;

        layout.setText(text);
        layout.setFontSize(fontSize);
        layout.setMaxWidth(maxWidth);
        layout.setAlignment(alignment);
        drawLayout(batch, layout, x, y, true);
    }

    public void drawLayout(Batch batch, HBLayout layout, float x, float y, boolean baselineOnFirstLine) {
        layout.buildIfNeeded();

        ShaderProgram oldShader = batch.getShader();
        batch.flush();

        shader.bind();
        shader.setUniformMatrix("u_projTrans", batch.getProjectionMatrix());
        shader.setUniform4fv("u_color", this.color, 0, 4);
        shader.setUniformf("u_fontSize", layout.fontSize());

        Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
        Gdx.gl.glBindTexture(GL32.GL_TEXTURE_BUFFER, glyphCache.glTexture());

        boolean isBlending = Gdx.gl.glIsEnabled(GL20.GL_BLEND);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFuncSeparate(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        mesh.bind(shader);

        float lineHeight = font.getLineHeight(layout.fontSize());
        int lineCount = layout.getLineCount();

        float currentY = baselineOnFirstLine ? y : (y + (lineCount - 1) * lineHeight);
        for (int i = 0; i < lineCount; i++) {
            float currentX = x;
            HBLayout.Line line = layout.getLine(i);
            int glyphCount = line.glyphCount();

            ensureBufferCapacity(glyphCount);
            glyphBuffer.clear();

            for (int j = 0; j < glyphCount; j++) {
                int glyphId = line.glyphId(j);
                GlyphEntry g = glyphCache.getOrCreate(glyphId);

                glyphBuffer.putInt(g.glyphLoc)
                    .putFloat(currentX + line.xOffset(j))
                    .putFloat(currentY + line.yOffset(j))
                    .putFloat(g.xBearing)
                    .putFloat(g.yBearing)
                    .putFloat(g.width)
                    .putFloat(g.height);

                currentX += line.xAdvance(j);
                currentY += line.yAdvance(j);
            }

            glyphBuffer.flip();
            uploadInstanceData();
            Gdx.gl30.glDrawArraysInstanced(GL20.GL_TRIANGLES, 0, 6, glyphCount);

            currentY -= lineHeight;
        }

        mesh.unbind(shader);
        if (!isBlending) {
            Gdx.gl.glDisable(GL20.GL_BLEND);
        }

        batch.setShader(oldShader);
    }

    // TODO: begin, end, flush

    public void setColor(Color color) {
        this.color[0] = color.r;
        this.color[1] = color.g;
        this.color[2] = color.b;
        this.color[3] = color.a;
    }

    private void uploadInstanceData() {
        Gdx.gl.glBindBuffer(GL20.GL_ARRAY_BUFFER, glyphVbo);
        if (bufferResized) {
            // libGDX ignores the size parameter. We have to manually set the limit.
            int oldLimit = glyphBuffer.limit();
            glyphBuffer.limit(bufferCapacity * GLYPH_DATA_BYTES);
            Gdx.gl.glBufferData(GL20.GL_ARRAY_BUFFER, glyphBuffer.remaining(), glyphBuffer, GL20.GL_DYNAMIC_DRAW);
            glyphBuffer.limit(oldLimit);
        } else {
            Gdx.gl.glBufferSubData(GL20.GL_ARRAY_BUFFER, 0, glyphBuffer.remaining(), glyphBuffer);
        }

        shader.enableVertexAttribute("a_glyphLoc");
        Gdx.gl30.glVertexAttribIPointer(shader.getAttributeLocation("a_glyphLoc"), 1, GL20.GL_UNSIGNED_INT, GLYPH_DATA_BYTES, OFFSET_glyphLoc);
        Gdx.gl30.glVertexAttribDivisor(shader.getAttributeLocation("a_glyphLoc"), 1);

        shader.enableVertexAttribute("a_glyphPos");
        shader.setVertexAttribute("a_glyphPos", 2, GL20.GL_FLOAT, false, GLYPH_DATA_BYTES, OFFSET_glyphPos);
        Gdx.gl30.glVertexAttribDivisor(shader.getAttributeLocation("a_glyphPos"), 1);

        shader.enableVertexAttribute("a_glyphOrigin");
        shader.setVertexAttribute("a_glyphOrigin", 2, GL20.GL_FLOAT, false, GLYPH_DATA_BYTES, OFFSET_glyphOrigin);
        Gdx.gl30.glVertexAttribDivisor(shader.getAttributeLocation("a_glyphOrigin"), 1);

        shader.enableVertexAttribute("a_glyphSize");
        shader.setVertexAttribute("a_glyphSize", 2, GL20.GL_FLOAT, false, GLYPH_DATA_BYTES, OFFSET_glyphSize);
        Gdx.gl30.glVertexAttribDivisor(shader.getAttributeLocation("a_glyphSize"), 1);

        bufferResized = false;
    }

    private void ensureBufferCapacity(int capacity) {
        if (capacity < bufferCapacity) return;
        bufferCapacity = capacity * 2;
        glyphBuffer = memRealloc(glyphBuffer, bufferCapacity * GLYPH_DATA_BYTES);
        bufferResized = true;
    }

    @Override
    public void dispose() {
        mesh.dispose();
        memFree(glyphBuffer);
        Gdx.gl.glDeleteBuffer(glyphVbo);
        glyphCache.dispose();
        hb_buffer_destroy(hbBuffer);
        shader.dispose();
    }
}
