(function () {
    "use strict";

    const config = {
        action: "summon",
        target: "entity",
        colour: "#648c9b",
        features: ["offset", "rotation", "scale", "delay", "forcedeath", "allowmulti", "autorotate"]
    };

    if (window.PhotonMutatorCommon) window.PhotonMutatorCommon.register(config);
    else (window.PhotonMutatorPendingConfigs = window.PhotonMutatorPendingConfigs || []).push(config);
})();