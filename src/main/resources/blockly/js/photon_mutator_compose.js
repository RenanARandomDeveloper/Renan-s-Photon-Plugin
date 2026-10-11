(function () {
    "use strict";

    const PM = (window.PhotonMutator = window.PhotonMutator || {});

    const DRAG_RETRY_MS = 50;

    function getStackedFeatures(containerBlock, ctx) {
        const features = [];
        const seen = new Set();
        let itemBlock = containerBlock.getInputTargetBlock("STACK");
        while (itemBlock) {
            if (!itemBlock.isInsertionMarker()) {
                const feature = ctx.blockTypeToFeature[itemBlock.type];
                if (feature && !seen.has(feature)) {
                    seen.add(feature);
                    features.push(feature);
                }
            }
            itemBlock = itemBlock.getNextBlock();
        }
        return features;
    }

    function pruneDuplicateMarkers(containerBlock, ctx) {
        const seen = new Set();
        let itemBlock = containerBlock.getInputTargetBlock("STACK");
        while (itemBlock) {
            const nextBlock = itemBlock.getNextBlock();
            if (!itemBlock.isInsertionMarker()) {
                const feature = ctx.blockTypeToFeature[itemBlock.type];
                if (feature) {
                    if (seen.has(feature))
                        itemBlock.dispose(true);
                    else
                        seen.add(feature);
                }
            }
            itemBlock = nextBlock;
        }
    }

    function refreshFlyout(containerBlock, ctx) {
        const workspace = containerBlock.workspace;
        const flyout = workspace && workspace.getFlyout ? workspace.getFlyout() : null;
        if (!flyout)
            return;

        const present = new Set(getStackedFeatures(containerBlock, ctx));
        const available = ctx.featureOrder.filter(function (feature) {
            return !present.has(feature);
        });
        const quarkXml = available.map(function (feature) {
            const element = Blockly.utils.xml.createElement("block");
            element.setAttribute("type", ctx.featureBlockTypes[feature]);
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

    function applyFeatureChange(block, newFeatures) {
        const core = PM.core;
        const oldFeatures = (block.enabledFeatures_ || []).slice();
        if (core.sameList(oldFeatures, newFeatures))
            return;

        core.withEventGroup(function () {
            block.enabledFeatures_ = newFeatures.slice();
            core.fireMutationEvent(block, core.stateOf(oldFeatures), core.stateOf(newFeatures));
            block.updateShape_(true);
        });
    }

    function schedule(block, containerBlock, ctx) {
        const apply = function () {
            block.photonMutatorComposeTimer_ = null;
            if (PM.core.isDead(containerBlock) || PM.core.isDead(block))
                return;

            const workspace = containerBlock.workspace;
            if (workspace && typeof workspace.isDragging === "function" && workspace.isDragging()) {
                block.photonMutatorComposeTimer_ = setTimeout(apply, DRAG_RETRY_MS);
                return;
            }

            try {
                pruneDuplicateMarkers(containerBlock, ctx);
                applyFeatureChange(block, getStackedFeatures(containerBlock, ctx));
            } catch (err) {
                console.error("[Photon] mutator compose failed (" + ctx.config.mutatorName + ")", err);
            }
            refreshFlyout(containerBlock, ctx);
        };

        clearTimeout(block.photonMutatorComposeTimer_);
        block.photonMutatorComposeTimer_ = setTimeout(apply, 0);
    }

    function decompose(block, workspace, ctx) {
        const containerBlock = workspace.newBlock(ctx.config.containerType);
        containerBlock.initSvg();
        let connection = containerBlock.getInput("STACK").connection;
        const enabled = PM.inputs.sanitizeFeatures(block.enabledFeatures_, ctx.featureSlots);
        for (const feature of enabled) {
            const itemBlock = workspace.newBlock(ctx.featureBlockTypes[feature]);
            itemBlock.initSvg();
            connection.connect(itemBlock.previousConnection);
            connection = itemBlock.nextConnection;
        }
        refreshFlyout(containerBlock, ctx);

        const dragListener = function (event) {
            if (PM.core.isDead(containerBlock)) {
                workspace.removeChangeListener(dragListener);
                return;
            }
            if (event.type !== Blockly.Events.BLOCK_DRAG || event.isStart)
                return;
            cleanupOrphanedMarkers(containerBlock);
        };
        workspace.addChangeListener(dragListener);

        return containerBlock;
    }

    PM.compose = {schedule: schedule, decompose: decompose};

    if (PM.boot) PM.boot();
})();