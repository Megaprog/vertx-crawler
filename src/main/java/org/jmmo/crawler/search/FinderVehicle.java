package org.jmmo.crawler.search;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FinderVehicle extends AbstractVerticle {
    private static final Logger log = LoggerFactory.getLogger(FinderVehicle.class);

    @Override
    public void start() throws Exception {
        log.debug("started");

        getVertx().eventBus().consumer(SearchMessages.FIND, message -> {
            log.debug("Find " + message.body());

            final JsonObject messageJson = (JsonObject) message.body();
            final Path file = Paths.get(messageJson.getString("file"));

            if (!Files.exists(file)) {
                log.error("File " + file + " is not found");
                sendFail(file);
                return;
            }

            final Pattern pattern = patternFor(messageJson.getString("word"), messageJson.getBoolean("sensitive", false), messageJson.getBoolean("whole", false));

            try {
                final int sum = Files.lines(file).mapToInt(s -> {
                    int count = 0;

                    final Matcher matcher = pattern.matcher(s);
                    while (matcher.find()) {
                        count++;
                    }

                    return count;
                }).sum();

                getVertx().eventBus().send(SearchMessages.FOUND, new JsonObject().put("file", file.toString()).put("count", sum));

            } catch (IOException e) {
                log.error("Cannot read form " + file);
                sendFail(file);
            }
        });
    }

    @Override
    public void stop() throws Exception {
        log.debug("stopped");
    }

    protected Pattern patternFor(String word, boolean sensitive, boolean whole) {
        String pattern = Pattern.quote(word);
        int flags = 0;

        if (whole) {
            pattern = "\\b" + pattern + "\\b";
        }

        if (!sensitive) {
            flags |= Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
        }

        return Pattern.compile(pattern, flags);
    }

    protected void sendFail(Path file) {
        getVertx().eventBus().send(SearchMessages.FIND_FAILED, file.toString());
    }
}
