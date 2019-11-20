# NiFi Machine Translation Processor

This NiFi processor performs machine translation on FlowFiles, using a configurable Machine Translation engine.
Succesfully translated FlowFiles are passed to the `success` relation,
whereas FlowFiles which fail to translate are passed to the `failure` relation. 

## Building

This processor can be built by calling

    mvn clean package

in the top level directory. The resultant NAR file will be saved in `machinetranslation-nar/target`.

## Configuration

To configure the processor, you must set the `Connector` property to the fully qualified name of the Java connector,
which must be on the classpath, you wish to use.
Additional classes/JARs can be added to the classpath with the `Extra Resources` property, which can be pointed at a folder or individual file.
For instance:

    Connector = uk.gov.dstl.machinetranslation.connector.remedi.RemediConnector
    Extra Resources = ~/machinetranslation/remedi-1.0-shaded.jar

You can optionally provide a JSON object containing configuration to be passed to the connector.
For instance, for the RemediConnector above you might pass

    {
      "preProcessingServer": "ws://localhost:9080",
      "translationServer": "ws://localhost:9090",
      "postProcessingServer": "ws://localhost:9080"
    } 

## Tutorial

For more information, refer to the `tutorial/` directory for a walkthrough of how to build, install and use the processor.