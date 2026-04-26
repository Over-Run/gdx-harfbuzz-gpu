package io.github.overrun.gdxhbgpu;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.Disposable;
import org.lwjgl.util.harfbuzz.hb_glyph_info_t;
import org.lwjgl.util.harfbuzz.hb_glyph_position_t;

import java.nio.ByteBuffer;
import java.util.Objects;

import static org.lwjgl.system.MemoryUtil.*;
import static org.lwjgl.util.harfbuzz.HarfBuzz.*;
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
    private int bufferCapacity = 128;
    private boolean bufferResized = true;

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
    }

    private static final class ShaderSource {
        static final String VERTEX = hb_gpu_shader_vertex_source(HB_GPU_SHADER_LANG_GLSL);
        static final String FRAGMENT = hb_gpu_shader_fragment_source(HB_GPU_SHADER_LANG_GLSL);
    }

    public static ShaderProgram createShader() {
        String vertexShader = "#version 330 core\n" +
            ShaderSource.VERTEX + "\n" +
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
        // TODO: in upstream hb_gpu_render is renamed to hb_gpu_draw
        String fragmentShader = "#version 330 core\n" +
            ShaderSource.FRAGMENT + "\n" +
            "in vec2 v_renderCoord;\n" +
            "flat in uint v_glyphLoc;\n" +
            "out vec4 FragColor;\n" +
            "uniform vec4 u_color;\n" +
            "void main() {\n" +
            "    float coverage = hb_gpu_render(v_renderCoord, v_glyphLoc);\n" +
            "    FragColor = vec4(u_color.rgb, u_color.a * coverage);\n" +
            "}";
        ShaderProgram shaderProgram = new ShaderProgram(vertexShader, fragmentShader);
        if (!shaderProgram.isCompiled()) {
            throw new IllegalArgumentException("Error compiling shader: " + shaderProgram.getLog());
        }
        return shaderProgram;
    }

    public void drawText(Batch batch, String text, float x, float y, float fontSize) {
        drawText(batch, text, x, y, fontSize, Color.WHITE);
    }

    // TODO: multiline text
    public void drawText(Batch batch, String text, float x, float y, float fontSize, Color color) {
        if (text.isEmpty()) return;

        hb_buffer_reset(hbBuffer);
        hb_buffer_add_utf8(hbBuffer, text, 0, -1);
        hb_buffer_set_direction(hbBuffer, HB_DIRECTION_LTR);
        hb_buffer_set_script(hbBuffer, HB_SCRIPT_COMMON);
        hb_buffer_set_language(hbBuffer, hb_language_from_string("en"));

        hb_shape(font.font(), hbBuffer, null);

        int glyphCount = hb_buffer_get_length(hbBuffer);
        if (glyphCount == 0) {
            return;
        }

        hb_glyph_info_t.Buffer glyphInfos = Objects.requireNonNull(hb_buffer_get_glyph_infos(hbBuffer));
        hb_glyph_position_t.Buffer glyphPositions = Objects.requireNonNull(hb_buffer_get_glyph_positions(hbBuffer));

        ensureBufferCapacity(glyphCount);
        glyphBuffer.clear();

        float scale = fontSize / font.upem();
        float currentX = x;
        float currentY = y;
        for (int i = 0; i < glyphCount; i++) {
            int glyphId = glyphInfos.get(i).codepoint();
            hb_glyph_position_t glyphPosition = glyphPositions.get(i);
            GlyphEntry g = glyphCache.getOrCreate(glyphId);

            float pxOffX = glyphPosition.x_offset() * scale;
            float pxOffY = glyphPosition.y_offset() * scale;

            glyphBuffer.putInt(g.glyphLoc)
                .putFloat(currentX + pxOffX)
                .putFloat(currentY + pxOffY)
                .putFloat(g.xBearing)
                .putFloat(g.yBearing)
                .putFloat(g.width)
                .putFloat(g.height);

            currentX += glyphPosition.x_advance() * scale;
            currentY += glyphPosition.y_advance() * scale;
        }

        glyphBuffer.flip();

        ShaderProgram oldShader = batch.getShader();
        batch.flush();

        shader.bind();
        shader.setUniformMatrix("u_projTrans", batch.getProjectionMatrix());
        shader.setUniform4fv("u_color", new float[]{color.r, color.g, color.b, color.a}, 0, 4);
        shader.setUniformf("u_upem", font.upem());
        shader.setUniformf("u_fontSize", fontSize);

        Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
        Gdx.gl.glBindTexture(GL32.GL_TEXTURE_BUFFER, glyphCache.glTexture());
        shader.setUniformi("hb_gpu_atlas", 0);

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFuncSeparate(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        mesh.bind(shader);
        uploadInstanceData();
        Gdx.gl30.glDrawArraysInstanced(GL20.GL_TRIANGLES, 0, 6, glyphCount);
        mesh.unbind(shader);
        Gdx.gl.glDisable(GL20.GL_BLEND);

        batch.setShader(oldShader);
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
