(function () {
    "use strict";

    const config = {
        action: "remove",
        target: "block",
        colour: "#9b8264",
        features: ["name", "force", "delay"]
    };

    if (window.PhotonMutatorCommon) window.PhotonMutatorCommon.register(config);
    else (window.PhotonMutatorPendingConfigs = window.PhotonMutatorPendingConfigs || []).push(config);
})();