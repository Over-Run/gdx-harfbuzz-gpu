package io.github.overrun.gdxhbgpu;

import com.badlogic.gdx.utils.Disposable;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.harfbuzz.hb_font_extents_t;

import static org.lwjgl.util.harfbuzz.HarfBuzz.*;

public class HBFont implements Disposable {
    private final long face;
    private final long font;
    private final int upem;
    private final int hAscender;
    private final int hDescender;
    private final int hLineGap;

    public HBFont(HBBlob blob, int faceIndex) {
        face = hb_face_create(blob.blob(), faceIndex);
        font = hb_font_create(face);
        upem = hb_face_get_upem(face);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            hb_font_extents_t extents = hb_font_extents_t.malloc(stack);
            hb_font_get_h_extents(font, extents);
            hAscender = extents.ascender();
            hDescender = extents.descender();
            hLineGap = extents.line_gap();
        }
    }

    @Override
    public void dispose() {
        hb_font_destroy(font);
        hb_face_destroy(face);
    }

    public long face() {
        return face;
    }

    public long font() {
        return font;
    }

    public int upem() {
        return upem;
    }

    public int hAscender() {
        return hAscender;
    }

    public int hDescender() {
        return hDescender;
    }

    public int hLineGap() {
        return hLineGap;
    }

    public float unitsToPixels(float units, float fontSizePx) {
        return units * (fontSizePx / upem);
    }

    public float getLineHeight(float fontSize) {
        return unitsToPixels(hAscender + hDescender - hLineGap, fontSize);
    }
}
