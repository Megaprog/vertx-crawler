package org.jmmo.crawler.search;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.cli.CLIException;
import io.vertx.core.cli.annotations.*;
import io.vertx.core.impl.launcher.commands.BareCommand;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Paths;

@Name("find")
@Summary("Find the word in directory recursively")
public class FindCommand extends BareCommand {

    private String word;
    private String dir;
    private Integer finders;
    private boolean sensitive;
    private boolean whole;

    @Argument(index = 0, argName = "word", required = true)
    @Description("The word for searching.")
    public void setWord(String word) {
        this.word = word;
    }

    @Option(longName = "dir", argName = "directory")
    @Description("Specifies directory for searching. Defaults is current directory.")
    @DefaultValue(".")
    public void setDir(String dir) {
        this.dir = dir;
    }

    @Option(longName = "finders", argName = "finders")
    @Description("Specifies how many finders instances will be deployed. Defaults is 2.")
    @DefaultValue("2")
    public void setFinders(int finders) {
        this.finders = finders;
    }

    @Option(longName = "sensitive", argName = "sensitive", flag = true)
    @Description("Will the search case sensitive or not. Defaults is false.")
    @DefaultValue("false")
    public void setSensitive(boolean sensitive) {
        this.sensitive = sensitive;
    }

    @Option(longName = "whole", argName = "whole", flag = true)
    @Description("Search for whole word only or not. Defaults is false.")
    @DefaultValue("false")
    public void setWhole(boolean whole) {
        this.whole = whole;
    }

    @Override
    public boolean isClustered() {
        return false;
    }

    @Override
    public boolean getHA() {
        return false;
    }

    private static final Logger log = LoggerFactory.getLogger(FindCommand.class);

    @Override
    public void run() throws CLIException {
        super.run();

        log.info("Finder parameters:");
        log.info("Word: " + word);
        log.info("Directory: " + dir);
        log.info("Finders: " + finders);
        log.info("Sensitive: " + sensitive);
        log.info("Whole: " + whole);

        if (word.trim().isEmpty()) {
            log.error("The word for searching cannot be empty");
            return;
        }

        if (!Files.exists(Paths.get(dir))) {
            log.error("Searching directory doesn't exists");
            return;
        }

        final JsonObject conf = new JsonObject();
        conf.put("word", word);
        conf.put("dir", dir);
        conf.put("sensitive", sensitive);
        conf.put("whole", whole);

        vertx.eventBus().consumer(SearchMessages.DONE, message -> {
            log.info("All jobs done");
            vertx.close();
        });

        vertx.deployVerticle(FinderVehicle.class.getName(), new DeploymentOptions().setWorker(true).setInstances(finders), ar1 ->
            vertx.deployVerticle(DirScannerVehicle.class.getName(), ar2 ->
                vertx.deployVerticle(SearchVehicle.class.getName(), new DeploymentOptions().setConfig(conf))
            )
        );
    }
}
