package io.github.overrun.gdxhbgpu;

class GlyphEntry {
    public final int glyphLoc;
    public final int xBearing;
    public final int yBearing;
    public final int width;
    public final int height;
    public final int hAdvance;
    public final int vAdvance;

    public GlyphEntry(
        int glyphLoc,
        int xBearing,
        int yBearing,
        int width,
        int height,
        int hAdvance,
        int vAdvance
    ) {
        this.glyphLoc = glyphLoc;
        this.xBearing = xBearing;
        this.yBearing = yBearing;
        this.width = width;
        this.height = height;
        this.hAdvance = hAdvance;
        this.vAdvance = vAdvance;
    }
}
