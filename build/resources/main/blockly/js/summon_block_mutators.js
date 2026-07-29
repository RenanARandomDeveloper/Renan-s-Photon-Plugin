function summonBlockValueSlot(name, labelKey, check, defaultBlockType, defaultField, defaultValue) {
    return { name: name, kind: "value", labelKey: labelKey, check: check, default: { blockType: defaultBlockType, field: defaultField, value: defaultValue } };
}

(function (config) {
    if (window.PhotonMutatorCommon) window.PhotonMutatorCommon.register(config);
    else (window.PhotonMutatorPendingConfigs = window.PhotonMutatorPendingConfigs || []).push(config);
})({
    containerType: "summon_block_mutator_container",
    containerMessageKey: "blockly.block.photon_mutator.container",
    mutatorName: "summon_block_mutator",
    validationExtensionName: "summon_block_validate_inputs",
    missingValueKey: "blockly.block.photon_mutator.missing_value",
    colour: "#9b8264",
    features: [
        {
            key: "offset",
            blockType: "summon_block_mutator_offset",
            labelKey: "blockly.block.photon_mutator.offset",
            slots: [
                summonBlockValueSlot("OffSetX0", "blockly.block.photon_mutator.offsetx", "Number", "math_number", "NUM", "0"),
                summonBlockValueSlot("OffSetY0", "blockly.block.photon_mutator.offsety", "Number", "math_number", "NUM", "0"),
                summonBlockValueSlot("OffSetZ0", "blockly.block.photon_mutator.offsetz", "Number", "math_number", "NUM", "0")
            ]
        },
        {
            key: "rotation",
            blockType: "summon_block_mutator_rotation",
            labelKey: "blockly.block.photon_mutator.rotation",
            slots: [
                summonBlockValueSlot("RotationX0", "blockly.block.photon_mutator.rotationx", "Number", "math_number", "NUM", "0"),
                summonBlockValueSlot("RotationY0", "blockly.block.photon_mutator.rotationy", "Number", "math_number", "NUM", "0"),
                summonBlockValueSlot("RotationZ0", "blockly.block.photon_mutator.rotationz", "Number", "math_number", "NUM", "0")
            ]
        },
        {
            key: "scale",
            blockType: "summon_block_mutator_scale",
            labelKey: "blockly.block.photon_mutator.scale",
            slots: [
                summonBlockValueSlot("ScaleX0", "blockly.block.photon_mutator.scalex", "Number", "math_number", "NUM", "1"),
                summonBlockValueSlot("ScaleY0", "blockly.block.photon_mutator.scaley", "Number", "math_number", "NUM", "1"),
                summonBlockValueSlot("ScaleZ0", "blockly.block.photon_mutator.scalez", "Number", "math_number", "NUM", "1")
            ]
        },
        {
            key: "delay",
            blockType: "summon_block_mutator_delay",
            labelKey: "blockly.block.photon_mutator.delay",
            slots: [
                summonBlockValueSlot("Delay0", "blockly.block.photon_mutator.delay", "Number", "math_number", "NUM", "0")
            ]
        },
        {
            key: "forcedeath",
            blockType: "summon_block_mutator_forcedeath",
            labelKey: "blockly.block.photon_mutator.forcedeath",
            slots: [
                summonBlockValueSlot("Death0", "blockly.block.photon_mutator.forcedeath", "Boolean", "logic_boolean", "BOOL", "FALSE")
            ]
        },
        {
            key: "allowmulti",
            blockType: "summon_block_mutator_allowmulti",
            labelKey: "blockly.block.photon_mutator.allowmulti",
            slots: [
                summonBlockValueSlot("Multi0", "blockly.block.photon_mutator.allowmulti", "Boolean", "logic_boolean", "BOOL", "FALSE")
            ]
        },
        {
            key: "state",
            blockType: "summon_block_mutator_state",
            labelKey: "blockly.block.photon_mutator.state",
            slots: [
                summonBlockValueSlot("State0", "blockly.block.photon_mutator.state", "Boolean", "logic_boolean", "BOOL", "FALSE")
            ]
        }
    ]
});