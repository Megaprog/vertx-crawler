package org.jmmo.crawler;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

public class CrawlerVehicle extends AbstractVerticle {
    public static final String HTML_EXTENSION = ".html";

    private static final Logger log = LoggerFactory.getLogger(CrawlerVehicle.class);

    protected Map<String, String> urls = new HashMap<>();
    protected Map<String, Integer> files = new HashMap<>();
    protected Map<String, Integer> names = new HashMap<>();
    protected String rootUrl;
    protected Path rootDir;
    protected int depth;
    protected int processed;

    @Override
    public void start() throws Exception {
        log.debug("started");

        rootUrl = config().getString("url");
        rootDir = Paths.get(config().getString("dir")).toAbsolutePath();
        depth = config().getInteger("depth");

        getVertx().eventBus().consumer(CrawlMessages.DOWNLOADED, message -> {
            log.trace("Downloaded " + message.body());

            final JsonObject messageJson = (JsonObject) message.body();
            final String file = messageJson.getString("file");
            final Integer level = files.get(file);
            if (level == null) {
                log.warn("Downloaded file " + file + " is not found");
                return;
            }

            final JsonArray redirects = messageJson.getJsonArray("redirects");
            if (redirects != null) {
                redirects.forEach(redirect -> urls.put((String) redirect, file));
            }

            if (level < depth) {
                getVertx().eventBus().send(CrawlMessages.PARSE, new JsonObject().put("file", file).put("level", level + 1));
            }
        });

        getVertx().eventBus().consumer(CrawlMessages.URL_FOUND, message -> {
            log.trace("Found url " + message.body());

            final JsonObject messageJson = (JsonObject) message.body();

            checkUrl(messageJson.getString("url")).ifPresent(url -> {
                final int level = messageJson.getInteger("level");
                final String storedFile = urls.get(url);
                if (storedFile != null) {
                    final Integer storedLevel = files.get(storedFile);
                    if (level < storedLevel) {
                        files.put(storedFile, level);
                        if (storedLevel >= depth && level < depth) {
                            getVertx().eventBus().send(CrawlMessages.PARSE, new JsonObject().put("file", storedFile).put("level", level + 1));
                        }
                    }
                    message.reply(fileToUrl(storedFile));
                    return;
                }

                try {
                    final String file = urlToPath(new URL(URLDecoder.decode(url, "utf-8"))).toString();
                    urls.put(url, file);
                    files.put(file, level);

                    message.reply(fileToUrl(file));
                    getVertx().eventBus().send(CrawlMessages.DOWNLOAD, new JsonObject().put("url", url).put("file", file));
                    processed++;
                } catch (MalformedURLException | UnsupportedEncodingException e) {
                    log.error("Bad url " + url, e);
                }
            });
        });

        getVertx().eventBus().consumer(CrawlMessages.PARSED, message -> {
            log.debug("Parsed " + message.body());

            if (--processed <= 0) {
                log.info("Crawling the " + rootUrl + " is done");
                getVertx().eventBus().publish(CrawlMessages.DONE, rootUrl);
            }
        });

        getVertx().eventBus().send(CrawlMessages.URL_FOUND, new JsonObject().put("url", rootUrl).put("level", 0));
    }

    @Override
    public void stop() throws Exception {
        log.debug("stopped");
    }

    protected final Pattern ulrPattern = Pattern.compile("[\u0001-\u001f<>:\"\\\\|?*\u007f]+");

    protected Path urlToPath(URL url) {
        final Path path = rootDir.resolve(ulrPattern.matcher(url.getHost() + url.getPath()).replaceAll("")).toAbsolutePath();
        return checkPath(checkExtension(path));
    }

    protected Path checkExtension(Path path) {
        final String name = path.getFileName().toString();
        final int extensionIndex = name.lastIndexOf('.');
        if (extensionIndex == -1 || !HTML_EXTENSION.equals(name.substring(extensionIndex))) {
            return path.resolveSibling(name + HTML_EXTENSION);
        }

        return path;
    }

    protected Path checkPath(Path path) {
        final String pathString = path.toString();

        if (files.containsKey(pathString)) {
            final String name = path.getFileName().toString();
            final int extensionIndex = name.lastIndexOf('.');
            final String newName;
            if (extensionIndex > -1) {
                newName = generateNewName(name.substring(0, extensionIndex), pathString) + name.substring(extensionIndex);
            } else {
                newName = generateNewName(name, pathString);
            }
            return checkPath(path.resolveSibling(newName));
        }

        return path;
    }

    protected String generateNewName(String name, String pathString) {
        final Integer count = names.get(pathString);
        final Integer newCount = count == null ? 1 : count + 1;
        names.put(pathString, newCount);

        return name + "_" + newCount;
    }

    protected Optional<String> checkUrl(String urlString) {
        try {
            new URL(urlString);
            return Optional.of(urlString);
        } catch (MalformedURLException e) {
            final String resolvedUrl;
            if (urlString.startsWith("//")) {
                resolvedUrl = config().getString("protocol") + ":" + urlString;
            } else if (urlString.startsWith("/")) {
                resolvedUrl = config().getString("protocol") + "://" + config().getString("host") + urlString;
            } else {
                resolvedUrl = config().getString("protocol") + "://" + urlString;
            }

            try {
                new URL(resolvedUrl);
                return Optional.of(resolvedUrl);
            } catch (MalformedURLException e1) {
                log.error("Bad url " + urlString, e1);
            }
        }

        return Optional.empty();
    }

    protected String fileToUrl(String file) {
        return rootDir.relativize(Paths.get(file)).toString();
    }
}
