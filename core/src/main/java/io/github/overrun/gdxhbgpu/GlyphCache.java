package io.github.overrun.gdxhbgpu;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.GL30;
import com.badlogic.gdx.graphics.GL32;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.IntMap;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.harfbuzz.hb_glyph_extents_t;

import static org.lwjgl.system.MemoryUtil.NULL;
import static org.lwjgl.util.harfbuzz.HarfBuzz.*;
import static org.lwjgl.util.harfbuzz.HarfBuzzGPU.*;

class GlyphCache implements Disposable {
    private final long font;
    private final long drawEncoder;
    private int glTextureBuffer;
    private final int glTexture;
    private int capacity;
    private int nextGlyphLoc;
    private final IntMap<GlyphEntry> glyphEntryMap = new IntMap<>();

    public GlyphCache(long font, int capacity) {
        this.font = font;
        this.capacity = capacity;

        drawEncoder = hb_gpu_draw_create_or_fail();
        if (drawEncoder == NULL) {
            throw new IllegalStateException("Failed to create HarfBuzz GPU shape encoder!");
        }

        glTextureBuffer = Gdx.gl.glGenBuffer();
        Gdx.gl.glBindBuffer(GL32.GL_TEXTURE_BUFFER, glTextureBuffer);
        Gdx.gl.glBufferData(GL32.GL_TEXTURE_BUFFER, capacity * 8, null, GL20.GL_DYNAMIC_DRAW);

        glTexture = Gdx.gl.glGenTexture();
        Gdx.gl.glBindTexture(GL32.GL_TEXTURE_BUFFER, glTexture);
        Gdx.gl32.glTexBuffer(GL32.GL_TEXTURE_BUFFER, GL30.GL_RGBA16I, glTextureBuffer);
    }

    public GlyphEntry getOrCreate(int glyphId) {
        if (glyphEntryMap.containsKey(glyphId)) {
            return glyphEntryMap.get(glyphId);
        }
        GlyphEntry glyphEntry = encodeAndUpload(glyphId);
        glyphEntryMap.put(glyphId, glyphEntry);
        return glyphEntry;
    }

    private GlyphEntry encodeAndUpload(int glyphId) {
        hb_gpu_draw_glyph(drawEncoder, font, glyphId);

        long blob;
        GlyphEntry glyphEntry;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            hb_glyph_extents_t extents = hb_glyph_extents_t.malloc(stack);
            blob = hb_gpu_draw_encode(drawEncoder, extents);
            glyphEntry = new GlyphEntry(
                nextGlyphLoc,
                extents.x_bearing(),
                extents.y_bearing(),
                extents.width(),
                extents.height()
            );
        }
        int length = hb_blob_get_length(blob);

        int requiredTexels = length / 8;
        if (length > 0) {
            if (nextGlyphLoc + requiredTexels > capacity) {
                growCapacity(nextGlyphLoc + requiredTexels);
            }

            Gdx.gl.glBindBuffer(GL32.GL_TEXTURE_BUFFER, glTextureBuffer);
            Gdx.gl.glBufferSubData(GL32.GL_TEXTURE_BUFFER, nextGlyphLoc * 8, length, hb_blob_get_data(blob));
        }

        nextGlyphLoc += requiredTexels;

        hb_gpu_draw_recycle_blob(drawEncoder, blob);

        return glyphEntry;
    }

    private void growCapacity(int minCapacity) {
        int newCapacity = Math.max(capacity * 2, minCapacity + 1024);
        int oldBuffer = glTextureBuffer;

        int newBuffer = Gdx.gl.glGenBuffer();
        Gdx.gl.glBindBuffer(GL32.GL_TEXTURE_BUFFER, newBuffer);
        Gdx.gl.glBufferData(GL32.GL_TEXTURE_BUFFER, newCapacity * 8, null, GL20.GL_DYNAMIC_DRAW);

        Gdx.gl.glBindBuffer(GL30.GL_COPY_READ_BUFFER, oldBuffer);
        Gdx.gl.glBindBuffer(GL30.GL_COPY_WRITE_BUFFER, newBuffer);
        Gdx.gl30.glCopyBufferSubData(GL30.GL_COPY_READ_BUFFER, GL30.GL_COPY_WRITE_BUFFER, 0, 0, nextGlyphLoc * 8);

        Gdx.gl.glDeleteBuffer(oldBuffer);

        Gdx.gl.glBindTexture(GL32.GL_TEXTURE_BUFFER, glTexture);
        Gdx.gl32.glTexBuffer(GL32.GL_TEXTURE_BUFFER, GL30.GL_RGBA16I, newBuffer);

        glTextureBuffer = newBuffer;
        capacity = newCapacity;
    }

    public int glTexture() {
        return glTexture;
    }

    @Override
    public void dispose() {
        hb_gpu_draw_destroy(drawEncoder);
        Gdx.gl.glDeleteTexture(glTexture);
        Gdx.gl.glDeleteBuffer(glTextureBuffer);
    }
}
