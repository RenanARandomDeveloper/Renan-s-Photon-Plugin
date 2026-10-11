package net.renan.photonplugin.menus.resource.preview.ui;

import net.renan.photonplugin.menus.resource.preview.forge.ForgeConfig;
import net.renan.photonplugin.menus.resource.preview.neo.model.FxScene;

public sealed interface LoadedScene {
    boolean isEmpty();

    record Neo(FxScene scene) implements LoadedScene {
        @Override
        public boolean isEmpty() {
            return scene.objects().isEmpty();
        }
    }

    record Forge(ForgeConfig.ForgeScene scene) implements LoadedScene {
        @Override
        public boolean isEmpty() {
            return scene.main.objects.isEmpty();
        }
    }
}
