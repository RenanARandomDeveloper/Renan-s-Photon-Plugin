function summonEntityValueSlot(name, labelKey, check, defaultBlockType, defaultField, defaultValue) {
    return { name: name, kind: "value", labelKey: labelKey, check: check, default: { blockType: defaultBlockType, field: defaultField, value: defaultValue } };
}

(function (config) {
    if (window.PhotonMutatorCommon) window.PhotonMutatorCommon.register(config);
    else (window.PhotonMutatorPendingConfigs = window.PhotonMutatorPendingConfigs || []).push(config);
})({
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
            slots: [
                summonEntityValueSlot("OffSetX0", "blockly.block.photon_mutator.offsetx", "Number", "math_number", "NUM", "0"),
                summonEntityValueSlot("OffSetY0", "blockly.block.photon_mutator.offsety", "Number", "math_number", "NUM", "0"),
                summonEntityValueSlot("OffSetZ0", "blockly.block.photon_mutator.offsetz", "Number", "math_number", "NUM", "0")
            ]
        },
        {
            key: "rotation",
            blockType: "summon_entity_mutator_rotation",
            labelKey: "blockly.block.photon_mutator.rotation",
            slots: [
                summonEntityValueSlot("RotationX0", "blockly.block.photon_mutator.rotationx", "Number", "math_number", "NUM", "0"),
                summonEntityValueSlot("RotationY0", "blockly.block.photon_mutator.rotationy", "Number", "math_number", "NUM", "0"),
                summonEntityValueSlot("RotationZ0", "blockly.block.photon_mutator.rotationz", "Number", "math_number", "NUM", "0")
            ]
        },
        {
            key: "scale",
            blockType: "summon_entity_mutator_scale",
            labelKey: "blockly.block.photon_mutator.scale",
            slots: [
                summonEntityValueSlot("ScaleX0", "blockly.block.photon_mutator.scalex", "Number", "math_number", "NUM", "1"),
                summonEntityValueSlot("ScaleY0", "blockly.block.photon_mutator.scaley", "Number", "math_number", "NUM", "1"),
                summonEntityValueSlot("ScaleZ0", "blockly.block.photon_mutator.scalez", "Number", "math_number", "NUM", "1")
            ]
        },
        {
            key: "delay",
            blockType: "summon_entity_mutator_delay",
            labelKey: "blockly.block.photon_mutator.delay",
            slots: [
                summonEntityValueSlot("Delay0", "blockly.block.photon_mutator.delay", "Number", "math_number", "NUM", "0")
            ]
        },
        {
            key: "forcedeath",
            blockType: "summon_entity_mutator_forcedeath",
            labelKey: "blockly.block.photon_mutator.forcedeath",
            slots: [
                summonEntityValueSlot("Death0", "blockly.block.photon_mutator.forcedeath", "Boolean", "logic_boolean", "BOOL", "FALSE")
            ]
        },
        {
            key: "allowmulti",
            blockType: "summon_entity_mutator_allowmulti",
            labelKey: "blockly.block.photon_mutator.allowmulti",
            slots: [
                summonEntityValueSlot("Multi0", "blockly.block.photon_mutator.allowmulti", "Boolean", "logic_boolean", "BOOL", "FALSE")
            ]
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
});