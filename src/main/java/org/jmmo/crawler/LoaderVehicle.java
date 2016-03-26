package org.jmmo.crawler;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.file.OpenOptions;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.streams.Pump;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class LoaderVehicle extends AbstractVerticle {
    public static final int RETRY_DELAY = 3000;

    private static final Logger log = LoggerFactory.getLogger(LoaderVehicle.class);

    protected int downloads;
    protected Optional<Long> timerOpt = Optional.empty();
    protected final Deque<JsonObject> tasks = new LinkedList<>();

    @Override
    public void start() throws Exception {
        log.debug("started");

        getVertx().eventBus().consumer(CrawlMessages.DOWNLOAD, message -> {
            log.trace("Download " + message.body());

            final JsonObject messageJson = (JsonObject) message.body();

            if (timerOpt.isPresent()) {
                tasks.addFirst(messageJson);
            } else {
                startDownload(messageJson.getString("url"), messageJson.getString("file"), new ArrayList<>());
            }
        });
    }

    @Override
    public void stop() throws Exception {
        log.debug("stopped");
    }

    protected void startDownload(String url, String file, List<String> redirectsTo) {
        download(url, file, redirectsTo);
        timerOpt = Optional.of(getVertx().setPeriodic(config().getInteger("delay"), timerId -> {
            if (tasks.isEmpty()) {
                getVertx().cancelTimer(timerId);
                timerOpt = Optional.empty();
            } else {
                if (downloads <= config().getInteger("downloads")) {
                    final JsonObject task = tasks.removeLast();
                    download(task.getString("url"), task.getString("file"), new ArrayList<>());
                }
            }
        }));
    }

    protected void download(String originalUrl, String file, List<String> redirectsTo) {
        final String currentUrl = redirectsTo.isEmpty() ? originalUrl : redirectsTo.get(redirectsTo.size() - 1);

        log.debug("Downloading " + currentUrl + " to " + file + ", original=" + originalUrl + ", redirects=" + redirectsTo);

        try {
            final URL url = new URL(currentUrl);
            final int port = url.getPort() > -1 ? url.getPort() : url.getDefaultPort();

            final HttpClientOptions httpClientOptions = new HttpClientOptions();
            if ("https".equals(url.getProtocol())) {
                httpClientOptions.setSsl(true).setTrustAll(true);
            }

            downloads++;
            final HttpClientRequest request = vertx.createHttpClient(httpClientOptions).get(port, url.getHost(), url.getPath(), response -> {
                response.pause();

                log.trace("Response status: " + response.statusCode() + " " + response.statusMessage());

                final String contentType = response.getHeader("Content-Type");
                if (contentType == null || !response.getHeader("Content-Type").startsWith("text/html")) {
                    downloads--;
                    log.debug("Ignored content type " + contentType + " of " + currentUrl);
                    getVertx().eventBus().send(CrawlMessages.DOWNLOAD_FAIL, jsonResult(originalUrl, file, redirectsTo).put("content-type", contentType));
                    return;
                }

                switch (response.statusCode()) {
                    case 200: {
                        getVertx().executeBlocking(future -> {
                            try {
                                Files.createDirectories(Paths.get(file).getParent());
                                future.complete();
                            } catch (IOException e) {
                                log.error("Failed to create directories to " + file, e);
                                future.fail(e);
                            }
                        }, dirCreated -> getVertx().fileSystem().open(file, new OpenOptions(), opened -> {
                            if (opened.failed()) {
                                downloads--;
                                log.error("Cannot open the file " + file, opened.cause());
                                return;
                            }

                            response.resume();
                            Pump.pump(response, opened.result()).start();
                            response.endHandler(endEvent -> {
                                downloads--;
                                opened.result().close(closed ->
                                        getVertx().eventBus().send(CrawlMessages.DOWNLOADED, jsonResult(originalUrl, file, redirectsTo)));
                            });
                        }));

                        break;
                    }
                    case 301:
                    case 302:
                    case 303: {
                        downloads--;

                        final String redirect = response.getHeader("location");
                        log.debug("Redirected " + currentUrl + " to " + redirect);

                        if (originalUrl.equals(redirect) || redirectsTo.contains(redirect)) {
                            log.warn("Cyclic redirects from " + currentUrl + ", original url " + originalUrl + ", redirects " + redirectsTo);
                            getVertx().eventBus().send(CrawlMessages.DOWNLOAD_FAIL, jsonResult(originalUrl, file, redirectsTo).put("cyclic", redirect));
                        } else {
                            redirectsTo.add(redirect);
                            download(originalUrl, file, redirectsTo);
                        }

                        break;
                    }
                    default: {
                        downloads--;
                        log.info("Failed to load " + currentUrl + " because of status code " + response.statusCode());
                        getVertx().eventBus().send(CrawlMessages.DOWNLOAD_FAIL, jsonResult(originalUrl, file, redirectsTo).put("status", response.statusCode()));
                    }
                }
            });

            request.exceptionHandler(e -> {
                downloads--;
                log.warn("Cannot establish http connection to " + currentUrl, e);

                getVertx().eventBus().send(CrawlMessages.DOWNLOAD_FAIL, jsonResult(originalUrl, file, redirectsTo).put("error", e.toString()));
            });

            request.end();

        } catch (MalformedURLException e) {
            log.error("Bad url " + currentUrl, e);
            getVertx().eventBus().send(CrawlMessages.DOWNLOAD_FAIL, jsonResult(originalUrl, file, redirectsTo).put("error", e.toString()));
        }
    }

    protected JsonObject jsonResult(String url, String file, List<String> redirectsTo) {
        return new JsonObject()
                .put("url", url)
                .put("file", file)
                .put("redirects", new JsonArray(redirectsTo));
    }
}
