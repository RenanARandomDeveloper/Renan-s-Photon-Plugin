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
        containerType: "remove_entity_mutator_container",
        containerMessageKey: "blockly.block.photon_mutator.container",
        mutatorName: "remove_entity_mutator",
        validationExtensionName: "remove_entity_validate_inputs",
        missingValueKey: "blockly.block.photon_mutator.missing_value",
        colour: "#648c9b",
        features: [
            {
                key: "name",
                blockType: "remove_entity_mutator_name",
                labelKey: "blockly.block.photon_mutator.name",
                slots: [Slots.name()]
            },
            {
                key: "force",
                blockType: "remove_entity_mutator_force",
                labelKey: "blockly.block.photon_mutator.force",
                slots: [Slots.value("Force0", "blockly.block.photon_mutator.force", "Boolean", "logic_boolean", "BOOL", "FALSE")]
            },
            {
                key: "delay",
                blockType: "remove_entity_mutator_delay",
                labelKey: "blockly.block.photon_mutator.delay",
                slots: [Slots.value("Delay0", "blockly.block.photon_mutator.delay", "Number", "math_number", "NUM", "0")]
            }
        ]
    };

    if (window.PhotonMutatorCommon) window.PhotonMutatorCommon.register(config);
    else (window.PhotonMutatorPendingConfigs = window.PhotonMutatorPendingConfigs || []).push(config);
})();