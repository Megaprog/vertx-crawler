package org.jmmo.crawler;

import io.vertx.core.spi.launcher.DefaultCommandFactory;

public class CrawlerCommandFactory extends DefaultCommandFactory<CrawlCommand> {

    public CrawlerCommandFactory() {
        super(CrawlCommand.class);
    }
}
