package net.renan.photonplugin.blockly;

import net.mcreator.ui.MCreator;
import net.renan.photonplugin.Log;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

final class FXCompat {

    private FXCompat() {}

    static final LazyResolution<Method> PLATFORM_RUN_LATER = new LazyResolution<>(
            "javafx.application.Platform.runLater(Runnable)",
            () -> forNameAnyLoader("javafx.application.Platform").getMethod("runLater", Runnable.class));

    static final LazyResolution<Method> PLATFORM_ENTER_NESTED = new LazyResolution<>(
            "javafx.application.Platform.enterNestedEventLoop(Object)",
            () -> forNameAnyLoader("javafx.application.Platform").getMethod("enterNestedEventLoop", Object.class));

    static final LazyResolution<Method> PLATFORM_EXIT_NESTED = new LazyResolution<>(
            "javafx.application.Platform.exitNestedEventLoop(Object, Object)",
            () -> forNameAnyLoader("javafx.application.Platform").getMethod("exitNestedEventLoop", Object.class, Object.class));

    static final LazyResolution<Class<?>> WEBVIEW_CLASS = new LazyResolution<>(
            "javafx.scene.web.WebView",
            () -> forNameAnyLoader("javafx.scene.web.WebView"));

    static final LazyResolution<Class<?>> PARENT_CLASS = new LazyResolution<>(
            "javafx.scene.Parent",
            () -> forNameAnyLoader("javafx.scene.Parent"));

    static final LazyResolution<Method> WEBVIEW_GET_ENGINE = new LazyResolution<>(
            "WebView.getEngine()",
            () -> WEBVIEW_CLASS.get().getMethod("getEngine"));

    static final LazyResolution<Method> PARENT_GET_CHILDREN = new LazyResolution<>(
            "Parent.getChildrenUnmodifiable()",
            () -> PARENT_CLASS.get().getMethod("getChildrenUnmodifiable"));

    static final LazyResolution<Method> WEBENGINE_EXECUTE_SCRIPT = new LazyResolution<>(
            "WebEngine.executeScript(String)",
            () -> forNameAnyLoader("javafx.scene.web.WebEngine").getMethod("executeScript", String.class));

    static final LazyResolution<Method> WEBENGINE_GET_LOAD_WORKER = new LazyResolution<>(
            "WebEngine.getLoadWorker()",
            () -> forNameAnyLoader("javafx.scene.web.WebEngine").getMethod("getLoadWorker"));

    static final LazyResolution<Method> WORKER_STATE_PROPERTY = new LazyResolution<>(
            "javafx.concurrent.Worker.stateProperty()",
            () -> forNameAnyLoader("javafx.concurrent.Worker").getMethod("stateProperty"));

    static final LazyResolution<Class<?>> WORKER_STATE_CLASS = new LazyResolution<>(
            "javafx.concurrent.Worker$State",
            () -> forNameAnyLoader("javafx.concurrent.Worker$State"));

    static final LazyResolution<Class<?>> CHANGE_LISTENER_CLASS = new LazyResolution<>(
            "javafx.beans.value.ChangeListener",
            () -> forNameAnyLoader("javafx.beans.value.ChangeListener"));

    static final LazyResolution<Method> OBSERVABLE_VALUE_ADD_LISTENER = new LazyResolution<>(
            "javafx.beans.value.ObservableValue.addListener(ChangeListener)",
            () -> forNameAnyLoader("javafx.beans.value.ObservableValue")
                    .getMethod("addListener", CHANGE_LISTENER_CLASS.get()));

    static final LazyResolution<Method> JSOBJECT_SET_MEMBER = new LazyResolution<>(
            "JSObject.setMember(String, Object)",
            () -> forNameAnyLoader("netscape.javascript.JSObject").getMethod("setMember", String.class, Object.class));

    static final LazyResolution<Method> JSOBJECT_CALL = new LazyResolution<>(
            "JSObject.call(String, Object[])",
            () -> forNameAnyLoader("netscape.javascript.JSObject").getMethod("call", String.class, Object[].class));

