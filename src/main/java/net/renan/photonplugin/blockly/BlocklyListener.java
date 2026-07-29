package net.renan.photonplugin.blockly;

import net.mcreator.plugin.JavaPlugin;
import net.mcreator.plugin.MCREventListener;
import net.mcreator.ui.MCreator;
import net.renan.photonplugin.Log;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

public class BlocklyListener {
    private static final String BLOCKLY_PKG = "net.mcreator.plugin.events.ui.";
    private static final String BLOCKLY_CLS_DOM = "BlocklyPanelRegisterDOMData";
    private static final String BLOCKLY_CLS_JS = "BlocklyPanelRegisterJSObjects";

    private static final Map<Object, Object> KEEP_ALIVE_BRIDGES = Collections.synchronizedMap(new WeakHashMap<>());

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void register(JavaPlugin plugin) {
        final ClassLoader cl = BlocklyListener.class.getClassLoader();
        final String pkgPath = BLOCKLY_PKG.replace('.', '/');

        final boolean hasDOMData = classResourceExists(cl, pkgPath + BLOCKLY_CLS_DOM + ".class");
        final boolean hasJSObjects = classResourceExists(cl, pkgPath + BLOCKLY_CLS_JS + ".class");

        if (!hasDOMData && !hasJSObjects) {
            Log.error("Blockly event classes not found; FX selector will not be registered.");
            return;
        }

        final String targetFQN = hasDOMData ? (BLOCKLY_PKG + BLOCKLY_CLS_DOM) : (BLOCKLY_PKG + BLOCKLY_CLS_JS);
        Class<?> eventClass;

        try {
            eventClass = Class.forName(targetFQN, false, cl);
        } catch (Throwable t) {
            Log.error("Failed to resolve event class '" + targetFQN + "' via Class.forName", t);
            return;
        }

        MCREventListener listener = event -> {
            Object blocklyPanel = extractBlocklyPanel(event);
            if (blocklyPanel == null) return;

            MCreator mcreator = extractMCreator(blocklyPanel);
            if (mcreator == null) return;

            BlocklyJS blocklyJS = new BlocklyJS(mcreator, hasDOMData);
            Object bridgeObject = blocklyJS.getBridgeObject();
            KEEP_ALIVE_BRIDGES.put(blocklyPanel, bridgeObject);

            if (hasDOMData) {
                registerModernBridge(event, blocklyPanel, hasJSObjects, bridgeObject);
            } else {
                registerLegacyBridge(event, blocklyPanel, hasJSObjects, bridgeObject);
            }
        };

        plugin.addListener((Class) eventClass, listener);
    }

    private static Object extractBlocklyPanel(Object event) {
        try {
            return event.getClass().getMethod("getBlocklyPanel").invoke(event);
        } catch (Throwable ex) {
            Log.error("Failed to invoke getBlocklyPanel() on event " + event.getClass().getName(), ex);
            return null;
        }
    }

    private static MCreator extractMCreator(Object blocklyPanel) {
        try {
            return (MCreator) blocklyPanel.getClass().getMethod("getMCreator").invoke(blocklyPanel);
        } catch (Throwable ex) {
            Log.error("Failed to invoke getMCreator() on BlocklyPanel " + blocklyPanel.getClass().getName(), ex);
            return null;
        }
    }

    private static void registerModernBridge(Object event, Object blocklyPanel, boolean hasJSObjects, Object bridgeObject) {
        boolean registered = attemptBridgeRegistration(event, blocklyPanel, true, hasJSObjects, bridgeObject, false);
        if (!registered) {
            FXCompat.runOnFxThreadOrDirect(() ->
                    attemptBridgeRegistration(event, blocklyPanel, true, hasJSObjects, bridgeObject, true));
        }
    }

    private static void registerLegacyBridge(Object event, Object blocklyPanel, boolean hasJSObjects, Object bridgeObject) {
        FXCompat.runOnFxThreadOrDirect(() -> {
            attemptBridgeRegistration(event, blocklyPanel, false, hasJSObjects, bridgeObject, false);

            Object webEngine = findWebEngineInScene(blocklyPanel);
            boolean attached = FXCompat.attachWebEngineLoadListener(webEngine, () ->
                    attemptBridgeRegistration(event, blocklyPanel, false, hasJSObjects, bridgeObject, true));
            if (!attached) {
                Log.warn("Could not attach WebEngine load-state listener; legacy bridge relies on the initial injection only.");
            }
        });
    }

