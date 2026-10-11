(function () {
    "use strict";

    const PM = (window.PhotonMutator = window.PhotonMutator || {});

    function validateBlock(block, ctx, group) {
        if (PM.core.isDead(block))
            return;
        if (block.isInFlyout || (block.workspace && block.workspace.isFlyout))
            return;
        if (typeof block.isInsertionMarker === "function" && block.isInsertionMarker())
            return;

        PM.inputs.ensureStatusInput(block, ctx);

        const t = PM.core.t;
        const missingLabels = [];
        let missingValue = false;
        (block.enabledFeatures_ || []).forEach(function (feature) {
            const def = ctx.featureSlots[feature];
            if (!def)
                return;
            def.slots.forEach(function (slot) {
                const input = block.getInput(slot.name);
                if (!input)
                    return;

                let missing;
                if (slot.kind === "field")
                    missing = !!slot.required && !block.getFieldValue(slot.name);
                else
                    missing = !!(input.connection && !input.connection.targetBlock());

                if (missing && slot.kind !== "field")
                    missingValue = true;
                if (missing)
                    missingLabels.push(slot.labelKey ? String(t(slot.labelKey)).replace(/[\s:]+$/, "") : slot.name);
            });
        });
        let warning = null;
        if (missingLabels.length) {
            warning = t(ctx.config.missingValueKey) + "\n" + missingLabels.map(function (label) {
                return "\u2022 " + label;
            }).join("\n");
        }
        block.setWarningText(warning);
        syncStatusField(block, ctx, missingValue, group);
    }

    function syncStatusField(block, ctx, missingValue, group) {
        const base = ctx.config.statusField;
        if (!base)
            return;
        const field = block.getField(base + "0");
        if (!field)
            return;
        const wanted = missingValue ? "" : "ok";
        if (field.getValue() === wanted)
            return;

        if (PM.core.canSkipUndo()) {
            PM.core.withoutUndo(function () {
                field.setValue(wanted);
            });
            return;
        }

        const previousGroup = Blockly.Events.getGroup();
        if (group)
            Blockly.Events.setGroup(group);
        try {
            field.setValue(wanted);
        } finally {
            Blockly.Events.setGroup(previousGroup);
        }
    }

    function install(block, ctx) {
        block.setOnChange(function (e) {
            if (e && e.isUiEvent)
                return;
            validateBlock(this, ctx, e && e.group);
        });
        setTimeout(function () {
            try {
                validateBlock(block, ctx);
            } catch (err) {
                console.warn("[Photon] initial validation failed", err);
            }
        }, 0);
    }

    PM.validation = {install: install};

    if (PM.boot) PM.boot();
})();