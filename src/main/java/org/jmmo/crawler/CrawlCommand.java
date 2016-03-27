package org.jmmo.crawler;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.cli.CLIException;
import io.vertx.core.cli.annotations.*;
import io.vertx.core.impl.launcher.commands.BareCommand;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.stream.Collectors;

@Name("crawl")
@Summary("Start web crawling")
public class CrawlCommand extends BareCommand {

    private String url;
    private String configuration;
    private Integer loaders;
    private Integer parsers;
    private Integer downloads;
    private String directory;
    private Integer depth;
    private Integer delay;
    private Boolean updateLinks;
    private Boolean storeOriginals;

    @Argument(index = 0, argName = "url", required = false)
    @Description("Web site url for crawling.")
    public void setUrl(String url) {
        this.url = url;
    }

    @Option(longName = "conf", argName = "config")
    @Description("Specifies configuration that should be provided to the verticle. <config> should reference either a " +
            "text file containing a valid JSON object which represents the configuration OR be a JSON string.")
    public void setConfig(String configuration) {
        this.configuration = configuration;
    }

    @Option(longName = "dir", argName = "directory")
    @Description("Specifies directory for downloaded files. Defaults is 'output'.")
    public void setDirectory(String directory) {
        this.directory = directory;
    }

    @Option(longName = "loaders", argName = "loaders")
    @Description("Specifies how many loaders instances will be deployed. Defaults is 1.")
    public void setLoaders(int loaders) {
        this.loaders = loaders;
    }

    @Option(longName = "downloads", argName = "downloads")
    @Description("Specifies how many simultaneous downloads can be started for one loader. Defaults is 10.")
    public void setDownloads(int downloads) {
        this.downloads = downloads;
    }

    @Option(longName = "delay", argName = "delay")
    @Description("Specifies how many milliseconds must be delayed between requests. Defaults is 200.")
    public void setDelay(int delay) {
        this.delay = delay;
    }

    @Option(longName = "parsers", argName = "parsers")
    @Description("Specifies how many parsers instances will be deployed. Defaults is available processors.")
    public void setParsers(int parsers) {
        this.parsers = parsers;
    }

    @Option(longName = "depth", argName = "depth")
    @Description("Specifies how deeply crawler must dig. Defaults is 5.")
    public void setDepth(int depth) {
        this.depth = depth;
    }

    @Option(longName = "updateLinks", shortName = "ul", argName = "updateLinks")
    @Description("Specifies would be links in html documents updated to point to downloaded pages. Defaults is false.")
    public void setUpdateLinks(String updateLinks) {
        this.updateLinks = Boolean.valueOf(updateLinks);
    }

    @Option(longName = "storeOriginals", shortName = "so", argName = "storeOriginals")
    @Description("Specifies would be original html documents stored after updating links or not. Defaults is false.")
    public void setStoreOriginals(String storeOriginals) {
        this.storeOriginals = Boolean.valueOf(storeOriginals);
    }

    @Override
    public boolean isClustered() {
        return false;
    }

    @Override
    public boolean getHA() {
        return false;
    }

    private static final Logger log = LoggerFactory.getLogger(CrawlCommand.class);

    @Override
    public void run() throws CLIException {
        super.run();

        final JsonObject conf = getConfiguration();
        putNotNull(conf, "url", url);
        putNotNull(conf, "dir", directory, "output");
        putNotNull(conf, "loaders", loaders, 1);
        putNotNull(conf, "downloads", downloads, 10);
        putNotNull(conf, "delay", delay, 200);
        putNotNull(conf, "parsers", parsers, Runtime.getRuntime().availableProcessors());
        putNotNull(conf, "depth", depth, 5);
        putNotNull(conf, "updateLinks", updateLinks, false);
        putNotNull(conf, "storeOriginals", storeOriginals, false);

        log.info("Crawler parameters:");
        log.info("Configuration file: " + configuration);
        log.info("Url: " + conf.getString("url"));
        log.info("Directory: " + conf.getString("dir"));
        log.info("Loaders: " + conf.getInteger("loaders"));
        log.info("Downloads: " + conf.getInteger("downloads"));
        log.info("Delay: " + conf.getInteger("delay"));
        log.info("Parsers: " + conf.getInteger("parsers"));
        log.info("Depth: " + conf.getInteger("depth"));
        log.info("Update links: " + conf.getBoolean("updateLinks"));
        log.info("Store originals: " + conf.getBoolean("storeOriginals"));

        try {
            Files.createDirectories(Paths.get(conf.getString("dir")));
        } catch (IOException e) {
            log.error("Wrong dir", e);
            return;
        }

        if (!conf.containsKey("url")) {
            log.error("Url is not found");
            return;
        }

        try {
            final URL url = new URL(conf.getString("url"));
            conf.put("protocol", url.getProtocol());
            conf.put("host", url.getHost());
            conf.put("port", url.getPort() > -1 ? url.getPort() : url.getDefaultPort());
        } catch (MalformedURLException e ) {
            log.error("Bad url", e);
            return;
        }

        vertx.eventBus().consumer(CrawlMessages.DONE, message -> {
            log.info("All jobs done");
            vertx.close();
        });

        vertx.deployVerticle(ParserVehicle.class.getName(), new DeploymentOptions().setConfig(conf).setInstances(conf.getInteger("parsers")).setWorker(true), ar1 -> {
            vertx.deployVerticle(LoaderVehicle.class.getName(), new DeploymentOptions().setConfig(conf).setInstances(conf.getInteger("loaders")), ar2 -> {
                vertx.deployVerticle(CrawlerVehicle.class.getName(), new DeploymentOptions().setConfig(conf));
            });
        });
    }

    protected JsonObject getConfiguration() {
        return Optional.ofNullable(configuration).map(this::readConfig).map(jsonString ->  {
            try {
                return new JsonObject(jsonString);
            } catch (DecodeException e) {
                log.error("Configuration file " + jsonString + " does not contain a valid JSON object");
            }

            return null;
        }).orElseGet(JsonObject::new);
    }

    protected String readConfig(String config) {
        try {
            return Files.lines(Paths.get(config), Charset.forName("utf-8")).collect(Collectors.joining("\n"));
        } catch (IOException e) {
            log.error("-conf option does not point to a file and is not valid JSON: " + config);
        }

        return null;
    }

    protected <T> void putNotNull(JsonObject jsonObject, String key, T value) {
        if (value != null) {
            jsonObject.put(key, value);
        }
    }

    protected <T> void putNotNull(JsonObject jsonObject, String key, T value, T defaultValue) {
        putNotNull(jsonObject, key, value);
        if (!jsonObject.containsKey(key)) {
            jsonObject.put(key, defaultValue);
        }
    }
}
