package io.github.overrun.gdxhbgpu;

import com.badlogic.gdx.utils.Disposable;

import static org.lwjgl.util.harfbuzz.HarfBuzz.*;

public class HBFont implements Disposable {
    private final long face;
    private final long font;
    private final int upem;

    public HBFont(HBBlob blob, int faceIndex) {
        face = hb_face_create(blob.blob(), faceIndex);
        font = hb_font_create(face);
        upem = hb_face_get_upem(face);
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
}
