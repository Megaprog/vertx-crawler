# Web Crawler

Async Web Crawler based on [Vert.x](http://vertx.io/) framework.

## Known limitations

* Only HTML files are downloading
* Only UTF-8 encoding is supported
* The option --resolveLinks is experimental and works only with anchor links (tag &lt;a>) which point the html files

## How to package it?

mvn clean package

vertx-crawler-1.0-SNAPSHOT-fat.jar file will be created at target directory

## How to run Crawl command?

    java -jar target/vertx-crawler-1.0-SNAPSHOT-fat.jar crawl [--conf=<config>] [--dir=<directory>] [--depth=<depth>] [--delay=<delay>]
                                                                 [--downloads=<downloads>] [--loaders=<loaders>] [--parsers=<parsers>] 
                                                                 [--linksToFiles=<linksToFiles>] [--storeOriginals=<storeOriginals>] url

Options and Arguments:
  
    --conf <config>                     Specifies configuration that should be
                                        provided to the verticle. <config>
                                        should reference either a text file
                                        containing a valid JSON object which
                                        represents the configuration OR be a
                                        JSON string. There is a sample config
                                        file in project root.
    --dir <directory>                   Specifies directory for downloaded
                                        files. Defaults is 'output'.  
    --depth <depth>                     Specifies how deeply crawler must dig.
                                        Defaults is 5.  
    --delay <delay>                     Specifies how many milliseconds must be
                                        delayed between requests. 
                                        Defaults is 200.  
    --downloads <downloads>             Specifies how many simultaneous
                                        downloads can be started for one loader.
                                        Defaults is 10.
    --loaders <loaders>                 Specifies how many loaders instances
                                        will be deployed. Defaults is 1.  
    --parsers <parsers>                 Specifies how many parsers instances
                                        will be deployed. Defaults is available
                                        processors.
    --resolveLinks <resolveLinks>       (Experimental) specifies would be links 
                                        with relative urls resolved to absolute 
                                        ones (otherwise they will not be 
                                        clickable). Defaults is true.
    --linksToFiles <linksToFiles>       (Experimental) specifies would be links
                                        in html documents changed to point the
                                        downloaded files. Have effect only 
                                        if --resolvedLinks is true.
                                        Defaults is false.
    --storeOriginals <storeOriginals>   Specifies would be original html
                                        documents stored after updating links or
                                        not. Defaults is false.
    <url>                               Web site url for crawling.

## How to run Find command?

    java -jar target/vertx-crawler-1.0-SNAPSHOT-fat.jar find [--dir=<directory>] [--ext=<extension>] [--finders=<finders>] [--sensitive] [--whole] word

Options and Arguments:

    --dir <directory>          Specifies directory for searching. Defaults is
                               current directory.
    --ext <extension>          Specifies file extension for searching. If is
                               present then word will be searching only in files
                               with same extension. Defaults is * means all
                               extensions.
    --finders <finders>        Specifies how many finders instances will be
                               deployed. Defaults is 2.
    --sensitive                Will the search case sensitive or not. Defaults
                               is false.
    --whole                    Search for whole word only or not. Defaults is
                               false.
    <word>                     The word for searching.