    private static boolean attemptBridgeRegistration(Object event, Object blocklyPanel, boolean hasDOMData, boolean hasJSObjects, Object bridgeObject, boolean logErrors) {
        boolean registered = false;

        if (hasDOMData) {
            try {
                event.getClass().getMethod("addJavaScriptBridge", String.class, Object.class).invoke(event, "photon_fx", bridgeObject);
                registered = true;
            } catch (Throwable ex) {
                if (logErrors) {
                    Log.error("Failed to register bridge via addJavaScriptBridge. Attempting legacy API fallback.", ex);
                }
            }
        }

        if (!registered && hasJSObjects) {
            registered = tryDirectWebEngineInjection(blocklyPanel, bridgeObject, logErrors);

            if (!registered) {
                try {
                    Object domWindow = event.getClass().getMethod("getDOMWindow").invoke(event);
                    domWindow.getClass().getMethod("put", Object.class, Object.class).invoke(domWindow, "photon_fx", bridgeObject);
                    registered = true;
                } catch (Throwable ex) {
                    if (logErrors) {
                        Log.error("Legacy fallback getDOMWindow().put failed on event " + event.getClass().getName(), ex);
                    }
                }
            }
        }

        if (!registered && logErrors) {
            Log.error("Failed to register 'photon_fx' bridge. FX selector will be unavailable.");
        }

        return registered;
    }

    private static boolean tryDirectWebEngineInjection(Object blocklyPanel, Object bridgeObject, boolean logErrors) {
        if (blocklyPanel == null) return false;

        Object webEngine = findWebEngineInScene(blocklyPanel);
        if (webEngine == null) return false;

        Method executeScript = FXCompat.WEBENGINE_EXECUTE_SCRIPT.get();
        Method setMember = FXCompat.JSOBJECT_SET_MEMBER.get();
        if (executeScript == null || setMember == null) return false;

        try {
            Object jsWindow = executeScript.invoke(webEngine, "window");
            if (jsWindow == null) return false;

            setMember.invoke(jsWindow, "photon_fx", bridgeObject);
            return true;
        } catch (Throwable ex) {
            if (logErrors) {
                Log.error("Failed to inject bridge directly via WebEngine/JSObject.", ex);
            }
            return false;
        }
    }

    private static Object findWebEngineInScene(Object blocklyPanel) {
        try {
            Object scene = blocklyPanel.getClass().getMethod("getScene").invoke(blocklyPanel);
            if (scene == null) return null;
            Object root = scene.getClass().getMethod("getRoot").invoke(scene);
            return findWebEngineInNode(root);
        } catch (Throwable ex) {
            Log.error("Failed to get BlocklyPanel Scene to locate WebView.", ex);
            return null;
        }
    }

    private static Object findWebEngineInNode(Object node) {
        if (node == null) return null;

        Class<?> webViewClass = FXCompat.WEBVIEW_CLASS.get();
        if (webViewClass != null && webViewClass.isInstance(node)) {
            Method getEngine = FXCompat.WEBVIEW_GET_ENGINE.get();
            if (getEngine == null) return null;
            try {
                return getEngine.invoke(node);
            } catch (Throwable ex) {
                Log.error("WebView found in Scene, but getEngine() failed.", ex);
                return null;
            }
        }

        Class<?> parentClass = FXCompat.PARENT_CLASS.get();
        if (parentClass != null && parentClass.isInstance(node)) {
            Method getChildren = FXCompat.PARENT_GET_CHILDREN.get();
            if (getChildren == null) return null;
            try {
                Object children = getChildren.invoke(node);
                if (children instanceof Iterable<?> iterable) {
                    for (Object child : iterable) {
                        Object found = findWebEngineInNode(child);
                        if (found != null) return found;
                    }
                }
            } catch (Throwable ignored) {}
        }

        return null;
    }

    private static boolean classResourceExists(ClassLoader cl, String resource) {
        try (InputStream is = cl.getResourceAsStream(resource)) {
            return is != null;
        } catch (Exception ignored) {
            return false;
        }
    }
}