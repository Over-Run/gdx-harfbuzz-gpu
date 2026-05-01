package io.github.overrun.gdxhbgpu;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.GL32;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Disposable;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.system.MemoryUtil.*;
import static org.lwjgl.util.harfbuzz.HarfBuzzGPU.*;

public class HBTextRenderer implements Disposable {
    private static final int GLYPH_DATA_BYTES = 8 + 8 + 8 + 4 + 4;
    private static final int OFFSET_a_position = 0;
    private static final int OFFSET_a_texCoord = 8;
    private static final int OFFSET_a_normal = 8 + 8;
    private static final int OFFSET_a_emPerPos = 8 + 8 + 8;
    private static final int OFFSET_a_glyphLoc = 8 + 8 + 8 + 4;
    private final HBFont font;
    private final GlyphCache glyphCache;
    private final ShaderProgram shader = createShader();
    private final int glyphVao;
    private final int glyphVbo;
    private final int glyphIbo;
    /**
     * <pre>{@code
     * struct GlyphData {
     *     float a_position[2];
     *     float a_texCoord[2];
     *     float a_normal[2];
     *     float a_emPerPos;
     *     uint  a_glyphLoc;
     * }
     * }</pre>
     */
    private ByteBuffer glyphBuffer;
    private IntBuffer indexBuffer;
    private int bufferCapacity = 1024;
    private boolean bufferResized = true;
    private boolean indexBufferResized = true;
    private int vertexCount = 0;
    private final float[] color = {1, 1, 1, 1};
    private final HBLayout layout;
    private final Matrix4 projectionMatrix = new Matrix4();
    private final Matrix4 transformMatrix = new Matrix4();
    private final Matrix4 combinedMatrix = new Matrix4();
    private boolean drawing = false;
    private final float[] viewport = new float[2];

    public HBTextRenderer(HBFont font) {
        this.font = font;
        glyphCache = new GlyphCache(font.font(), 65536);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer p = stack.mallocInt(1);
            Gdx.gl30.glGenVertexArrays(1, p);
            glyphVao = p.get(0);
        }
        glyphVbo = Gdx.gl.glGenBuffer();
        glyphIbo = Gdx.gl.glGenBuffer();
        glyphBuffer = memAlloc(bufferCapacity * GLYPH_DATA_BYTES);
        indexBuffer = memAllocInt(bufferCapacity * 6 / 4);

        shader.bind();
        shader.setUniformi("hb_gpu_atlas", 0);

        layout = new HBLayout(font, null, 0);

        initGL();
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
            "in vec2 a_position;\n" +
            "in vec2 a_texCoord;\n" +
            "in vec2 a_normal;\n" +
            "in float a_emPerPos;\n" +
            "in uint a_glyphLoc;\n" +
            "out vec2 v_renderCoord;\n" +
            "flat out uint v_glyphLoc;\n" +
            "uniform mat4 u_projTrans;\n" +
            "uniform vec2 u_viewport;\n" +
            "void main() {\n" +
            "    vec2 pos = a_position;\n" +
            "    vec2 tex = a_texCoord;\n" +
            "    vec4 jac = vec4(a_emPerPos, 0.0, 0.0, a_emPerPos);\n" +
            "    hb_gpu_dilate(pos, tex, a_normal, jac, u_projTrans, u_viewport);\n" +
            "    gl_Position =  u_projTrans * vec4(pos, 0.0, 1.0);\n" +
            "    v_renderCoord = tex;\n" +
            "    v_glyphLoc = a_glyphLoc;\n" +
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

