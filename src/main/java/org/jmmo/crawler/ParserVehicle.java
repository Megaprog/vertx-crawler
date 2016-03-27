package org.jmmo.crawler;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.json.JsonObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ParserVehicle extends AbstractVerticle {
    public static final String BACKUP_EXTENSION = ".backup";

    private static final Logger log = LoggerFactory.getLogger(ParserVehicle.class);

    protected boolean updateLinks;
    protected boolean storeOriginals;

    @Override
    public void start() throws Exception {
        log.info("started");

        updateLinks = config().getBoolean("updateLinks");
        storeOriginals = config().getBoolean("storeOriginals");

        getVertx().eventBus().consumer(CrawlMessages.PARSE, message -> {
            log.debug("Parsing " + message.body());

            final JsonObject messageJson = (JsonObject) message.body();
            final String file = messageJson.getString("file");
            final String baseUrl = messageJson.getString("url");
            final int level = messageJson.getInteger("level");

            try {
                final Document document = Jsoup.parse(new File(file), "utf-8", baseUrl);
                final Elements links = document.select("a[href]:not([href^=#]):not([href^=javascript]):not([rel=nofollow]):not([download])");

                final int[] counter = new int[1];
                links.forEach(element -> {
                    getVertx().eventBus().send(CrawlMessages.URL_FOUND, new JsonObject()
                            .put("url", element.attr("href")).put("baseUrl", baseUrl).put("file", file).put("level", level), ar -> {
                        if (updateLinks && ar.succeeded()) {
                            final String newUrl = (String) ar.result().body();
                            if (newUrl != null) {
                                element.attr("href", newUrl);
                            }
                        }

                        if (++counter[0] == links.size()) {
                            if (updateLinks) {
                                final Path original = Paths.get(file);
                                if (storeOriginals) {
                                    final String name = original.getFileName().toString();
                                    final int extensionIndex = name.lastIndexOf(".");
                                    final Path backup = original.resolveSibling((extensionIndex > -1 ? name.substring(0, extensionIndex) : name) + BACKUP_EXTENSION);
                                    try {
                                        Files.move(original, backup);
                                    } catch (IOException e) {
                                        log.error("Cannot rename file " + original + " to " + backup, e);
                                        sendFail(file);
                                        return;
                                    }
                                }

                                try {
                                    Files.write(original, document.toString().getBytes("utf-8"));
                                } catch (IOException e) {
                                    log.error("Cannot save to file " + original, e);
                                    sendFail(file);
                                    return;
                                }
                            }

                            getVertx().eventBus().send(CrawlMessages.PARSED, file);
                        }
                    });
                });

            } catch (Exception e) {
                log.error("Exception during parsing " + file, e);
                sendFail(file);
            }
        });
    }

    @Override
    public void stop() throws Exception {
        log.info("stopped");
    }

    protected void sendFail(String file) {
        getVertx().eventBus().send(CrawlMessages.PARSE_FAIL, file);
    }
}
