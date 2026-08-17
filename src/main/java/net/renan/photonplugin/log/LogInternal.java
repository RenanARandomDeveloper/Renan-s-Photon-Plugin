package net.renan.photonplugin.log;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

final class LogInternal {

    static final Logger LOGGER = LogManager.getLogger("Photon");

    private LogInternal() {
    }
}