    private void initGL() {
        int aPosition = shader.getAttributeLocation("a_position");
        int aTexCoord = shader.getAttributeLocation("a_texCoord");
        int aNormal = shader.getAttributeLocation("a_normal");
        int aEmPerPos = shader.getAttributeLocation("a_emPerPos");
        int aGlyphLoc = shader.getAttributeLocation("a_glyphLoc");

        Gdx.gl30.glBindVertexArray(glyphVao);
        Gdx.gl.glBindBuffer(GL20.GL_ARRAY_BUFFER, glyphVbo);
        Gdx.gl.glEnableVertexAttribArray(aPosition);
        Gdx.gl.glVertexAttribPointer(aPosition, 2, GL20.GL_FLOAT, false, GLYPH_DATA_BYTES, OFFSET_a_position);
        Gdx.gl.glEnableVertexAttribArray(aTexCoord);
        Gdx.gl.glVertexAttribPointer(aTexCoord, 2, GL20.GL_FLOAT, false, GLYPH_DATA_BYTES, OFFSET_a_texCoord);
        Gdx.gl.glEnableVertexAttribArray(aNormal);
        Gdx.gl.glVertexAttribPointer(aNormal, 2, GL20.GL_FLOAT, false, GLYPH_DATA_BYTES, OFFSET_a_normal);
        Gdx.gl.glEnableVertexAttribArray(aEmPerPos);
        Gdx.gl.glVertexAttribPointer(aEmPerPos, 1, GL20.GL_FLOAT, false, GLYPH_DATA_BYTES, OFFSET_a_emPerPos);
        Gdx.gl.glEnableVertexAttribArray(aGlyphLoc);
        Gdx.gl30.glVertexAttribIPointer(aGlyphLoc, 1, GL20.GL_UNSIGNED_INT, GLYPH_DATA_BYTES, OFFSET_a_glyphLoc);
        Gdx.gl.glBindBuffer(GL20.GL_ELEMENT_ARRAY_BUFFER, glyphIbo);
        Gdx.gl30.glBindVertexArray(0);
    }

    public void begin() {
        if (drawing) throw new IllegalStateException("TextRenderer.end must be called before begin.");

        glyphBuffer.clear();
        indexBuffer.clear();
        shader.bind();
        setupMatrices();
        setupColor();
        vertexCount = 0;

        drawing = true;
    }

