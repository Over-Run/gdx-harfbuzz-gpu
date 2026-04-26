package io.github.overrun.gdxhbgpu;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.GdxRuntimeException;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

import static org.lwjgl.system.MemoryUtil.*;
import static org.lwjgl.util.harfbuzz.HarfBuzz.*;

public class HBBlob implements Disposable {
    private final long blob;

    HBBlob(ByteBuffer data) {
        blob = hb_blob_create_or_fail(data, HB_MEMORY_MODE_READONLY, memAddress(data), MemoryUtil::nmemFree);
        if (blob == NULL) {
            throw new GdxRuntimeException("Failed to create HarfBuzz blob object");
        }
    }

    public HBBlob(FileHandle handle) {
        this(load(handle));
    }

    static ByteBuffer load(FileHandle file) {
        int length = (int) file.length();
        ByteBuffer data = memAlloc(length);
        byte[] bytes = new byte[Math.min(length, 8192)];
        try (InputStream stream = file.read()) {
            while (true) {
                int count = stream.read(bytes);
                if (count <= 0) break;
                data.put(bytes, 0, count);
            }
            return data.flip();
        } catch (IOException e) {
            throw new GdxRuntimeException("Failed to load asset " + file.name(), e);
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
