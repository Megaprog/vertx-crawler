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

        vertx.deployVerticle(FinderVehicle.class.getName(), new DeploymentOptions().setInstances(finders), ar -> {
            vertx.deployVerticle(SearchVehicle.class.getName(), new DeploymentOptions().setConfig(conf));
        });
    }
}
