package net.renan.photonplugin.menus.resource.preview.common.render;

import net.renan.photonplugin.menus.resource.preview.common.model.IMaterial;

public interface LibraryResolver {
    LibraryResolver NONE = new LibraryResolver() {
        @Override
        public IMaterial material(String resourcePath) {
            return null;
        }

        @Override
        public String graphTexture(String graphPath) {
            return null;
        }
    };

    IMaterial material(String resourcePath);

    String graphTexture(String graphPath);
}
