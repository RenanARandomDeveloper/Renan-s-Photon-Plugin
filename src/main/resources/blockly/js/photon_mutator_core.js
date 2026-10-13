(function () {
    "use strict";

    const PM = (window.PhotonMutator = window.PhotonMutator || {});

    const KEY = "blockly.block.photon_mutator.";

    const MIN_DISPLAY_LENGTH = 100;
    try {
        const FieldClass = typeof Blockly !== "undefined" ? Blockly.Field : null;
        if (FieldClass && FieldClass.prototype && !FieldClass.prototype.photonDisplayLengthPatched_
            && typeof FieldClass.prototype.getDisplayText_ === "function") {
            const originalGetDisplayText = FieldClass.prototype.getDisplayText_;
            FieldClass.prototype.getDisplayText_ = function () {
                const previous = this.maxDisplayLength;
                if (!(previous >= MIN_DISPLAY_LENGTH))
                    this.maxDisplayLength = MIN_DISPLAY_LENGTH;
                try {
                    return originalGetDisplayText.apply(this, arguments);
                } finally {
                    this.maxDisplayLength = previous;
                }
            };
            FieldClass.prototype.photonDisplayLengthPatched_ = true;
        }
    } catch (err) {
        console.warn("[Photon] could not raise Blockly maxDisplayLength", err);
    }

    function t(key) {
        try {
            if (typeof javabridge !== "undefined" && javabridge && typeof javabridge.t === "function")
                return javabridge.t(key);
        } catch (err) {
            console.warn("[Photon] javabridge.t failed for " + key, err);
        }
        return key;
    }

    function isDead(block) {
        return !block || !!block.disposed || (typeof block.isDeadOrDying === "function" && block.isDeadOrDying());
    }

    function withEventGroup(fn) {
        const alreadyGrouped = !!Blockly.Events.getGroup();
        if (!alreadyGrouped)
            Blockly.Events.setGroup(true);
        try {
            return fn();
        } finally {
            if (!alreadyGrouped)
                Blockly.Events.setGroup(false);
        }
    }

    function sameList(a, b) {
        if (a.length !== b.length)
            return false;
        for (let i = 0; i < a.length; i++) {
            if (a[i] !== b[i])
                return false;
        }
        return true;
    }

    function stateOf(features) {
        return JSON.stringify({features: features.slice()});
    }

    function fireMutationEvent(block, oldState, newState) {
        if (oldState === newState)
            return;
        if (!Blockly.Events.isEnabled())
            return;
        try {
            const EventClass = (typeof Blockly.Events.get === "function" && Blockly.Events.get(Blockly.Events.BLOCK_CHANGE))
                || Blockly.Events.BlockChange
                || Blockly.Events.Change;
            Blockly.Events.fire(new EventClass(block, "mutation", null, oldState, newState));
        } catch (err) {
            console.warn("[Photon] could not fire mutation event", err);
        }
    }

    function fireCreateEvent(block) {
        if (!Blockly.Events.isEnabled())
            return;
        try {
            const EventClass = (typeof Blockly.Events.get === "function" && Blockly.Events.get(Blockly.Events.BLOCK_CREATE))
                || Blockly.Events.BlockCreate
                || Blockly.Events.Create;
            Blockly.Events.fire(new EventClass(block));
        } catch (err) {
            console.warn("[Photon] could not fire create event", err);
        }
    }

    function withoutUndo(fn) {
        const events = Blockly.Events;
        if (typeof events.setRecordUndo !== "function" || typeof events.getRecordUndo !== "function")
            return fn();
        const previous = events.getRecordUndo();
        events.setRecordUndo(false);
        try {
            return fn();
        } finally {
            events.setRecordUndo(previous);
        }
    }

    function canSkipUndo() {
        return typeof Blockly.Events.setRecordUndo === "function" && typeof Blockly.Events.getRecordUndo === "function";
    }

    PM.core = {
        KEY: KEY,
        t: t,
        isDead: isDead,
        withEventGroup: withEventGroup,
        sameList: sameList,
        stateOf: stateOf,
        fireMutationEvent: fireMutationEvent,
        fireCreateEvent: fireCreateEvent,
        withoutUndo: withoutUndo,
        canSkipUndo: canSkipUndo
    };

    if (PM.boot) PM.boot();
})();