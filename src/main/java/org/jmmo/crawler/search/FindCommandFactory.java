package org.jmmo.crawler.search;

import io.vertx.core.spi.launcher.DefaultCommandFactory;

public class FindCommandFactory extends DefaultCommandFactory<FindCommand> {

    public FindCommandFactory() {
        super(FindCommand.class);
    }
}
