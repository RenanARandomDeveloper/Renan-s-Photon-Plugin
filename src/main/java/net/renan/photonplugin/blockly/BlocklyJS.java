package net.renan.photonplugin.blockly;

import net.mcreator.minecraft.DataListEntry;
import net.mcreator.ui.MCreator;
import net.mcreator.ui.dialogs.DataListSelectorDialog;
import net.mcreator.ui.init.L10N;
import net.mcreator.workspace.Workspace;
import net.renan.photonplugin.Log;

import javax.annotation.Nonnull;
import javax.swing.*;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class BlocklyJS {

    private final MCreator mcreator;
    private final boolean newApi;
    private final Object nestedLoopKey = new Object();
    private final AtomicBoolean dialogOpen = new AtomicBoolean(false);

    public BlocklyJS(MCreator mcreator, boolean newApi) {
        this.mcreator = mcreator;
        this.newApi = newApi;
        Log.info("bridge initialized (Using New API: %b)", newApi);
    }

    public Object getBridgeObject() {
        return newApi ? new NewBridge(this) : new BridgeLegacy(this);
    }

    public static class NewBridge {
        private final BlocklyJS parent;

        public NewBridge(BlocklyJS parent) {
            this.parent = parent;
        }

        @SuppressWarnings({"unused", "unchecked"})
        public void openDataListEntrySelector(Consumer callback) {
            parent.openSelectorNewApi((Consumer<String[]>) callback);
        }
    }

    public static class BridgeLegacy {
        private final BlocklyJS parent;

        public BridgeLegacy(BlocklyJS parent) {
            this.parent = parent;
        }

        @SuppressWarnings("unused")
        public void openDataListEntrySelector(Object callback) {
            parent.openSelectorLegacyApi(callback);
        }
    }

    private void openSelectorNewApi(Consumer<String[]> callback) {
        if (!dialogOpen.compareAndSet(false, true)) {
            Log.warn("FX selector is already open - duplicate request ignored.");
            if (callback != null) {
                callback.accept(buildDefaultRetval());
            }
            return;
        }

        Log.info("Opening FX selector dialog (New API)...");
        SwingUtilities.invokeLater(() -> {
            String[] retval = buildDefaultRetval();
            try {
                bringMCreatorToFront();
                DataListEntry selected = openDialog();
                if (selected != null) {
                    retval[0] = selected.getName();
                    retval[1] = selected.getReadableName();
                    Log.info("Selector selection finished (New API). Entry selected: %s (%s)", retval[0], retval[1]);
                } else {
                    Log.info("Selector selection canceled by user.");
                }
            } catch (Exception e) {
                Log.error("Error in selector (New API)", e);
            } finally {
                dialogOpen.set(false);
                if (callback != null) {
                    callback.accept(retval);
                }
            }
        });
    }

    private void openSelectorLegacyApi(Object jsCallback) {
        if (!dialogOpen.compareAndSet(false, true)) {
            Log.warn("FX selector is already open - duplicate request ignored.");
            invokeJsCallback(jsCallback, buildDefaultRetval());
            return;
        }

        Log.info("Opening FX selector dialog (Legacy API)...");
        FXCompat.runOnFxThreadOrDirect(() -> SwingUtilities.invokeLater(() -> {
            String[] retval = buildDefaultRetval();
            try {
                bringMCreatorToFront();
                DataListEntry selected = openDialog();
                if (selected != null) {
                    retval[0] = selected.getName();
                    retval[1] = selected.getReadableName();
                    Log.info("Selector selection finished (Legacy API). Entry selected: %s (%s)", retval[0], retval[1]);
                } else {
                    Log.info("Selector selection canceled by user (Legacy API).");
                }
            } catch (Exception e) {
                Log.error("Error in selector (Legacy API)", e);
            } finally {
                dialogOpen.set(false);
                platformExitNestedEventLoop(nestedLoopKey, retval);
            }
        }));

        String[] retval = platformEnterNestedEventLoop(nestedLoopKey, buildDefaultRetval());
        invokeJsCallback(jsCallback, retval);
    }

    private static void platformExitNestedEventLoop(Object key, Object value) {
        FXCompat.runOnFxThreadOrDirect(() -> {
            Method m = FXCompat.PLATFORM_EXIT_NESTED.get();
            if (m == null) return;
            try {
                m.invoke(null, key, value);
            } catch (Throwable e) {
                Log.error("Platform.exitNestedEventLoop failed", e);
            }
        });
    }

    private static String[] platformEnterNestedEventLoop(Object key, String[] fallback) {
        Method m = FXCompat.PLATFORM_ENTER_NESTED.get();
        if (m == null) {
            return fallback;
        }
        try {
            Object result = m.invoke(null, key);
            if (result instanceof String[]) {
                return (String[]) result;
            }
        } catch (Throwable e) {
            Log.error("Platform.enterNestedEventLoop failed", e);
        }
        return fallback;
    }

    private static void invokeJsCallback(Object jsCallback, String[] retval) {
        if (jsCallback == null) {
            return;
        }
        Method m = FXCompat.JSOBJECT_CALL.get();
        if (m == null) {
            return;
        }
        try {
            m.invoke(jsCallback, "callback", new Object[]{ retval[0], retval[1] });
            Log.info("Successfully invoked JS callback with values: [%s, %s]", retval[0], retval[1]);
        } catch (Throwable e) {
            Log.error("JS callback invocation failed", e);
        }
    }

    private void bringMCreatorToFront() {
        if (mcreator != null) {
            mcreator.toFront();
            mcreator.requestFocus();
        }
    }

    private DataListEntry openDialog() {
        return DataListSelectorDialog.openSelectorDialog(
                mcreator,
                BlocklyJS::getFXEntries,
                L10N.t("dialog.selector.title"),
                L10N.t("dialog.selector.photon_fx.message")
        );
    }

    private static String[] buildDefaultRetval() {
        return new String[]{ "", L10N.t("blockly.extension.data_list_selector.no_entry") };
    }

    private static List<DataListEntry> getFXEntries(@Nonnull Workspace workspace) {
        return FXListManager.getEntries(workspace.getWorkspaceFolder()).stream().map(name -> new DataListEntry.Dummy(name) {
            @Override
            public String getReadableName() {
                if (name == null || name.isEmpty()) {
                    return name;
                }
                return name.substring(0, 1).toUpperCase() + name.substring(1);
            }
        }).collect(Collectors.toList());
    }
}