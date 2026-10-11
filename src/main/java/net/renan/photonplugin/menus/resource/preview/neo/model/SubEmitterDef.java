package net.renan.photonplugin.menus.resource.preview.neo.model;

import net.renan.photonplugin.menus.resource.preview.neo.model.number.NumberFunction;

public record SubEmitterDef(String fxId, Event event, NumberFunction emitProbability, int tickInterval,
                            boolean inheritColor, boolean inheritSize, boolean inheritRotation,
                            boolean inheritLifetime, boolean inheritDuration) {
    public enum Event {BIRTH, DEATH, TICK, UNSUPPORTED}
}
