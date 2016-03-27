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
    protected Map<String, String> baseUrls = new HashMap<>();
    protected Map<String, Integer> files = new HashMap<>();
    protected Map<String, Integer> names = new HashMap<>();
    protected String rootUrl;
    protected Path rootDir;
    protected int depth;
    protected int processed;

    @Override
    public void start() throws Exception {
        log.info("Crawl started");

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

            final String fromUrl;
            final JsonArray redirects = messageJson.getJsonArray("redirects");
            if (redirects != null && !redirects.isEmpty()) {
                redirects.forEach(redirect -> urls.put((String) redirect, file));
                fromUrl = redirects.getString(redirects.size() - 1);
            } else {
                fromUrl = messageJson.getString("url");
            }

            baseUrls.put(file, fromUrl);

            if (level < depth) {
                getVertx().eventBus().send(CrawlMessages.PARSE, new JsonObject().put("file", file).put("url", fromUrl).put("level", level + 1));
            } else {
                log.debug(messageJson.getString("url") + " reach level " + level + " and will not to be parsed");
                processedUrl();
            }
        });

        getVertx().eventBus().consumer(CrawlMessages.URL_FOUND, message -> {
            log.trace("Found url " + message.body());

            final JsonObject messageJson = (JsonObject) message.body();
            final String baseUrl = messageJson.getString("baseUrl");
            final String baseFile = messageJson.getString("file");
            final Optional<String> urlOpt = checkUrl(messageJson.getString("url"), baseUrl);

            urlOpt.ifPresent(url -> {
                final int level = messageJson.getInteger("level");
                final String storedFile = urls.get(url);
                if (storedFile != null) {
                    final Integer storedLevel = files.get(storedFile);
                    if (level < storedLevel) {
                        files.put(storedFile, level);
                        if (storedLevel >= depth && level < depth) {
                            processed++;
                            getVertx().eventBus().send(CrawlMessages.PARSE, new JsonObject()
                                    .put("file", storedFile).put("url", baseUrls.get(storedFile)).put("level", level + 1));
                        }
                    }

                    message.reply(fileToUrl(storedFile, baseFile));
                } else {

                    urlToPath(url).map(Path::toString).ifPresent(file -> {
                        urls.put(url, file);
                        files.put(file, level);

                        message.reply(fileToUrl(file, baseFile));
                        processed++;
                        getVertx().eventBus().send(CrawlMessages.DOWNLOAD, new JsonObject().put("url", url).put("file", file));
                    });
                }
            });

            if (!urlOpt.isPresent()) {
                message.reply(null);
            }
        });

        getVertx().eventBus().consumer(CrawlMessages.PARSED, message -> {
            log.trace("Parsed " + message.body());
            processedUrl();
        });

        getVertx().eventBus().consumer(CrawlMessages.DOWNLOAD_FAILED, message -> {
            log.trace("Download fail " + message.body());
            processedUrl();
        });

        getVertx().eventBus().consumer(CrawlMessages.PARSE_FAILED, message -> {
            log.trace("Parse fail " + message.body());
            processedUrl();
        });

        getVertx().eventBus().send(CrawlMessages.URL_FOUND, new JsonObject().put("url", rootUrl).put("level", 0));
    }

    @Override
    public void stop() throws Exception {
        log.debug("stopped");
    }

    protected final Pattern ulrPattern = Pattern.compile("[\u0001-\u001f<>:\"\\\\|?*\u007f]+");

    protected Optional<Path> urlToPath(String urlString) {
        if (urlString == null) {
            return Optional.empty();
        }

        try {
            return Optional.of(urlToPath(new URL(URLDecoder.decode(urlString, "utf-8"))));
        } catch (MalformedURLException | UnsupportedEncodingException e) {
            log.error("Bad url " + urlString, e);
            return Optional.empty();
        }
    }

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

    protected Optional<String> checkUrl(String urlString, String baseUrlString) {
        URL url;
        try {
            url = new URL(urlString);
        } catch (MalformedURLException e) {
            String resolvedUrl;
            try {
                final URL baseUrl = new URL(baseUrlString);
                if (urlString.startsWith("//")) {
                    resolvedUrl = baseUrl.getProtocol() + ":" + urlString;
                } else if (urlString.startsWith("/")) {
                    resolvedUrl = baseUrl.getProtocol() + "://" + baseUrl.getHost() + urlString;
                } else {
                    final String basePath = baseUrl.getPath();
                    final int slashIndex = basePath.lastIndexOf("/");
                    resolvedUrl = baseUrl.getProtocol() + "://" + baseUrl.getHost() + (slashIndex == -1 ? "" : basePath.substring(0, slashIndex)) + "/" + urlString;
                }
            } catch (MalformedURLException e1) {
                if (urlString.startsWith("//")) {
                    resolvedUrl = config().getString("protocol") + ":" + urlString;
                } else if (urlString.startsWith("/")) {
                    resolvedUrl = config().getString("protocol") + "://" + config().getString("host") + urlString;
                } else {
                    resolvedUrl = config().getString("protocol") + "://" + config().getString("host") + "/" + urlString;
                }
            }

            try {
                url = new URL(resolvedUrl);
            } catch (MalformedURLException e2) {
                log.warn("Bad url " + urlString, e2);
                return Optional.empty();
            }
        }

        if (!"http".equals(url.getProtocol()) && !"https".equals(url.getProtocol())) {
            log.info("Unsupported protocol " + url.getProtocol() + " in url " + url + " ignore it");
            return Optional.empty();
        }

        return Optional.of(url.toString());
    }

    protected String fileToUrl(String file, String baseFile) {
        if (baseFile == null) {
            return rootDir.relativize(Paths.get(file)).toString();
        }

        return Paths.get(baseFile).getParent().relativize(Paths.get(file)).toString();
    }

    protected void processedUrl() {
        if (--processed <= 0) {
            log.info("Crawling the " + rootUrl + " is done");
            getVertx().eventBus().publish(CrawlMessages.DONE, rootUrl);
        }
    }
}
