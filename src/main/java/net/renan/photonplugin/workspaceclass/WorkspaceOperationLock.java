package net.renan.photonplugin.workspaceclass;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public final class WorkspaceOperationLock {

    private static final ConcurrentHashMap<String, ReentrantLock> LOCKS = new ConcurrentHashMap<>();

    private WorkspaceOperationLock() {
    }

    public static ReentrantLock forKey(String key) {
        return LOCKS.computeIfAbsent(key, k -> new ReentrantLock());
    }
}