(function () {

    function isTrivialValueBlock(targetBlock) {
        return targetBlock.type === "math_number" || targetBlock.type === "logic_boolean";
    }

    function getTrivialBlockValue(targetBlock) {
        return targetBlock.type === "math_number" ?
            targetBlock.getFieldValue("NUM") : targetBlock.getFieldValue("BOOL");
    }

    function rememberRawValue(block, storeKey, slotName, value) {
        if (!block.photonMutatorSavedValues_)
            block.photonMutatorSavedValues_ = {};
        if (!block.photonMutatorSavedValues_[storeKey])
            block.photonMutatorSavedValues_[storeKey] = {};
        block.photonMutatorSavedValues_[storeKey][slotName] = value;
    }

    function getRememberedValue(block, storeKey, slotName) {
        return block.photonMutatorSavedValues_ && block.photonMutatorSavedValues_[storeKey] ?
            block.photonMutatorSavedValues_[storeKey][slotName] : undefined;
    }

    function connectDefaultBlock(input, workspace, defaultSpec, rememberedValue) {
        if (!input || !input.connection || input.connection.targetBlock())
            return;
        const blockXML = Blockly.utils.xml.createElement("block");
        blockXML.setAttribute("type", defaultSpec.blockType);
        const fieldXML = Blockly.utils.xml.createElement("field");
        fieldXML.setAttribute("name", defaultSpec.field);
        fieldXML.textContent = (rememberedValue !== undefined && rememberedValue !== null) ?
            rememberedValue : defaultSpec.value;
        blockXML.appendChild(fieldXML);
        const defaultBlock = Blockly.Xml.domToBlock(blockXML, workspace);
        input.connection.connect(defaultBlock.outputConnection);
    }

    function addFeatureInputs(block, storeKey, feature, insertDefaults) {
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
                    const remembered = getRememberedValue(block, storeKey, slot.name);
                    const initial = (remembered !== undefined && remembered !== null) ? remembered : slot.default;
                    if (initial !== undefined && initial !== null)
                        block.setFieldValue(initial, slot.name);
                }
            } else {
                const input = block.appendValueInput(slot.name).setCheck(slot.check).setAlign(Blockly.ALIGN_RIGHT);
                if (slot.labelKey)
                    input.appendField(javabridge.t(slot.labelKey));
                if (insertDefaults)
                    connectDefaultBlock(input, block.workspace, slot.default, getRememberedValue(block, storeKey, slot.name));
            }
        });
    }

    function removeFeatureInputs(block, storeKey, feature) {
        feature.slots.forEach(function (slot) {
            const input = block.getInput(slot.name);
            if (!input)
                return;
            if (input.connection) {
                const targetBlock = input.connection.targetBlock();
                if (targetBlock) {
                    if (isTrivialValueBlock(targetBlock)) {
                        rememberRawValue(block, storeKey, slot.name, getTrivialBlockValue(targetBlock));
                        targetBlock.dispose(false);
                    } else {
                        targetBlock.unplug(false);
                        if (targetBlock.bumpNeighbours)
                            targetBlock.bumpNeighbours();
                    }
                }
            } else {
                const field = input.fieldRow && input.fieldRow.find(f => f.name === slot.name);
                if (field)
                    rememberRawValue(block, storeKey, slot.name, field.getValue());
            }
            block.removeInput(slot.name);
        });
    }

    function featureForBlockType(featureBlockTypes, blockType) {
        return Object.keys(featureBlockTypes).find(key => featureBlockTypes[key] === blockType);
    }

    function getStackedFeatures(containerBlock, featureBlockTypes) {
        const features = [];
        let itemBlock = containerBlock.getInputTargetBlock("STACK");
        while (itemBlock && !itemBlock.isInsertionMarker()) {
            const feature = featureForBlockType(featureBlockTypes, itemBlock.type);
            if (feature && features.indexOf(feature) === -1)
                features.push(feature);
            itemBlock = itemBlock.getNextBlock();
        }
        return features;
    }

    function pruneDuplicateMarkers(containerBlock, featureBlockTypes) {
        const seen = [];
        let itemBlock = containerBlock.getInputTargetBlock("STACK");
        while (itemBlock && !itemBlock.isInsertionMarker()) {
            const nextBlock = itemBlock.getNextBlock();
            const feature = featureForBlockType(featureBlockTypes, itemBlock.type);
            if (feature) {
                if (seen.indexOf(feature) !== -1)
                    itemBlock.dispose(true);
                else
                    seen.push(feature);
            }
            itemBlock = nextBlock;
        }
    }

    function refreshMutatorFlyout(containerBlock, featureOrder, featureBlockTypes) {
        const workspace = containerBlock.workspace;
        const flyout = workspace && workspace.getFlyout ? workspace.getFlyout() : null;
        if (!flyout)
            return;
        const present = getStackedFeatures(containerBlock, featureBlockTypes);
        const available = featureOrder.filter(f => present.indexOf(f) === -1);
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
        const featureOrder = config.features.map(f => f.key);
        const featureBlockTypes = {};
        const featureSlots = {};
        config.features.forEach(function (f) {
            featureBlockTypes[f.key] = f.blockType;
            featureSlots[f.key] = f;
        });

        const markerBlockDefs = config.features.map(f => ({
            "type": f.blockType,
            "message0": javabridge.t(f.labelKey),
            "previousStatement": null,
            "nextStatement": null,
            "colour": f.colour || config.colour,
            "duplicatable": false
        }));

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

        const storeKey = config.mutatorName;

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
                refreshMutatorFlyout(containerBlock, featureOrder, featureBlockTypes);
                workspace.addChangeListener(function (event) {
                    if (event.type !== Blockly.Events.BLOCK_DRAG || event.isStart)
                        return;
                    cleanupOrphanedMarkers(containerBlock);
                });
                return containerBlock;
            },

            compose: function (containerBlock) {
                const block = this;
                clearTimeout(this.photonMutatorComposeTimer_);
                this.photonMutatorComposeTimer_ = setTimeout(function () {
                    if (containerBlock.disposed)
                        return;
                    pruneDuplicateMarkers(containerBlock, featureBlockTypes);
                    block.enabledFeatures_ = getStackedFeatures(containerBlock, featureBlockTypes);
                    block.updateShape_(true);
                    refreshMutatorFlyout(containerBlock, featureOrder, featureBlockTypes);
                }, 0);
            },

            updateShape_: function (insertDefaults) {
                const enabled = this.enabledFeatures_ || [];
                const block = this;

                for (const feature of featureOrder) {
                    if (enabled.indexOf(feature) === -1)
                        removeFeatureInputs(block, storeKey, featureSlots[feature]);
                }

                for (const feature of enabled) {
                    addFeatureInputs(block, storeKey, featureSlots[feature], !!insertDefaults);
                }

                for (const feature of enabled) {
                    for (const slot of featureSlots[feature].slots) {
                        if (block.getInput(slot.name))
                            block.moveInputBefore(slot.name, null);
                    }
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

    window.PhotonMutatorCommon = { register: register };
    const pending = window.PhotonMutatorPendingConfigs || [];
    window.PhotonMutatorPendingConfigs = [];
    pending.forEach(register);

})();