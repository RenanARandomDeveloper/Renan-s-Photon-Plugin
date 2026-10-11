(function () {
    "use strict";

    const PM = (window.PhotonMutator = window.PhotonMutator || {});

    function rememberRawValue(block, slotName, value) {
        if (!block.photonMutatorSavedValues_)
            block.photonMutatorSavedValues_ = {};
        block.photonMutatorSavedValues_[slotName] = value;
    }

    function getRememberedValue(block, slotName) {
        return block.photonMutatorSavedValues_ ? block.photonMutatorSavedValues_[slotName] : undefined;
    }

    function rememberHint(block, slotName, hint) {
        if (!block.photonMutatorSavedHints_)
            block.photonMutatorSavedHints_ = {};
        block.photonMutatorSavedHints_[slotName] = hint;
    }

    function getRememberedHint(block, slotName) {
        return block.photonMutatorSavedHints_ ? block.photonMutatorSavedHints_[slotName] : undefined;
    }

    function isTrivialValueBlock(targetBlock, slot) {
        return !!slot.default && targetBlock.type === slot.default.blockType;
    }

    function getTrivialBlockValue(targetBlock, slot) {
        return targetBlock.getFieldValue(slot.default.field);
    }

    function connectDefaultBlock(input, workspace, defaultSpec, rememberedValue) {
        if (!input || !input.connection || input.connection.targetBlock())
            return;

        const fieldValue = (rememberedValue !== undefined && rememberedValue !== null) ? rememberedValue : defaultSpec.value;

        let defaultBlock;
        Blockly.Events.disable();
        try {
            defaultBlock = workspace.newBlock(defaultSpec.blockType);
            defaultBlock.setFieldValue(String(fieldValue), defaultSpec.field);
        } finally {
            Blockly.Events.enable();
        }

        if (workspace.rendered)
            defaultBlock.initSvg();

        PM.core.fireCreateEvent(defaultBlock);
        input.connection.connect(defaultBlock.outputConnection);

        if (workspace.rendered)
            defaultBlock.render();
    }

    function addFeatureInputs(block, feature, insertDefaults) {
        const t = PM.core.t;

        feature.slots.forEach(function (slot) {
            if (block.getInput(slot.name))
                return;

            if (slot.kind === "field") {
                const field = Blockly.fieldRegistry.fromJson(slot.field);
                if (!field) {
                    console.warn("[Photon] could not create field for slot " + slot.name);
                    return;
                }
                const input = block.appendDummyInput(slot.name).setAlign(Blockly.ALIGN_RIGHT);
                if (slot.labelKey)
                    input.appendField(t(slot.labelKey));
                input.appendField(field, slot.name);
                if (insertDefaults) {
                    const remembered = getRememberedValue(block, slot.name);
                    const initial = (remembered !== undefined && remembered !== null) ? remembered : slot.default;
                    if (initial !== undefined && initial !== null) {
                        const hint = getRememberedHint(block, slot.name);
                        if (hint && typeof field.setReadableNameHint === "function")
                            field.setReadableNameHint(hint);
                        block.setFieldValue(initial, slot.name);
                    }
                }
            } else {
                const input = block.appendValueInput(slot.name).setCheck(slot.check).setAlign(Blockly.ALIGN_RIGHT);
                if (slot.labelKey)
                    input.appendField(t(slot.labelKey));
                if (insertDefaults)
                    connectDefaultBlock(input, block.workspace, slot.default, getRememberedValue(block, slot.name));
            }
        });
    }

    function removeFeatureInputs(block, feature) {
        feature.slots.forEach(function (slot) {
            const input = block.getInput(slot.name);
            if (!input)
                return;

            if (input.connection) {
                const targetBlock = input.connection.targetBlock();
                if (targetBlock) {
                    if (isTrivialValueBlock(targetBlock, slot)) {
                        rememberRawValue(block, slot.name, getTrivialBlockValue(targetBlock, slot));
                        targetBlock.dispose(false);
                    } else {
                        targetBlock.unplug(false);
                        if (targetBlock.bumpNeighbours)
                            targetBlock.bumpNeighbours();
                    }
                }
            } else {
                const field = input.fieldRow && input.fieldRow.find(function (f) {
                    return f.name === slot.name;
                });
                if (field) {
                    rememberRawValue(block, slot.name, field.getValue());
                    if (typeof field.getReadableName === "function")
                        rememberHint(block, slot.name, field.getReadableName());
                }
            }

            block.removeInput(slot.name);
        });
    }

    function sanitizeFeatures(list, featureSlots) {
        const result = [];
        const seen = new Set();
        (Array.isArray(list) ? list : []).forEach(function (raw) {
            const key = String(raw).trim();
            if (!key || seen.has(key))
                return;
            if (!featureSlots[key]) {
                console.warn("[Photon] unknown mutator feature ignored: " + key);
                return;
            }
            seen.add(key);
            result.push(key);
        });
        return result;
    }

    function ensureStatusInput(block, ctx) {
        const base = ctx.config.statusField;
        if (!base)
            return;
        const name = base + "0";
        if (block.getInput(name))
            return;
        if (!Blockly.FieldLabelSerializable) {
            console.warn("[Photon] Blockly.FieldLabelSerializable not available, status field skipped");
            return;
        }
        const input = block.appendDummyInput(name);
        input.appendField(new Blockly.FieldLabelSerializable("ok"), name);
        input.setVisible(false);
    }

    function updateShape(block, ctx, insertDefaults) {
        const featureOrder = ctx.featureOrder;
        const featureSlots = ctx.featureSlots;
        const enabled = block.enabledFeatures_ || [];
        const enabledSet = new Set(enabled);

        PM.core.withEventGroup(function () {
            ensureStatusInput(block, ctx);

            for (const feature of featureOrder) {
                if (!enabledSet.has(feature))
                    removeFeatureInputs(block, featureSlots[feature]);
            }

            for (const feature of enabled) {
                if (featureSlots[feature])
                    addFeatureInputs(block, featureSlots[feature], !!insertDefaults);
            }

            const anchor = ctx.config.insertBefore && block.getInput(ctx.config.insertBefore) ? ctx.config.insertBefore : null;
            for (const feature of enabled) {
                if (!featureSlots[feature])
                    continue;
                for (const slot of featureSlots[feature].slots) {
                    if (block.getInput(slot.name))
                        block.moveInputBefore(slot.name, anchor);
                }
            }
        });
    }

    PM.inputs = {sanitizeFeatures: sanitizeFeatures, updateShape: updateShape, ensureStatusInput: ensureStatusInput};

    if (PM.boot) PM.boot();
})();