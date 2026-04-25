package io.github.overrun.gdxhbgpu;

import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.Disposable;

public class GlyphMesh implements Disposable {
    private static final float[] vertices = {
        0, 0,
        1, 0,
        1, 1,
        0, 0,
        1, 1,
        0, 1,
    };
    private final Mesh mesh;

    public GlyphMesh() {
        mesh = new Mesh(
            true,
            6,
            0,
            new VertexAttribute(VertexAttributes.Usage.TextureCoordinates, 2, ShaderProgram.TEXCOORD_ATTRIBUTE)
        );
        mesh.setVertices(vertices);
    }

    @Override
    public void dispose() {
        mesh.dispose();
    }

    public Mesh mesh() {
        return mesh;
    }
}
