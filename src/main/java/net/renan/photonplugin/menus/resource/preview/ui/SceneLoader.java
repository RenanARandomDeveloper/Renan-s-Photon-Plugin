package net.renan.photonplugin.menus.resource.preview.ui;

import net.mcreator.ui.init.L10N;

import net.renan.photonplugin.menus.resource.preview.common.PreviewMode;
import net.renan.photonplugin.menus.resource.preview.common.nbt.NbtReader;
import net.renan.photonplugin.menus.resource.preview.common.nbt.NbtTag;
import net.renan.photonplugin.menus.resource.preview.forge.ForgeParser;
import net.renan.photonplugin.menus.resource.preview.neo.parse.FxNbtParser;

import java.io.IOException;

public final class SceneLoader {
    private SceneLoader() {
    }

    public static LoadedScene parse(byte[] bytes, PreviewMode mode, boolean project) throws IOException {
        NbtTag.CompoundTag root = NbtReader.readCompressed(bytes);
        switch (mode) {
            case NEOFORGE -> {
                if (ForgeParser.looksLikeForge(root)) {
                    throw mismatch(PreviewMode.FORGE, mode);
                }
                return new LoadedScene.Neo(project ? FxNbtParser.parseProject(root) : FxNbtParser.parseScene(root));
            }
            case FORGE -> {
                if (FxNbtParser.looksLikeNeo(root)) {
                    throw mismatch(PreviewMode.NEOFORGE, mode);
                }
                return new LoadedScene.Forge(project ? ForgeParser.parseProject(root) : ForgeParser.parseFx(root));
            }
            default -> throw new IllegalArgumentException("unsupported mode " + mode);
        }
    }

    public static EditionMismatch fxPackNotSupported(PreviewMode mode) {
        return new EditionMismatch(L10N.t("plugin.photon.resourcemenu.fx.preview.error.fxpack_not_supported", PreviewMode.NEOFORGE.displayName(), mode.displayName()));
    }

    private static EditionMismatch mismatch(PreviewMode fileEdition, PreviewMode previewMode) {
        return new EditionMismatch(L10N.t("plugin.photon.resourcemenu.fx.preview.error.edition_mismatch", fileEdition.displayName(), previewMode.displayName()));
    }

    public static final class EditionMismatch extends IOException {
        public EditionMismatch(String message) {
            super(message);
        }
    }
}
