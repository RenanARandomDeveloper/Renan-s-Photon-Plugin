package net.renan.photonplugin;

import net.mcreator.generator.Generator;
import net.mcreator.generator.GeneratorFlavor;
import net.mcreator.plugin.JavaPlugin;
import net.mcreator.plugin.Plugin;
import net.mcreator.plugin.events.workspace.MCreatorLoadedEvent;
import net.mcreator.plugin.events.workspace.WorkspaceRefactoringEvent;
import net.mcreator.ui.MCreator;
import net.renan.photonplugin.backup.BackupForge;
import net.renan.photonplugin.backup.BackupNeo;
import net.renan.photonplugin.blockly.BlocklyListener;
import net.renan.photonplugin.copiers.CopyFilesToAssetsFolderForge;
import net.renan.photonplugin.copiers.CopyFilesToAssetsFolderNeo;
import net.renan.photonplugin.fixers.AssetPlacementFixer;
import net.renan.photonplugin.fixers.ForgeMixinsExtra;
import net.renan.photonplugin.foldercreators.FolderCreatorForge;
import net.renan.photonplugin.foldercreators.FolderCreatorNeo;
import net.renan.photonplugin.menus.resource.forge.ResourceMenusForge;
import net.renan.photonplugin.menus.resource.neo.ResourceMenusNeo;
import net.renan.photonplugin.menus.workspace.WorkspaceMenuForge;
import net.renan.photonplugin.menus.workspace.WorkspaceMenuNeo;
import net.renan.photonplugin.workspaceclass.WorkspaceClassRemover;
import net.renan.photonplugin.workspaceclass.create.WorkspaceClassGeneratorForge;
import net.renan.photonplugin.workspaceclass.create.WorkspaceClassGeneratorNeo;

import javax.swing.*;
import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Photon extends JavaPlugin {
    private static final Logger LOG = Logger.getLogger(Photon.class.getName());

    public Photon(Plugin plugin) {
        super(plugin);

        try {
            BlocklyListener.register(this);

            addListener(MCreatorLoadedEvent.class, event -> SwingUtilities.invokeLater(() -> {
                MCreator mcreator = event.getMCreator();
                if (!isPhotonDependency(mcreator)) {
                    info("Plugin was not activated: current workspace does not contain 'photon_plugin' dependency.");
                    return;
                }

                info("Workspace loaded with Photon dependency.");
                WorkspaceRegistry.register(mcreator, mcreator.getWorkspaceFolder());
                activateFlavor(mcreator);
            }));

            addListener(WorkspaceRefactoringEvent.class, event -> SwingUtilities.invokeLater(() -> {
                MCreator mcreator = event.getMCreator();
                if (isPhotonDependency(mcreator)) {
                    info("Workspace refactored with Photon dependency.");
                    WorkspaceRegistry.register(mcreator, mcreator.getWorkspaceFolder());
                    activateFlavor(mcreator);
                } else {
                    info("Workspace refactored without Photon dependency.");
                    deactivateFlavor(mcreator);
                }
            }));

            info("Photon plugin was successfully loaded.");

        } catch (Throwable t) {
            error("Photon plugin failed to load.", t);
        }
    }

    private void activateFlavor(MCreator mcreator) {
        try {
            Generator generator = mcreator.getGenerator();
            GeneratorFlavor flavor = generator.getGeneratorConfiguration().getGeneratorFlavor();
            File mcFolder = mcreator.getWorkspaceFolder();

            info("Activating generator flavor: " + flavor.name());

            if (flavor == GeneratorFlavor.NEOFORGE) {
                AssetPlacementFixer.startSync(mcFolder);
                FolderCreatorNeo.createStructure(mcFolder);
                CopyFilesToAssetsFolderNeo.startSync(mcFolder);
                WorkspaceClassGeneratorNeo.generate(mcreator);
                ResourceMenusNeo.setupMenu(mcreator);
                WorkspaceMenuNeo.setupMenu(mcreator);
                BackupNeo.setup(mcreator);

            } else if (flavor == GeneratorFlavor.FORGE) {
                String version = String.valueOf(mcreator.getWorkspace().getMCreatorVersion());
                if (version.startsWith("2025002")) {
                    ForgeMixinsExtra.execute(mcFolder.toString());
                }

                WorkspaceClassGeneratorForge.generate(mcreator);
                FolderCreatorForge.createStructure(mcFolder);
                CopyFilesToAssetsFolderForge.startSync(mcFolder);
                ResourceMenusForge.setupMenu(mcreator);
                WorkspaceMenuForge.setupMenu(mcreator);
                BackupForge.setup(mcreator);

            } else {
                warn("Unknown or unsupported generator flavor: " + flavor.name());
            }
        } catch (Throwable t) {
            error("Failed to activate flavor configuration.", t);
        }
    }

    private void deactivateFlavor(MCreator mcreator) {
        File mcFolder = mcreator.getWorkspaceFolder();
        try {
            Generator generator = mcreator.getGenerator();
            GeneratorFlavor flavor = generator.getGeneratorConfiguration().getGeneratorFlavor();
            WorkspaceClassRemover.remove(mcreator);

            info("Deactivating generator flavor: " + flavor.name());

            if (flavor == GeneratorFlavor.NEOFORGE) {
                AssetPlacementFixer.stopSync(mcFolder);
                CopyFilesToAssetsFolderNeo.stopSync(mcFolder);
                ResourceMenusNeo.removeMenu(mcreator);
                WorkspaceMenuNeo.removeMenu(mcreator);
                BackupNeo.shutdown(mcreator);
            } else if (flavor == GeneratorFlavor.FORGE) {
                CopyFilesToAssetsFolderForge.stopSync(mcFolder);
                ResourceMenusForge.removeMenu(mcreator);
                WorkspaceMenuForge.removeMenu(mcreator);
                BackupForge.shutdown(mcreator);
            } else {
                warn("Unknown or unsupported generator flavor during deactivation: " + flavor.name());
            }
        } catch (Throwable t) {
            error("Failed to cleanly deactivate flavor configuration.", t);
        } finally {
            WorkspaceRegistry.unregister(mcreator);
            Log.releaseWorkspace(mcFolder);
        }
    }

    private static boolean isPhotonDependency(MCreator mcreator) {
        return mcreator.getWorkspace().getWorkspaceSettings().getMCreatorDependencies().contains("photon_plugin");
    }

    private static void info(String message) {
        System.out.println("[Photon] " + message);
    }

    private static void warn(String message) {
        System.out.println("[Photon] [WARN] " + message);
    }

    private static void error(String message, Throwable t) {
        LOG.log(Level.SEVERE, "[Photon] " + message, t);
    }
}