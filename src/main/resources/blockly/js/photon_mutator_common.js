(function () {
    "use strict";

    function makeValueSlot(name, labelKey, check, defaultBlockType, defaultField, defaultValue) {
        return {
            name: name,
            kind: "value",
            labelKey: labelKey,
            check: check,
            default: { blockType: defaultBlockType, field: defaultField, value: defaultValue }
        };
    }

    function makeNameSlot(slotName, labelKey) {
        return {
            name: slotName || "Name0",
            kind: "field",
            labelKey: labelKey || "blockly.block.photon_mutator.name",
            field: { type: "field_photon_fx_selector" }
        };
    }

    function makeVector3Slots(namePrefix, labelPrefix, defaultValue) {
        return ["X", "Y", "Z"].map(function (axis) {
            return makeValueSlot(namePrefix + axis + "0", labelPrefix + axis.toLowerCase(), "Number", "math_number", "NUM", defaultValue);
        });
    }

    const Slots = { value: makeValueSlot, name: makeNameSlot, vector3: makeVector3Slots };

    function isTrivialValueBlock(targetBlock, slot) {
        return !!slot.default && targetBlock.type === slot.default.blockType;
    }

    function getTrivialBlockValue(targetBlock, slot) {
        return targetBlock.getFieldValue(slot.default.field);
    }

    function rememberRawValue(block, slotName, value) {
        if (!block.photonMutatorSavedValues_)
            block.photonMutatorSavedValues_ = {};
        block.photonMutatorSavedValues_[slotName] = value;
    }

    function getRememberedValue(block, slotName) {
        return block.photonMutatorSavedValues_ ? block.photonMutatorSavedValues_[slotName] : undefined;
    }

    function connectDefaultBlock(input, workspace, defaultSpec, rememberedValue) {
        if (!input || !input.connection || input.connection.targetBlock())
            return;

        const defaultBlock = workspace.newBlock(defaultSpec.blockType);
        const fieldValue = (rememberedValue !== undefined && rememberedValue !== null) ? rememberedValue : defaultSpec.value;
        defaultBlock.setFieldValue(String(fieldValue), defaultSpec.field);

        if (workspace.rendered)
            defaultBlock.initSvg();

        input.connection.connect(defaultBlock.outputConnection);

        if (workspace.rendered)
            defaultBlock.render();
    }

    function addFeatureInputs(block, feature, insertDefaults) {
        feature.slots.forEach(function (slot) {
            if (block.getInput(slot.name))
                return;

            if (slot.kind === "field") {
                const field = Blockly.fieldRegistry.fromJson(slot.field);
                const input = block.appendDummyInput(slot.name).setAlign(Blockly.ALIGN_RIGHT);
                if (slot.labelKey)
                    input.appendField(javabridge.t(slot.labelKey));
                input.appendField(field, slot.name);
                if (insertDefaults) {
                    const remembered = getRememberedValue(block, slot.name);
                    const initial = (remembered !== undefined && remembered !== null) ? remembered : slot.default;
                    if (initial !== undefined && initial !== null)
                        block.setFieldValue(initial, slot.name);
                }
            } else {
                const input = block.appendValueInput(slot.name).setCheck(slot.check).setAlign(Blockly.ALIGN_RIGHT);
                if (slot.labelKey)
                    input.appendField(javabridge.t(slot.labelKey));
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
                const field = input.fieldRow && input.fieldRow.find(function (f) { return f.name === slot.name; });
                if (field)
                    rememberRawValue(block, slot.name, field.getValue());
            }

            block.removeInput(slot.name);
        });
    }

    function getStackedFeatures(containerBlock, blockTypeToFeature) {
        const features = [];
        const seen = new Set();
        let itemBlock = containerBlock.getInputTargetBlock("STACK");
        while (itemBlock && !itemBlock.isInsertionMarker()) {
            const feature = blockTypeToFeature[itemBlock.type];
            if (feature && !seen.has(feature)) {
                seen.add(feature);
                features.push(feature);
            }
            itemBlock = itemBlock.getNextBlock();
        }
        return features;
    }

    function pruneDuplicateMarkers(containerBlock, blockTypeToFeature) {
        const seen = new Set();
        let itemBlock = containerBlock.getInputTargetBlock("STACK");
        while (itemBlock && !itemBlock.isInsertionMarker()) {
            const nextBlock = itemBlock.getNextBlock();
            const feature = blockTypeToFeature[itemBlock.type];
            if (feature) {
                if (seen.has(feature))
                    itemBlock.dispose(true);
                else
                    seen.add(feature);
            }
            itemBlock = nextBlock;
        }
    }

    function refreshMutatorFlyout(containerBlock, featureOrder, featureBlockTypes, blockTypeToFeature) {
        const workspace = containerBlock.workspace;
        const flyout = workspace && workspace.getFlyout ? workspace.getFlyout() : null;
        if (!flyout)
            return;

        const present = new Set(getStackedFeatures(containerBlock, blockTypeToFeature));
        const available = featureOrder.filter(function (feature) { return !present.has(feature); });
        const quarkXml = available.map(function (feature) {
            const element = Blockly.utils.xml.createElement("block");
            element.setAttribute("type", featureBlockTypes[feature]);
            return element;
        });
        flyout.show(quarkXml);
    }

    function cleanupOrphanedMarkers(containerBlock) {
        containerBlock.workspace.getTopBlocks(false).forEach(function (block) {
            if (block !== containerBlock && !block.isInsertionMarker())
                block.dispose(false);
        });
    }

    function register(config) {
        const featureOrder = config.features.map(function (f) { return f.key; });
        const featureBlockTypes = {};
        const featureSlots = {};
        const blockTypeToFeature = {};

        config.features.forEach(function (f) {
            featureBlockTypes[f.key] = f.blockType;
            featureSlots[f.key] = f;
            blockTypeToFeature[f.blockType] = f.key;
        });

        const markerBlockDefs = config.features.map(function (f) {
            return {
                "type": f.blockType,
                "message0": javabridge.t(f.labelKey),
                "previousStatement": null,
                "nextStatement": null,
                "colour": f.colour || config.colour,
                "duplicatable": false
            };
        });

        Blockly.defineBlocksWithJsonArray([
            {
                "type": config.containerType,
                "message0": javabridge.t(config.containerMessageKey),
                "message1": "%1",
                "args1": [
                    {
                        "type": "input_statement",
                        "name": "STACK"
                    }
                ],
                "colour": config.colour
            }
        ].concat(markerBlockDefs));

        const mixin = {
            mutationToDom: function () {
                const container = Blockly.utils.xml.createElement("mutation");
                container.setAttribute("features", (this.enabledFeatures_ || []).join(","));
                return container;
            },

            domToMutation: function (xmlElement) {
                const featuresAttr = xmlElement.getAttribute("features");
                this.enabledFeatures_ = featuresAttr ? featuresAttr.split(",").filter(Boolean) : [];
                this.updateShape_(false);
            },

            saveExtraState: function () {
                return { features: (this.enabledFeatures_ || []).slice() };
            },

            loadExtraState: function (state) {
                this.enabledFeatures_ = (state && state.features) ? state.features.slice() : [];
                this.updateShape_(false);
            },

            decompose: function (workspace) {
                const containerBlock = workspace.newBlock(config.containerType);
                containerBlock.initSvg();
                let connection = containerBlock.getInput("STACK").connection;
                const enabled = this.enabledFeatures_ || [];
                for (const feature of enabled) {
                    const itemBlock = workspace.newBlock(featureBlockTypes[feature]);
                    itemBlock.initSvg();
                    connection.connect(itemBlock.previousConnection);
                    connection = itemBlock.nextConnection;
                }
                refreshMutatorFlyout(containerBlock, featureOrder, featureBlockTypes, blockTypeToFeature);

                const dragListener = function (event) {
                    if (containerBlock.disposed) {
                        workspace.removeChangeListener(dragListener);
                        return;
                    }
                    if (event.type !== Blockly.Events.BLOCK_DRAG || event.isStart)
                        return;
                    cleanupOrphanedMarkers(containerBlock);
                };
                workspace.addChangeListener(dragListener);

                return containerBlock;
            },

            compose: function (containerBlock) {
                const block = this;
                clearTimeout(this.photonMutatorComposeTimer_);
                this.photonMutatorComposeTimer_ = setTimeout(function () {
                    if (containerBlock.disposed || block.disposed)
                        return;
                    pruneDuplicateMarkers(containerBlock, blockTypeToFeature);
                    block.enabledFeatures_ = getStackedFeatures(containerBlock, blockTypeToFeature);
                    block.updateShape_(true);
                    refreshMutatorFlyout(containerBlock, featureOrder, featureBlockTypes, blockTypeToFeature);
                }, 0);
            },

            updateShape_: function (insertDefaults) {
                const enabled = this.enabledFeatures_ || [];
                const enabledSet = new Set(enabled);
                const block = this;

                const alreadyGrouped = !!Blockly.Events.getGroup();
                if (!alreadyGrouped)
                    Blockly.Events.setGroup(true);

                try {
                    for (const feature of featureOrder) {
                        if (!enabledSet.has(feature))
                            removeFeatureInputs(block, featureSlots[feature]);
                    }

                    for (const feature of enabled) {
                        addFeatureInputs(block, featureSlots[feature], !!insertDefaults);
                    }

                    for (const feature of enabled) {
                        for (const slot of featureSlots[feature].slots) {
                            if (block.getInput(slot.name))
                                block.moveInputBefore(slot.name, null);
                        }
                    }
                } finally {
                    if (!alreadyGrouped)
                        Blockly.Events.setGroup(false);
                }
            }
        };

        Blockly.Extensions.registerMutator(config.mutatorName, mixin, undefined, Object.values(featureBlockTypes));

        Blockly.Extensions.register(config.validationExtensionName, function () {
            this.setOnChange(function () {
                const enabled = this.enabledFeatures_ || [];
                const warnings = [];
                for (const feature of enabled) {
                    for (const slot of featureSlots[feature].slots) {
                        if (slot.kind === "field")
                            continue;
                        const input = this.getInput(slot.name);
                        if (input && input.connection && !input.connection.targetBlock())
                            warnings.push(javabridge.t(config.missingValueKey));
                    }
                }
                this.setWarningText(warnings.length ? warnings.join("\n") : null);
            });
        });
    }

    window.PhotonMutatorCommon = { register: register, slots: Slots };

    const pending = window.PhotonMutatorPendingConfigs || [];
    window.PhotonMutatorPendingConfigs = [];
    pending.forEach(register);

})();