    public void end() {
        if (!drawing) throw new IllegalStateException("TextRenderer.begin must be called before end.");
        if (vertexCount > 0) flush();

        drawing = false;
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    public void flush() {
        if (vertexCount == 0) return;

        Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
        Gdx.gl.glBindTexture(GL32.GL_TEXTURE_BUFFER, glyphCache.glTexture());
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFuncSeparate(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        glyphBuffer.flip();
        indexBuffer.flip();
        Gdx.gl30.glBindVertexArray(glyphVao);
        uploadData();
        Gdx.gl.glDrawElements(GL20.GL_TRIANGLES, indexBuffer.remaining(), GL20.GL_UNSIGNED_INT, 0);
        Gdx.gl30.glBindVertexArray(0);
        glyphBuffer.clear();
        indexBuffer.clear();
        vertexCount = 0;
    }

    private void uploadData() {
        Gdx.gl.glBindBuffer(GL20.GL_ARRAY_BUFFER, glyphVbo);
        if (bufferResized) {
            // libGDX ignores the size parameter. We have to manually set the limit.
            int oldLimit = glyphBuffer.limit();
            glyphBuffer.limit(bufferCapacity * GLYPH_DATA_BYTES);
            Gdx.gl.glBufferData(GL20.GL_ARRAY_BUFFER, glyphBuffer.remaining(), glyphBuffer, GL20.GL_STREAM_DRAW);
            glyphBuffer.limit(oldLimit);

            bufferResized = false;
        } else {
            Gdx.gl.glBufferSubData(GL20.GL_ARRAY_BUFFER, 0, glyphBuffer.remaining(), glyphBuffer);
        }

        Gdx.gl.glBindBuffer(GL20.GL_ELEMENT_ARRAY_BUFFER, glyphIbo);
        if (indexBufferResized) {
            int oldLimit = indexBuffer.limit();
            indexBuffer.limit(indexBuffer.capacity());
            Gdx.gl.glBufferData(GL20.GL_ELEMENT_ARRAY_BUFFER, indexBuffer.remaining(), indexBuffer, GL20.GL_STREAM_DRAW);
            indexBuffer.limit(oldLimit);

            indexBufferResized = false;
        } else {
            Gdx.gl.glBufferSubData(GL20.GL_ELEMENT_ARRAY_BUFFER, 0, indexBuffer.remaining(), indexBuffer);
        }
    }

    public void drawText(String text, float x, float y, float fontSize) {
        drawText(text, x, y, fontSize, 0);
    }

    public void drawText(String text, float x, float y, float fontSize, float maxWidth) {
        drawText(text, x, y, fontSize, maxWidth, Align.topLeft);
    }

    public void drawText(String text, float x, float y, float fontSize, float maxWidth, int alignment) {
        drawText(text, x, y, Math.max(maxWidth, 0), 0, fontSize, maxWidth, alignment);
    }

    public void drawText(
        String text,
        float x,
        float y,
        float targetWidth,
        float targetHeight,
        float fontSize,
        float maxWidth,
        int alignment
    ) {
        if (text.isEmpty()) return;

        layout.setText(text);
        layout.setFontSize(fontSize);
        layout.setMaxWidth(maxWidth);
        drawLayout(layout, x, y, targetWidth, targetHeight, alignment);
    }

    public void drawLayout(HBLayout layout, float x, float y, boolean baselineOnFirstLine) {
        if (!drawing) throw new IllegalStateException("TextRenderer.begin must be called before draw.");
        layout.buildIfNeeded();

        float fontSize = layout.fontSize();
        float scale = font.unitsToPixelFactor(fontSize);
        float emPerPos = font.upem() / fontSize;
        float lineHeight = font.getLineHeight(fontSize);
        int lineCount = layout.getLineCount();

        float currentY = baselineOnFirstLine ? y : (y + (lineCount - 1) * lineHeight);
        for (int i = 0; i < lineCount; i++) {
            float currentX = x;
            HBLayout.Line line = layout.getLine(i);
            int glyphCount = line.glyphCount();

            ensureBufferCapacity(glyphCount);
            ensureIndexBufferCapacity(glyphCount * 6 / 4);

            for (int j = 0; j < glyphCount; j++) {
                int glyphId = line.glyphId(j);
                GlyphEntry g = glyphCache.getOrCreate(glyphId);

                emitQuadIndices();
                emitVertices(g, currentX, currentY, line.xOffset(j), line.yOffset(j), scale, emPerPos);

                currentX += line.xAdvance(j);
                currentY += line.yAdvance(j);
            }

            currentY -= lineHeight;
        }
    }

    public void drawLayout(HBLayout layout, float x, float y, float targetWidth, float targetHeight, int alignment) {
        if (!drawing) throw new IllegalStateException("TextRenderer.begin must be called before draw.");
        layout.buildIfNeeded();

        float fontSize = layout.fontSize();
        float scale = font.unitsToPixelFactor(fontSize);
        float emPerPos = font.upem() / fontSize;
        float lineHeight = font.getLineHeight(fontSize);
        float hAscender = font.getHAscender(fontSize);
        int lineCount = layout.getLineCount();
        float totalHeight = lineCount * lineHeight;

        float textTop;
        if (targetHeight > 0) {
            if (Align.isTop(alignment)) {
                textTop = y + targetHeight;
            } else if (Align.isBottom(alignment)) {
                textTop = y + totalHeight;
            } else {
                // center vertical
                textTop = y + (targetHeight + totalHeight) / 2f;
            }
        } else {
            textTop = y + hAscender;
        }

        float currentY = textTop - hAscender;
        for (int i = 0; i < lineCount; i++) {
            HBLayout.Line line = layout.getLine(i);
            int glyphCount = line.glyphCount();

            ensureBufferCapacity(glyphCount);
            ensureIndexBufferCapacity(glyphCount * 6 / 4);

            float lineX;
            if (Align.isLeft(alignment)) {
                lineX = x;
            } else if (Align.isRight(alignment)) {
                lineX = x + targetWidth - line.width();
            } else {
                // center horizontal
                lineX = x + (targetWidth - line.width()) / 2f;
            }

            float currentX = lineX;
            for (int j = 0; j < glyphCount; j++) {
                int glyphId = line.glyphId(j);
                GlyphEntry g = glyphCache.getOrCreate(glyphId);

                emitQuadIndices();
                emitVertices(g, currentX, currentY, line.xOffset(j), line.yOffset(j), scale, emPerPos);

                currentX += line.xAdvance(j);
                currentY += line.yAdvance(j);
            }

            currentY -= lineHeight;
        }
    }

    private void emitVertex(
        float positionX,
        float positionY,
        float texCoordU,
        float texCoordV,
        float normalX,
        float normalY,
        float emPerPos,
        int glyphLoc
    ) {
        glyphBuffer.putFloat(positionX)
            .putFloat(positionY)
            .putFloat(texCoordU)
            .putFloat(texCoordV)
            .putFloat(normalX)
            .putFloat(normalY)
            .putFloat(emPerPos)
            .putInt(glyphLoc);
        vertexCount++;
    }

    private void emitVertices(
        GlyphEntry g,
        float currentX,
        float currentY,
        float xOffset,
        float yOffset,
        float scale,
        float emPerPos
    ) {
        float baseX = currentX + xOffset;
        float baseY = currentY + yOffset;
        float leftX = baseX + g.xBearing * scale;
        float topY = baseY + g.yBearing * scale;
        float rightX = leftX + g.width * scale;
        float bottomY = topY + g.height * scale;
        float u0 = g.xBearing;
        float v0 = g.yBearing;
        float u1 = g.xBearing + g.width;
        float v1 = g.yBearing + g.height;
        emitVertex(leftX, topY, u0, v0, -1, 1, emPerPos, g.glyphLoc);
        emitVertex(leftX, bottomY, u0, v1, -1, -1, emPerPos, g.glyphLoc);
        emitVertex(rightX, bottomY, u1, v1, 1, -1, emPerPos, g.glyphLoc);
        emitVertex(rightX, topY, u1, v0, 1, 1, emPerPos, g.glyphLoc);
    }

    private void emitQuadIndices() {
        indexBuffer.put(vertexCount)
            .put(vertexCount + 1)
            .put(vertexCount + 2)
            .put(vertexCount + 2)
            .put(vertexCount + 3)
            .put(vertexCount);
    }

    public void setColor(Color color) {
        if (drawing) flush();
        this.color[0] = color.r;
        this.color[1] = color.g;
        this.color[2] = color.b;
        this.color[3] = color.a;
        if (drawing) setupColor();
    }

    private void ensureBufferCapacity(int glyphCount) {
        int capacity = vertexCount + glyphCount * 4;
        if (capacity < bufferCapacity) return;
        bufferCapacity = capacity * 2;
        glyphBuffer = memRealloc(glyphBuffer, bufferCapacity * GLYPH_DATA_BYTES);
        bufferResized = true;
    }

    private void ensureIndexBufferCapacity(int count) {
        int capacity = indexBuffer.position() + count;
        if (capacity < indexBuffer.capacity()) return;
        indexBuffer = memRealloc(indexBuffer, indexBuffer.capacity() * 2);
        indexBufferResized = true;
    }

    @Override
    public void dispose() {
        memFree(glyphBuffer);
        memFree(indexBuffer);
        Gdx.gl.glDeleteBuffer(glyphVbo);
        Gdx.gl.glDeleteBuffer(glyphIbo);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            Gdx.gl30.glDeleteVertexArrays(1, stack.ints(glyphVao));
        }
        glyphCache.dispose();
        shader.dispose();
    }

    public Matrix4 projectionMatrix() {
        return projectionMatrix;
    }

    public Matrix4 transformMatrix() {
        return transformMatrix;
    }

    public void setProjectionMatrix(Matrix4 matrix) {
        if (drawing) flush();
        projectionMatrix.set(matrix);
        if (drawing) setupMatrices();
    }

    public void setTransformMatrix(Matrix4 matrix) {
        if (drawing) flush();
        transformMatrix.set(matrix);
        if (drawing) setupMatrices();
    }

    private void setupMatrices() {
        combinedMatrix.set(projectionMatrix).mul(transformMatrix);
        shader.setUniformMatrix("u_projTrans", combinedMatrix);
        viewport[0] = Gdx.graphics.getWidth();
        viewport[1] = Gdx.graphics.getHeight();
        shader.setUniform2fv("u_viewport", viewport, 0, 2);
    }

    private void setupColor() {
        shader.setUniform4fv("u_color", this.color, 0, 4);
    }
}
