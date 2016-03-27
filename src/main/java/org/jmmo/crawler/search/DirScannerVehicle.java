package org.jmmo.crawler.search;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.regex.Pattern;

public class DirScannerVehicle extends AbstractVerticle {
    private static final Logger log = LoggerFactory.getLogger(DirScannerVehicle.class);

    @Override
    public void start() throws Exception {
        log.debug("started");

        getVertx().eventBus().consumer(SearchMessages.SCAN, message -> {
            log.debug("Scanning " + message.body());

            final JsonObject messageJson = (JsonObject) message.body();
            final String path = messageJson.getString("path");
            final Optional<Pattern> fileFilterOpt = Optional.ofNullable(messageJson.getString("fileFilter")).map(Pattern::compile);

            try {
                getVertx().fileSystem().readDir(path, config().getString("filter"), dirResult -> {
                    if (dirResult.failed()) {
                        log.error("Fail during reading dir " + path, dirResult.cause());
                        sendFail(path);
                        return;
                    }

                    final int[] counter = new int[1];
                    dirResult.result().forEach(fileOrDir -> {
                        getVertx().fileSystem().lprops(fileOrDir, propsResult -> {
                            if (propsResult.failed()) {
                                log.error("Fail during props retrieving for " + fileOrDir, dirResult.cause());
                            } else if (propsResult.result().isDirectory()) {
                                getVertx().eventBus().send(SearchMessages.SCANNED_DIRECTORY, fileOrDir);
                            } else if (propsResult.result().isRegularFile()) {
                                if (fileFilterOpt.map(pattern -> pattern.matcher(fileOrDir).find()).orElse(true)) {
                                    getVertx().eventBus().send(SearchMessages.SCANNED_FILE, fileOrDir);
                                } else {
                                    log.trace("File " + fileOrDir + " doesn't match regexp " + fileFilterOpt);
                                }
                            } else {
                                log.info("Found neither file nor directory " + fileOrDir);
                            }

                            if (++counter[0] == dirResult.result().size()) {
                                getVertx().eventBus().send(SearchMessages.SCAN_COMPLETED, path);
                            }
                        });
                    });
                });

            } catch (Exception e) {
                log.error("Failed to scan " + path, e);
                sendFail(path);
            }
        });
    }

    @Override
    public void stop() throws Exception {
        log.debug("stopped");
    }

    protected void sendFail(String path) {
        getVertx().eventBus().send(SearchMessages.SCAN_FAILED, path);
    }
}
