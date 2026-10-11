(function () {
    "use strict";

    const PM = (window.PhotonMutator = window.PhotonMutator || {});

    function makeValueSlot(name, labelKey, check, defaultBlockType, defaultField, defaultValue) {
        return {
            name: name,
            kind: "value",
            labelKey: labelKey,
            check: check,
            default: {blockType: defaultBlockType, field: defaultField, value: defaultValue}
        };
    }

    function makeNameSlot(slotName, labelKey) {
        return {
            name: slotName || "Name0",
            kind: "field",
            labelKey: labelKey || PM.core.KEY + "name",
            required: true,
            field: {type: "field_photon_fx_selector"}
        };
    }

    function makeVector3Slots(namePrefix, labelPrefix, defaultValue) {
        return ["X", "Y", "Z"].map(function (axis) {
            return makeValueSlot(namePrefix + axis + "0", labelPrefix + axis.toLowerCase(), "Number", "math_number", "NUM", defaultValue);
        });
    }

    function makeDropdownSlot(name, labelKey, options, defaultValue) {
        return {
            name: name,
            kind: "field",
            labelKey: labelKey,
            default: defaultValue,
            field: {type: "field_dropdown", options: options}
        };
    }

    const Slots = {value: makeValueSlot, name: makeNameSlot, vector3: makeVector3Slots, dropdown: makeDropdownSlot};

    function buildFeatureLibrary() {
        const KEY = PM.core.KEY;
        const t = PM.core.t;

        return {
            name: function () {
                return [Slots.name()];
            },
            offset: function () {
                return Slots.vector3("OffSet", KEY + "offset", "0");
            },
            rotation: function () {
                return Slots.vector3("Rotation", KEY + "rotation", "0");
            },
            scale: function () {
                return Slots.vector3("Scale", KEY + "scale", "1");
            },
            force: function () {
                return [Slots.value("Force0", KEY + "force", "Boolean", "logic_boolean", "BOOL", "FALSE")];
            },
            delay: function () {
                return [Slots.value("Delay0", KEY + "delay", "Number", "math_number", "NUM", "0")];
            },
            forcedeath: function () {
                return [Slots.value("Death0", KEY + "forcedeath", "Boolean", "logic_boolean", "BOOL", "FALSE")];
            },
            allowmulti: function () {
                return [Slots.value("Multi0", KEY + "allowmulti", "Boolean", "logic_boolean", "BOOL", "FALSE")];
            },
            state: function () {
                return [Slots.value("State0", KEY + "state", "Boolean", "logic_boolean", "BOOL", "FALSE")];
            },
            autorotate: function () {
                return [Slots.dropdown("Auto0", KEY + "autorotate", [
                    [t(KEY + "autorotate.none"), "NONE"],
                    [t(KEY + "autorotate.forward"), "FORWARD"],
                    [t(KEY + "autorotate.look"), "LOOK"],
                    [t(KEY + "autorotate.xrot"), "XROT"]
                ], "NONE")];
            }
        };
    }

    function normalizeConfig(raw) {
        if (!raw || !Array.isArray(raw.features) || !raw.features.length)
            throw new Error("mutator config without features");

        const KEY = PM.core.KEY;
        const library = buildFeatureLibrary();
        const cfg = Object.assign({}, raw);
        const family = (cfg.action && cfg.target) ? cfg.action + "_" + cfg.target : null;

        if (family) {
            cfg.containerType = cfg.containerType || family + "_mutator_container";
            cfg.mutatorName = cfg.mutatorName || family + "_mutator";
            cfg.validationExtensionName = cfg.validationExtensionName || family + "_validate_inputs";
        }
        cfg.statusField = cfg.statusField === undefined ? "RequiredValues" : cfg.statusField;
        cfg.containerMessageKey = cfg.containerMessageKey || KEY + "container";
        cfg.missingValueKey = cfg.missingValueKey || KEY + "missing_value";

        if (!cfg.containerType || !cfg.mutatorName || !cfg.validationExtensionName)
            throw new Error("mutator config needs action+target or explicit containerType/mutatorName/validationExtensionName");

        const seen = new Set();
        cfg.features = cfg.features.map(function (entry) {
            const f = (typeof entry === "string") ? {key: entry} : Object.assign({}, entry);
            if (!f.key)
                throw new Error("mutator feature without key in " + cfg.mutatorName);
            if (seen.has(f.key))
                throw new Error("duplicate mutator feature '" + f.key + "' in " + cfg.mutatorName);
            seen.add(f.key);

            f.blockType = f.blockType || (family ? family + "_mutator_" + f.key : null);
            if (!f.blockType)
                throw new Error("feature '" + f.key + "' without blockType in " + cfg.mutatorName);
            f.labelKey = f.labelKey || KEY + f.key;

            if (typeof f.slots === "function")
                f.slots = f.slots();
            if (!f.slots) {
                if (!Object.prototype.hasOwnProperty.call(library, f.key))
                    throw new Error("unknown mutator feature '" + f.key + "' in " + cfg.mutatorName);
                f.slots = library[f.key]();
            }
            return f;
        });

        return cfg;
    }

    PM.config = {slots: Slots, normalize: normalizeConfig};

    if (PM.boot) PM.boot();
})();
