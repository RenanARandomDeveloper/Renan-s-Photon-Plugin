(function (config) {
    if (window.PhotonMutatorCommon) window.PhotonMutatorCommon.register(config);
    else (window.PhotonMutatorPendingConfigs = window.PhotonMutatorPendingConfigs || []).push(config);
})({
    containerType: "remove_block_mutator_container",
    containerMessageKey: "blockly.block.photon_mutator.container",
    mutatorName: "remove_block_mutator",
    validationExtensionName: "remove_block_validate_inputs",
    missingValueKey: "blockly.block.photon_mutator.missing_value",
    colour: "#9b8264",
    features: [
        {
            key: "name",
            blockType: "remove_block_mutator_name",
            labelKey: "blockly.block.photon_mutator.name",
            slots: [
                {
                    name: "Name0",
                    kind: "field",
                    labelKey: "blockly.block.photon_mutator.name",
                    field: { type: "field_photon_fx_selector" }
                }
            ]
        },
        {
            key: "force",
            blockType: "remove_block_mutator_force",
            labelKey: "blockly.block.photon_mutator.force",
            slots: [
                { name: "Force0", kind: "value", labelKey: "blockly.block.photon_mutator.force", check: "Boolean", default: { blockType: "logic_boolean", field: "BOOL", value: "FALSE" } }
            ]
        },
        {
            key: "delay",
            blockType: "remove_block_mutator_delay",
            labelKey: "blockly.block.photon_mutator.delay",
            slots: [
                { name: "Delay0", kind: "value", labelKey: "blockly.block.photon_mutator.delay", check: "Number", default: { blockType: "math_number", field: "NUM", value: "0" } }
            ]
        }
    ]
});