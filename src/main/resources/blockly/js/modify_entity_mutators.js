(function () {
    "use strict";

    const config = {
        action: "modify",
        target: "entity",
        colour: "#648c9b",
        features: ["name", "offset", "rotation", "scale"]
    };

    if (window.PhotonMutatorCommon) window.PhotonMutatorCommon.register(config);
    else (window.PhotonMutatorPendingConfigs = window.PhotonMutatorPendingConfigs || []).push(config);
})();