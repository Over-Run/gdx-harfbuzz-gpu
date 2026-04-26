package io.github.overrun.gdxhbgpu;

import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.assets.loaders.AsynchronousAssetLoader;
import com.badlogic.gdx.assets.loaders.FileHandleResolver;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.GdxRuntimeException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

import static org.lwjgl.system.MemoryUtil.memAlloc;

public class HBBlobLoader extends AsynchronousAssetLoader<HBBlob, HBBlobLoaderParameter> {
    public HBBlobLoader(FileHandleResolver resolver) {
        super(resolver);
    }

    public static void register(AssetManager assetManager) {
        assetManager.setLoader(HBBlob.class, new HBBlobLoader(assetManager.getFileHandleResolver()));
    }

    ByteBuffer data;

    @Override
    public void loadAsync(AssetManager manager, String fileName, FileHandle file, HBBlobLoaderParameter parameter) {
        int length = (int) file.length();
        data = memAlloc(length);
        byte[] bytes = new byte[Math.min(length, 8192)];
        try (InputStream stream = file.read()) {
            while (true) {
                int count = stream.read(bytes);
                if (count <= 0) break;
                data.put(bytes, 0, count);
            }
            data.flip();
        } catch (IOException e) {
            throw new GdxRuntimeException("Failed to load asset " + fileName, e);
        }
    }

    @Override
    public HBBlob loadSync(AssetManager manager, String fileName, FileHandle file, HBBlobLoaderParameter parameter) {
        return new HBBlob(data);
    }

    @SuppressWarnings("rawtypes")
    @Override
    public Array<AssetDescriptor> getDependencies(String fileName, FileHandle file, HBBlobLoaderParameter parameter) {
        return null;
    }
}
