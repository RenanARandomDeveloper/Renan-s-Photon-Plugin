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
        containerType: "data_block_mutator_container",
        containerMessageKey: "blockly.block.photon_mutator.container",
        mutatorName: "data_block_mutator",
        validationExtensionName: "data_block_validate_inputs",
        missingValueKey: "blockly.block.photon_mutator.missing_value",
        colour: "#9b8264",
        features: [
            {
                key: "name",
                blockType: "data_block_mutator_name",
                labelKey: "blockly.block.photon_mutator.name",
                slots: [Slots.name()]
            }
        ]
    };

    if (window.PhotonMutatorCommon) window.PhotonMutatorCommon.register(config);
    else (window.PhotonMutatorPendingConfigs = window.PhotonMutatorPendingConfigs || []).push(config);
})();