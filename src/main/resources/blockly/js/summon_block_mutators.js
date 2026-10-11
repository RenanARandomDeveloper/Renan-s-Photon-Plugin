(function () {
    "use strict";

    const config = {
        action: "summon",
        target: "block",
        colour: "#9b8264",
        features: ["offset", "rotation", "scale", "delay", "forcedeath", "allowmulti", "state"]
    };

    if (window.PhotonMutatorCommon) window.PhotonMutatorCommon.register(config);
    else (window.PhotonMutatorPendingConfigs = window.PhotonMutatorPendingConfigs || []).push(config);
})();