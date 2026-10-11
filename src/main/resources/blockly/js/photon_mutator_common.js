(function () {
    "use strict";

    const PM = (window.PhotonMutator = window.PhotonMutator || {});

    const REQUIRED_MODULES = ["core", "config", "inputs", "compose", "validation"];
    const MISSING_MODULES_CHECK_MS = 3000;

    const registeredMutators = Object.create(null);
    let booted = false;

    function missingModules() {
        return REQUIRED_MODULES.filter(function (name) {
            return !PM[name];
        });
    }

    function doRegister(rawConfig) {
        const config = PM.config.normalize(rawConfig);

        if (registeredMutators[config.mutatorName]) {
            console.warn("[Photon] mutator already registered, ignoring: " + config.mutatorName);
            return false;
        }

        const featureOrder = config.features.map(function (f) {
            return f.key;
        });
        const featureBlockTypes = Object.create(null);
        const featureSlots = Object.create(null);
        const blockTypeToFeature = Object.create(null);

        config.features.forEach(function (f) {
            featureBlockTypes[f.key] = f.blockType;
            featureSlots[f.key] = f;
            blockTypeToFeature[f.blockType] = f.key;
        });

        const ctx = {
            config: config,
            featureOrder: featureOrder,
            featureBlockTypes: featureBlockTypes,
            featureSlots: featureSlots,
            blockTypeToFeature: blockTypeToFeature
        };

        const t = PM.core.t;

        const markerBlockDefs = config.features.map(function (f) {
            return {
                "type": f.blockType,
                "message0": t(f.labelKey),
                "previousStatement": null,
                "nextStatement": null,
                "colour": f.colour || config.colour,
                "duplicatable": false
            };
        });

        Blockly.defineBlocksWithJsonArray([
            {
                "type": config.containerType,
                "message0": t(config.containerMessageKey),
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
                this.enabledFeatures_ = PM.inputs.sanitizeFeatures(featuresAttr ? featuresAttr.split(",") : [], featureSlots);
                this.updateShape_(false);
            },

            saveExtraState: function () {
                return {features: (this.enabledFeatures_ || []).slice()};
            },

            loadExtraState: function (state) {
                this.enabledFeatures_ = PM.inputs.sanitizeFeatures(state && state.features, featureSlots);
                this.updateShape_(false);
            },

            decompose: function (workspace) {
                return PM.compose.decompose(this, workspace, ctx);
            },

            compose: function (containerBlock) {
                PM.compose.schedule(this, containerBlock, ctx);
            },

            updateShape_: function (insertDefaults) {
                PM.inputs.updateShape(this, ctx, insertDefaults);
            }
        };

        Blockly.Extensions.registerMutator(config.mutatorName, mixin, undefined, Object.values(featureBlockTypes));

        Blockly.Extensions.register(config.validationExtensionName, function () {
            PM.validation.install(this, ctx);
        });

        registeredMutators[config.mutatorName] = true;
        return true;
    }

    function register(rawConfig) {
        try {
            return doRegister(rawConfig);
        } catch (err) {
            const label = rawConfig && (rawConfig.mutatorName || (rawConfig.action + "_" + rawConfig.target)) || "?";
            console.error("[Photon] failed to register mutator " + label, err);
            return false;
        }
    }

    function boot() {
        if (booted || missingModules().length)
            return;
        booted = true;

        window.PhotonMutatorCommon = {register: register, slots: PM.config.slots, normalize: PM.config.normalize};

        const pending = window.PhotonMutatorPendingConfigs || [];
        window.PhotonMutatorPendingConfigs = [];
        pending.forEach(function (config) {
            register(config);
        });
    }

    PM.boot = boot;
    boot();

    setTimeout(function () {
        if (!booted)
            console.error("[Photon] mutators not registered, missing modules: " + missingModules().join(", "));
    }, MISSING_MODULES_CHECK_MS);
})();
