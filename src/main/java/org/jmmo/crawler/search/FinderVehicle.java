package org.jmmo.crawler.search;

import io.vertx.core.AbstractVerticle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FinderVehicle extends AbstractVerticle {
    private static final Logger log = LoggerFactory.getLogger(FinderVehicle.class);

    @Override
    public void start() throws Exception {
        log.debug("started");
    }

    @Override
    public void stop() throws Exception {
        log.debug("stopped");
    }
}
