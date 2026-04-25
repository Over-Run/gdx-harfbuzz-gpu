package io.github.overrun.gdxhbgpu;

public class GlyphInfo {
    public final int glyphLoc;
    public final int xBearing;
    public final int yBearing;
    public final int width;
    public final int height;
    public final int hAdvance;

    public GlyphInfo(int glyphLoc, int xBearing, int yBearing, int width, int height, int hAdvance) {
        this.glyphLoc = glyphLoc;
        this.xBearing = xBearing;
        this.yBearing = yBearing;
        this.width = width;
        this.height = height;
        this.hAdvance = hAdvance;
    }
}
