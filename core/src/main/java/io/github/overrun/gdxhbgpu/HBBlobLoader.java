package io.github.overrun.gdxhbgpu;

import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.assets.loaders.AsynchronousAssetLoader;
import com.badlogic.gdx.assets.loaders.FileHandleResolver;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Array;

import java.nio.ByteBuffer;

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
        data = HBBlob.load(file);
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