    static void runOnFxThreadOrDirect(Runnable task) {
        Method runLater = PLATFORM_RUN_LATER.get();
        if (runLater == null) {
            task.run();
            return;
        }
        try {
            runLater.invoke(null, task);
        } catch (Throwable t) {
            Log.warn("Platform.runLater(...) invocation failed; executing directly instead.", t);
            task.run();
        }
    }

    static boolean attachWebEngineLoadListener(Object webEngine, Runnable onSucceeded) {
        if (webEngine == null) return false;
        try {
            Method getLoadWorker = WEBENGINE_GET_LOAD_WORKER.get();
            Method stateProperty = WORKER_STATE_PROPERTY.get();
            Class<?> changeListenerClass = CHANGE_LISTENER_CLASS.get();
            Method addListener = OBSERVABLE_VALUE_ADD_LISTENER.get();
            Class<?> stateClass = WORKER_STATE_CLASS.get();
            if (getLoadWorker == null || stateProperty == null || changeListenerClass == null
                    || addListener == null || stateClass == null) {
                return false;
            }

            Object worker = getLoadWorker.invoke(webEngine);
            if (worker == null) return false;

            Object stateObservable = stateProperty.invoke(worker);
            if (stateObservable == null) return false;

            Object succeededState = null;
            for (Object constant : stateClass.getEnumConstants()) {
                if ("SUCCEEDED".equals(((Enum<?>) constant).name())) {
                    succeededState = constant;
                    break;
                }
            }
            if (succeededState == null) return false;
            final Object succeeded = succeededState;

            Object listenerProxy = Proxy.newProxyInstance(
                    changeListenerClass.getClassLoader(),
                    new Class<?>[]{ changeListenerClass },
                    (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "changed" -> {
                                if (args != null && args.length == 3 && succeeded.equals(args[2])) {
                                    onSucceeded.run();
                                }
                                return null;
                            }
                            case "equals" -> {
                                return proxy == (args != null && args.length > 0 ? args[0] : null);
                            }
                            case "hashCode" -> {
                                return System.identityHashCode(proxy);
                            }
                            case "toString" -> {
                                return "WebEngineLoadListenerProxy@" + Integer.toHexString(System.identityHashCode(proxy));
                            }
                            default -> {
                                return null;
                            }
                        }
                    });

            addListener.invoke(stateObservable, listenerProxy);
            return true;
        } catch (Throwable t) {
            Log.warn("Failed to attach WebEngine load-state listener.", t);
            return false;
        }
    }

    private static Class<?> forNameAnyLoader(String className) throws ClassNotFoundException {
        ClassLoader[] candidates = {
                Thread.currentThread().getContextClassLoader(),
                MCreator.class.getClassLoader(),
                ClassLoader.getSystemClassLoader()
        };

        ClassNotFoundException last = null;
        for (ClassLoader loader : candidates) {
            if (loader == null) continue;
            try {
                return Class.forName(className, false, loader);
            } catch (ClassNotFoundException e) {
                last = e;
            }
        }
        throw last != null ? last : new ClassNotFoundException(className);
    }

    static final class LazyResolution<T> {
        private final String description;
        private final ThrowingSupplier<T> resolver;
        private volatile boolean attempted = false;
        private volatile T value;

        LazyResolution(String description, ThrowingSupplier<T> resolver) {
            this.description = description;
            this.resolver = resolver;
        }

        T get() {
            if (!attempted) {
                synchronized (this) {
                    if (!attempted) {
                        try {
                            value = resolver.get();
                        } catch (Throwable t) {
                            Log.warn("'" + description + "' is not available in this MCreator/JavaFX runtime; related functionality will fall back gracefully.", t);
                            value = null;
                        } finally {
                            attempted = true;
                        }
                    }
                }
            }
            return value;
        }

        @FunctionalInterface
        interface ThrowingSupplier<T> {
            T get() throws Throwable;
        }
    }
}