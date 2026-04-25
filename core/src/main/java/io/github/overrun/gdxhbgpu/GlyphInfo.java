package io.github.overrun.gdxhbgpu;

public class GlyphInfo {
    public final int glyphLoc;
    public final int xBearing;
    public final int yBearing;
    public final int width;
    public final int height;
    public final int xAdvance;
    public final int yAdvance;
    public final int xOffset;
    public final int yOffset;

    public GlyphInfo(
        int glyphLoc,
        int xBearing,
        int yBearing,
        int width,
        int height,
        int xAdvance,
        int yAdvance,
        int xOffset,
        int yOffset
    ) {
        this.glyphLoc = glyphLoc;
        this.xBearing = xBearing;
        this.yBearing = yBearing;
        this.width = width;
        this.height = height;
        this.xAdvance = xAdvance;
        this.yAdvance = yAdvance;
        this.xOffset = xOffset;
        this.yOffset = yOffset;
    }
}
