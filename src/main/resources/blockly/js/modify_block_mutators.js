(function () {
    "use strict";

    const config = {
        action: "modify",
        target: "block",
        colour: "#9b8264",
        features: ["name", "offset", "rotation", "scale"]
    };

    if (window.PhotonMutatorCommon) window.PhotonMutatorCommon.register(config);
    else (window.PhotonMutatorPendingConfigs = window.PhotonMutatorPendingConfigs || []).push(config);
})();