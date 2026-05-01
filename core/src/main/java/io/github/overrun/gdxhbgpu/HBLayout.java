package io.github.overrun.gdxhbgpu;

import com.badlogic.gdx.utils.FloatArray;
import com.badlogic.gdx.utils.IntArray;
import com.ibm.icu.text.BreakIterator;
import com.ibm.icu.util.ULocale;
import org.lwjgl.util.harfbuzz.hb_glyph_info_t;
import org.lwjgl.util.harfbuzz.hb_glyph_position_t;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

import static org.lwjgl.util.harfbuzz.HarfBuzz.*;

public class HBLayout {
    private static final Pattern LINE_SEP_PATTERN = Pattern.compile("\\R");
    private HBFont font;
    private String text;
    private float fontSize;
    private float maxWidth;
    private List<Line> lines;
    private boolean dirty = true;
    private ULocale locale = ULocale.getDefault();

    public HBLayout(
        HBFont font,
        String text,
        float fontSize,
        float maxWidth
    ) {
        this.font = Objects.requireNonNull(font);
        this.text = text;
        this.fontSize = fontSize;
        this.maxWidth = maxWidth;
    }

    public HBLayout(
        HBFont font,
        String text,
        float fontSize
    ) {
        this(font, text, fontSize, 0);
    }

    public static final class Line {
        private static final Line EMPTY = new Line(
            new int[0],
            new float[0],
            new float[0],
            new float[0],
            new float[0],
            0
        );
        private final int[] glyphIds;
        private final float[] xOffsets;
        private final float[] yOffsets;
        private final float[] xAdvances;
        private final float[] yAdvances;
        private final float width;

        public Line(
            int[] glyphIds,
            float[] xOffsets,
            float[] yOffsets,
            float[] xAdvances,
            float[] yAdvances,
            float width
        ) {
            this.glyphIds = glyphIds;
            this.xOffsets = xOffsets;
            this.yOffsets = yOffsets;
            this.xAdvances = xAdvances;
            this.yAdvances = yAdvances;
            this.width = width;
        }

        public float width() {
            return width;
        }

        public int glyphCount() {
            return glyphIds.length;
        }

        public int glyphId(int index) {
            return glyphIds[index];
        }

        public float xOffset(int index) {
            return xOffsets[index];
        }

        public float yOffset(int index) {
            return yOffsets[index];
        }

        public float xAdvance(int index) {
            return xAdvances[index];
        }

        public float yAdvance(int index) {
            return yAdvances[index];
        }
    }

    private void generateLines(String line, long hbBuffer, float scale) {
        if (maxWidth <= 0) {
            generateSingleLine(line, hbBuffer, scale);
            return;
        }

        BreakIterator breakIterator = BreakIterator.getLineInstance(locale);
        breakIterator.setText(line);

        IntArray lineGlyphIds = new IntArray();
        FloatArray lineXOffsets = new FloatArray();
        FloatArray lineYOffsets = new FloatArray();
        FloatArray lineXAdvances = new FloatArray();
        FloatArray lineYAdvances = new FloatArray();
        float lineWidth = 0;

        int start = breakIterator.first();
        for (int end = breakIterator.next(); end != BreakIterator.DONE; start = end, end = breakIterator.next()) {
            if (end <= start) continue;

            String unitStr = line.substring(start, end);

            hb_buffer_reset(hbBuffer);
            hb_buffer_add_utf16(hbBuffer, unitStr, 0, -1);
            hb_buffer_guess_segment_properties(hbBuffer);
            hb_shape(font.font(), hbBuffer, null);

            int glyphCount = hb_buffer_get_length(hbBuffer);
            if (glyphCount == 0) continue;

            hb_glyph_info_t.Buffer glyphInfos = Objects.requireNonNull(hb_buffer_get_glyph_infos(hbBuffer));
            hb_glyph_position_t.Buffer glyphPositions = Objects.requireNonNull(hb_buffer_get_glyph_positions(hbBuffer));

            float unitWidth = 0;
            float[] unitXAdvs = new float[glyphCount];
            for (int i = 0; i < glyphCount; i++) {
                unitXAdvs[i] = glyphPositions.get(i).x_advance() * scale;
                unitWidth += unitXAdvs[i];
            }

            if (lineGlyphIds.notEmpty() && lineWidth + unitWidth > maxWidth) {
                flushCurrentLine(lineGlyphIds, lineXOffsets, lineYOffsets, lineXAdvances, lineYAdvances, lineWidth);
                lineGlyphIds.clear();
                lineXOffsets.clear();
                lineYOffsets.clear();
                lineXAdvances.clear();
                lineYAdvances.clear();
                lineWidth = 0;
            }

            for (int i = 0; i < glyphCount; i++) {
                lineGlyphIds.add(glyphInfos.get(i).codepoint());
                hb_glyph_position_t glyphPosition = glyphPositions.get(i);
                lineXOffsets.add(glyphPosition.x_offset() * scale);
                lineYOffsets.add(glyphPosition.y_offset() * scale);
                lineXAdvances.add(unitXAdvs[i]);
                lineYAdvances.add(glyphPosition.y_advance() * scale);
            }
            lineWidth += unitWidth;
        }

        if (lineGlyphIds.notEmpty()) {
            flushCurrentLine(lineGlyphIds, lineXOffsets, lineYOffsets, lineXAdvances, lineYAdvances, lineWidth);
        }
    }

