package io.github.overrun.gdxhbgpu;

import com.badlogic.gdx.graphics.glutils.ShaderProgram;

import static org.lwjgl.util.harfbuzz.HarfBuzzGPU.*;

public class HBRenderer {
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
            "    vec2 pixelPos = (a_glyphPos + upemCoord) * (u_fontSize / u_upem);\n" +
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
}
