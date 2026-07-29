(function (config) {
    if (window.PhotonMutatorCommon) window.PhotonMutatorCommon.register(config);
    else (window.PhotonMutatorPendingConfigs = window.PhotonMutatorPendingConfigs || []).push(config);
})({
    containerType: "data_entity_mutator_container",
    containerMessageKey: "blockly.block.photon_mutator.container",
    mutatorName: "data_entity_mutator",
    validationExtensionName: "data_entity_validate_inputs",
    missingValueKey: "blockly.block.photon_mutator.missing_value",
    colour: "#648c9b",
    features: [
        {
            key: "name",
            blockType: "data_entity_mutator_name",
            labelKey: "blockly.block.photon_mutator.name",
            slots: [
                {
                    name: "Name0",
                    kind: "field",
                    labelKey: "blockly.block.photon_mutator.name",
                    field: { type: "field_photon_fx_selector" }
                }
            ]
        }
    ]
});