    private void generateSingleLine(String line, long hbBuffer, float scale) {
        hb_buffer_reset(hbBuffer);
        hb_buffer_add_utf16(hbBuffer, line, 0, -1);
        hb_buffer_guess_segment_properties(hbBuffer);
        hb_shape(font.font(), hbBuffer, null);

        int glyphCount = hb_buffer_get_length(hbBuffer);
        if (glyphCount == 0) {
            this.lines.add(Line.EMPTY);
            return;
        }

        hb_glyph_info_t.Buffer glyphInfos = Objects.requireNonNull(hb_buffer_get_glyph_infos(hbBuffer));
        hb_glyph_position_t.Buffer glyphPositions = Objects.requireNonNull(hb_buffer_get_glyph_positions(hbBuffer));

        int[] glyphIds = new int[glyphCount];
        float[] xOffsets = new float[glyphCount];
        float[] yOffsets = new float[glyphCount];
        float[] xAdvances = new float[glyphCount];
        float[] yAdvances = new float[glyphCount];
        float totalWidth = 0;
        for (int i = 0; i < glyphCount; i++) {
            glyphIds[i] = glyphInfos.get(i).codepoint();
            hb_glyph_position_t glyphPosition = glyphPositions.get(i);
            xOffsets[i] = glyphPosition.x_offset() * scale;
            yOffsets[i] = glyphPosition.y_offset() * scale;
            float xAdv = glyphPosition.x_advance() * scale;
            float yAdv = glyphPosition.y_advance() * scale;
            xAdvances[i] = xAdv;
            yAdvances[i] = yAdv;
            totalWidth += xAdv;
        }

        this.lines.add(new Line(
            glyphIds,
            xOffsets,
            yOffsets,
            xAdvances,
            yAdvances,
            totalWidth
        ));
    }

    private void flushCurrentLine(IntArray ids, FloatArray xOffs, FloatArray yOffs, FloatArray xAdvs, FloatArray yAdvs, float totalWidth) {
        int size = ids.size;
        int[] glyphIds = new int[size];
        float[] xOffsets = new float[size];
        float[] yOffsets = new float[size];
        float[] xAdvances = new float[size];
        float[] yAdvances = new float[size];
        for (int i = 0; i < size; i++) {
            glyphIds[i] = ids.get(i);
            xOffsets[i] = xOffs.get(i);
            yOffsets[i] = yOffs.get(i);
            xAdvances[i] = xAdvs.get(i);
            yAdvances[i] = yAdvs.get(i);
        }
        this.lines.add(new Line(glyphIds, xOffsets, yOffsets, xAdvances, yAdvances, totalWidth));
    }

    void buildIfNeeded() {
        if (!dirty) return;
        dirty = false;

        this.lines = new ArrayList<>();

        if (text == null) return;

        String[] split = LINE_SEP_PATTERN.split(text);

        float scale = font.unitsToPixelFactor(fontSize);
        long hbBuffer = hb_buffer_create();
        for (String line : split) {
            generateLines(line, hbBuffer, scale);
        }
        hb_buffer_destroy(hbBuffer);
    }

    public HBFont font() {
        return font;
    }

    public String text() {
        return text;
    }

    public float fontSize() {
        return fontSize;
    }

    public int getLineCount() {
        buildIfNeeded();
        return lines.size();
    }

    public Line getLine(int index) {
        buildIfNeeded();
        return lines.get(index);
    }

    public float getWidth() {
        buildIfNeeded();
        float maxWidth = 0;
        for (Line line : lines) {
            maxWidth = Math.max(maxWidth, line.width());
        }
        return maxWidth;
    }

    public float getHeight() {
        return getLineCount() * font.getLineHeight(fontSize);
    }

    public void invalidate() {
        dirty = true;
    }

    public void setFont(HBFont font) {
        this.font = Objects.requireNonNull(font);
        invalidate();
    }

    public void setText(String text) {
        if (Objects.equals(this.text, text)) return;
        this.text = text;
        invalidate();
    }

    public void setFontSize(float fontSize) {
        if (this.fontSize == fontSize) return;
        this.fontSize = fontSize;
        invalidate();
    }

    public void setMaxWidth(float maxWidth) {
        if (this.maxWidth == maxWidth) return;
        this.maxWidth = maxWidth;
        invalidate();
    }

    public void setLocale(ULocale locale) {
        this.locale = locale != null ? locale : ULocale.getDefault();
        invalidate();
    }
}
