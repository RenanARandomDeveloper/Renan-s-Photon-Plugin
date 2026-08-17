(function () {
    "use strict";
    const Slots = (window.PhotonMutatorCommon && window.PhotonMutatorCommon.slots) || {
        value: function (name, labelKey, check, defaultBlockType, defaultField, defaultValue) {
            return { name: name, kind: "value", labelKey: labelKey, check: check, default: { blockType: defaultBlockType, field: defaultField, value: defaultValue } };
        },
        name: function (slotName, labelKey) {
            return {
                name: slotName || "Name0",
                kind: "field",
                labelKey: labelKey || "blockly.block.photon_mutator.name",
                field: { type: "field_photon_fx_selector" }
            };
        },
        vector3: function (namePrefix, labelPrefix, defaultValue) {
            return ["X", "Y", "Z"].map(function (axis) {
                return Slots.value(namePrefix + axis + "0", labelPrefix + axis.toLowerCase(), "Number", "math_number", "NUM", defaultValue);
            });
        }
    };

    const config = {
        containerType: "summon_entity_mutator_container",
        containerMessageKey: "blockly.block.photon_mutator.container",
        mutatorName: "summon_entity_mutator",
        validationExtensionName: "summon_entity_validate_inputs",
        missingValueKey: "blockly.block.photon_mutator.missing_value",
        colour: "#648c9b",
        features: [
            {
                key: "offset",
                blockType: "summon_entity_mutator_offset",
                labelKey: "blockly.block.photon_mutator.offset",
                slots: Slots.vector3("OffSet", "blockly.block.photon_mutator.offset", "0")
            },
            {
                key: "rotation",
                blockType: "summon_entity_mutator_rotation",
                labelKey: "blockly.block.photon_mutator.rotation",
                slots: Slots.vector3("Rotation", "blockly.block.photon_mutator.rotation", "0")
            },
            {
                key: "scale",
                blockType: "summon_entity_mutator_scale",
                labelKey: "blockly.block.photon_mutator.scale",
                slots: Slots.vector3("Scale", "blockly.block.photon_mutator.scale", "1")
            },
            {
                key: "delay",
                blockType: "summon_entity_mutator_delay",
                labelKey: "blockly.block.photon_mutator.delay",
                slots: [Slots.value("Delay0", "blockly.block.photon_mutator.delay", "Number", "math_number", "NUM", "0")]
            },
            {
                key: "forcedeath",
                blockType: "summon_entity_mutator_forcedeath",
                labelKey: "blockly.block.photon_mutator.forcedeath",
                slots: [Slots.value("Death0", "blockly.block.photon_mutator.forcedeath", "Boolean", "logic_boolean", "BOOL", "FALSE")]
            },
            {
                key: "allowmulti",
                blockType: "summon_entity_mutator_allowmulti",
                labelKey: "blockly.block.photon_mutator.allowmulti",
                slots: [Slots.value("Multi0", "blockly.block.photon_mutator.allowmulti", "Boolean", "logic_boolean", "BOOL", "FALSE")]
            },
            {
                key: "autorotate",
                blockType: "summon_entity_mutator_autorotate",
                labelKey: "blockly.block.photon_mutator.autorotate",
                slots: [
                    {
                        name: "Auto0",
                        kind: "field",
                        labelKey: "blockly.block.photon_mutator.autorotate",
                        default: "NONE",
                        field: {
                            type: "field_dropdown",
                            options: [
                                [javabridge.t("blockly.block.photon_mutator.autorotate.none"), "NONE"],
                                [javabridge.t("blockly.block.photon_mutator.autorotate.forward"), "FORWARD"],
                                [javabridge.t("blockly.block.photon_mutator.autorotate.look"), "LOOK"],
                                [javabridge.t("blockly.block.photon_mutator.autorotate.xrot"), "XROT"]
                            ]
                        }
                    }
                ]
            }
        ]
    };

    if (window.PhotonMutatorCommon) window.PhotonMutatorCommon.register(config);
    else (window.PhotonMutatorPendingConfigs = window.PhotonMutatorPendingConfigs || []).push(config);
})();