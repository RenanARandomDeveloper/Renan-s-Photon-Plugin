(function () {
    "use strict";

    const config = {
        action: "remove",
        target: "entity",
        colour: "#648c9b",
        features: ["name", "force", "delay"]
    };

    if (window.PhotonMutatorCommon) window.PhotonMutatorCommon.register(config);
    else (window.PhotonMutatorPendingConfigs = window.PhotonMutatorPendingConfigs || []).push(config);
})();