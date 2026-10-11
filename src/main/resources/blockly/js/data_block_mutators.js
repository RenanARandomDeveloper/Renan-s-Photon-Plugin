(function () {
    "use strict";

    const config = {
        action: "data",
        target: "block",
        colour: "#9b8264",
        features: ["name"]
    };

    if (window.PhotonMutatorCommon) window.PhotonMutatorCommon.register(config);
    else (window.PhotonMutatorPendingConfigs = window.PhotonMutatorPendingConfigs || []).push(config);
})();