package io.github.overrun.gdxhbgpu;

import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.GdxRuntimeException;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

import static org.lwjgl.system.MemoryUtil.NULL;
import static org.lwjgl.system.MemoryUtil.memAddress;
import static org.lwjgl.util.harfbuzz.HarfBuzz.*;

public class HBBlob implements Disposable {
    private final long blob;

    public HBBlob(ByteBuffer data) {
        blob = hb_blob_create_or_fail(data, HB_MEMORY_MODE_READONLY, memAddress(data), MemoryUtil::nmemFree);
        if (blob == NULL) {
            throw new GdxRuntimeException("Failed to create HarfBuzz blob object");
        }
    }

    @Override
    public void dispose() {
        hb_blob_destroy(blob);
    }

    public long blob() {
        return blob;
    }
}
