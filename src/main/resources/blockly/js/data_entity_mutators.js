(function () {
    "use strict";

    const config = {
        action: "data",
        target: "entity",
        colour: "#648c9b",
        features: ["name"]
    };

    if (window.PhotonMutatorCommon) window.PhotonMutatorCommon.register(config);
    else (window.PhotonMutatorPendingConfigs = window.PhotonMutatorPendingConfigs || []).push(config);
})();