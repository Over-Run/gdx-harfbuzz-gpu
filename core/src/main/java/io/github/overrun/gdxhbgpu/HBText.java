package io.github.overrun.gdxhbgpu;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.GL30;
import com.badlogic.gdx.graphics.GL32;
import com.badlogic.gdx.utils.Disposable;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.harfbuzz.hb_glyph_extents_t;
import org.lwjgl.util.harfbuzz.hb_glyph_info_t;
import org.lwjgl.util.harfbuzz.hb_glyph_position_t;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.lwjgl.system.MemoryUtil.NULL;
import static org.lwjgl.util.harfbuzz.HarfBuzz.*;
import static org.lwjgl.util.harfbuzz.HarfBuzzGPU.*;

public class HBText implements Disposable {
    private final long font;
    private int textureBuffer;
    private int texture;
    private final List<GlyphInfo> glyphInfoList = new ArrayList<>();

    public HBText(long font) {
        this.font = font;
    }

    public HBText(HBFont font) {
        this(font.font());
    }

    private int estimateBufferSize(int glyphCount, hb_glyph_info_t.Buffer glyphInfos, String text) {
        int totalSize = 0;
        long draw = hb_gpu_draw_create_or_fail();
        if (draw == NULL) {
            throw new IllegalStateException("Failed to create HarfBuzz GPU shape encoder!");
        }
        try {
            for (int i = 0; i < glyphCount; i++) {
                int glyphId = glyphInfos.get(i).codepoint();
                hb_gpu_draw_glyph(draw, font, glyphId);
                long blob = hb_gpu_draw_encode(draw);
                totalSize += hb_blob_get_length(blob);
                hb_gpu_draw_recycle_blob(draw, blob);
                hb_gpu_draw_reset(draw); // TODO: this is required on old version of HarfBuzz.
                // see: https://github.com/harfbuzz/harfbuzz/releases/tag/14.2.0
            }
        } finally {
            hb_gpu_draw_destroy(draw);
        }
        return totalSize;
    }

    public void create(String text) {
        glyphInfoList.clear();

        if (text.isEmpty()) {
            return;
        }

        long hbBuffer = hb_buffer_create();
        hb_buffer_add_utf8(hbBuffer, text, 0, -1);
        hb_buffer_set_direction(hbBuffer, HB_DIRECTION_LTR);
        hb_buffer_set_script(hbBuffer, HB_SCRIPT_COMMON);
        hb_buffer_set_language(hbBuffer, hb_language_from_string("en"));

        hb_shape(font, hbBuffer, null);

        int glyphCount = hb_buffer_get_length(hbBuffer);
        hb_glyph_info_t.Buffer glyphInfos = Objects.requireNonNull(hb_buffer_get_glyph_infos(hbBuffer));
        hb_glyph_position_t.Buffer glyphPositions = Objects.requireNonNull(hb_buffer_get_glyph_positions(hbBuffer));

        if (textureBuffer == 0) {
            textureBuffer = Gdx.gl.glGenBuffer();
            Gdx.gl.glBindBuffer(GL32.GL_TEXTURE_BUFFER, textureBuffer);
            Gdx.gl.glBufferData(GL32.GL_TEXTURE_BUFFER, estimateBufferSize(glyphCount, glyphInfos, text), null, GL20.GL_STATIC_DRAW);
        } else {
            Gdx.gl.glBindBuffer(GL32.GL_TEXTURE_BUFFER, textureBuffer);
        }
        if (texture == 0) {
            texture = Gdx.gl.glGenTexture();
            Gdx.gl.glBindTexture(GL32.GL_TEXTURE_BUFFER, texture);
            Gdx.gl32.glTexBuffer(GL32.GL_TEXTURE_BUFFER, GL30.GL_RGBA16I, textureBuffer);
        }

        long draw = hb_gpu_draw_create_or_fail();
        if (draw == NULL) {
            throw new IllegalStateException("Failed to create HarfBuzz GPU shape encoder!");
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            hb_glyph_extents_t extents = hb_glyph_extents_t.malloc(stack);
            int bufOffset = 0;
            int glyphLoc = 0;
            for (int i = 0; i < glyphCount; i++) {
                int glyphId = glyphInfos.get(i).codepoint();
                hb_glyph_position_t glyphPosition = glyphPositions.get(i);
                hb_gpu_draw_glyph(draw, font, glyphId);
                hb_gpu_draw_get_extents(draw, extents);
                long blob = hb_gpu_draw_encode(draw);
                int length = hb_blob_get_length(blob);
                if (length > 0) {
                    Gdx.gl.glBufferSubData(GL32.GL_TEXTURE_BUFFER, bufOffset, length, hb_blob_get_data(blob));
                }
                glyphInfoList.add(new GlyphInfo(
                    glyphLoc,
                    extents.x_bearing(),
                    extents.y_bearing(),
                    extents.width(),
                    extents.height(),
                    glyphPosition.x_advance(),
                    glyphPosition.y_advance(),
                    glyphPosition.x_offset(),
                    glyphPosition.y_offset()
                ));
                bufOffset += length;
                glyphLoc += length / 8;
                hb_gpu_draw_recycle_blob(draw, blob);
                hb_gpu_draw_reset(draw);
            }
        } finally {
            hb_gpu_draw_destroy(draw);
            if (hbBuffer != 0) {
                hb_buffer_destroy(hbBuffer);
            }
        }
    }

    @Override
    public void dispose() {
        Gdx.gl.glDeleteBuffer(textureBuffer);
        Gdx.gl.glDeleteTexture(texture);
    }

    public int textureBuffer() {
        return textureBuffer;
    }

    public int texture() {
        return texture;
    }

    public List<GlyphInfo> glyphInfoList() {
        return glyphInfoList;
    }
}
