package org.jmmo.crawler.search;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SearchVehicle extends AbstractVerticle {
    private static final Logger log = LoggerFactory.getLogger(SearchVehicle.class);

    protected String word;
    protected Boolean sensitive;
    protected Boolean whole;

    protected int directories;
    protected int searches;

    @Override
    public void start() throws Exception {
        log.debug("started");

        word = config().getString("word");
        sensitive = config().getBoolean("sensitive");
        whole = config().getBoolean("whole");

        getVertx().eventBus().consumer(SearchMessages.FOUND, message -> {
            final JsonObject messageJson = (JsonObject) message.body();

            System.out.println(messageJson.getInteger("frequency") + " " + messageJson.getString("file"));

            searches--;
            checkDone();
        });

        getVertx().eventBus().consumer(SearchMessages.SCAN_COMPLETED, message -> {
            directories--;
            checkDone();
        });

        getVertx().eventBus().consumer(SearchMessages.SCANNED_FILE, message -> {
            searches++;
            getVertx().eventBus().send(SearchMessages.FIND, new JsonObject()
                    .put("file", message.body()).put("word", word).put("sensitive", sensitive).put("whole", whole));
        });

        getVertx().eventBus().consumer(SearchMessages.SCANNED_DIRECTORY, message -> {
            directories++;
            getVertx().eventBus().send(SearchMessages.SCAN, new JsonObject().put("path", message.body()).put("fileFilter", "(?i).+\\.html"));
        });

        getVertx().eventBus().send(SearchMessages.SCANNED_DIRECTORY, config().getString("dir"));
    }

    @Override
    public void stop() throws Exception {
        log.debug("stopped");
    }

    protected void checkDone() {
        if (directories <= 0 && searches <= 0) {
            log.info("Search of the " + word + " in " + config().getString("dir") + " is done");
            getVertx().eventBus().publish(SearchMessages.DONE, word);
        }
    }
}
