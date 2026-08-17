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
        containerType: "modify_block_mutator_container",
        containerMessageKey: "blockly.block.photon_mutator.container",
        mutatorName: "modify_block_mutator",
        validationExtensionName: "modify_block_validate_inputs",
        missingValueKey: "blockly.block.photon_mutator.missing_value",
        colour: "#9b8264",
        features: [
            {
                key: "name",
                blockType: "modify_block_mutator_name",
                labelKey: "blockly.block.photon_mutator.name",
                slots: [Slots.name()]
            },
            {
                key: "offset",
                blockType: "modify_block_mutator_offset",
                labelKey: "blockly.block.photon_mutator.offset",
                slots: Slots.vector3("OffSet", "blockly.block.photon_mutator.offset", "0")
            },
            {
                key: "rotation",
                blockType: "modify_block_mutator_rotation",
                labelKey: "blockly.block.photon_mutator.rotation",
                slots: Slots.vector3("Rotation", "blockly.block.photon_mutator.rotation", "0")
            },
            {
                key: "scale",
                blockType: "modify_block_mutator_scale",
                labelKey: "blockly.block.photon_mutator.scale",
                slots: Slots.vector3("Scale", "blockly.block.photon_mutator.scale", "1")
            }
        ]
    };

    if (window.PhotonMutatorCommon) window.PhotonMutatorCommon.register(config);
    else (window.PhotonMutatorPendingConfigs = window.PhotonMutatorPendingConfigs || []).push(config);
})();