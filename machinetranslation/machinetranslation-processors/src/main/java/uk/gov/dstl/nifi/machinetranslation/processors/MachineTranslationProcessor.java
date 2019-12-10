package uk.gov.dstl.nifi.machinetranslation.processors;

/*-
 * #%L
 * Machine Translation Processors
 * %%
 * Copyright (C) 2019 Dstl
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.IOUtils;
import org.apache.nifi.annotation.behavior.RequiresInstanceClassLoading;
import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.components.ValidationResult;
import org.apache.nifi.expression.ExpressionLanguageScope;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.processor.*;
import org.apache.nifi.processor.exception.ProcessException;
import org.apache.nifi.processor.util.StandardValidators;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.dstl.machinetranslation.connector.api.LanguagePair;
import uk.gov.dstl.machinetranslation.connector.api.MTConnectorApi;
import uk.gov.dstl.machinetranslation.connector.api.Translation;
import uk.gov.dstl.machinetranslation.connector.api.exceptions.ConfigurationException;
import uk.gov.dstl.machinetranslation.connector.api.exceptions.ConnectorException;
import uk.gov.dstl.machinetranslation.connector.api.utils.ConnectorUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** NiFi processor for translating text */
@Tags({"translation", "machine translation", "dstl", "text"})
@CapabilityDescription("Translates text using a configurable Machine Translation engine")
@RequiresInstanceClassLoading
public class MachineTranslationProcessor extends AbstractProcessor {

  private static final Logger LOGGER = LoggerFactory.getLogger(MachineTranslationProcessor.class);

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final TypeReference<Map<String, Object>> MAP =
      new TypeReference<Map<String, Object>>() {};

  public static final PropertyDescriptor PROP_SOURCE_LANGUAGE =
      new PropertyDescriptor.Builder()
          .name("sourceLanguage")
          .displayName("Source Language")
          .description("The language to translate from")
          .required(true)
          .defaultValue(ConnectorUtils.LANGUAGE_AUTO)
          .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
          .expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
          .build();
  public static final PropertyDescriptor PROP_TARGET_LANGUAGE =
      new PropertyDescriptor.Builder()
          .name("targetLanguage")
          .displayName("Target Language")
          .description("The language to translate into")
          .required(true)
          .defaultValue("en")
          .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
          .expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
          .build();
  public static final PropertyDescriptor PROP_CONNECTOR =
      new PropertyDescriptor.Builder()
          .name("connector")
          .displayName("Connector Class")
          .description(
              "Fully qualified class name of the Machine Translator connector, which must be on the class path")
          .required(true)
          .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
          .addValidator(
              (subject, input, context) -> {
                try {
                  Class<?> c = Class.forName(input);

                  return new ValidationResult.Builder()
                      .subject(subject)
                      .input(input)
                      .valid(MTConnectorApi.class.isAssignableFrom(c))
                      .explanation("Connector must implement MTConnectorApi")
                      .build();
                } catch (ClassNotFoundException e) {
                  return new ValidationResult.Builder()
                      .subject(subject)
                      .input(input)
                      .valid(false)
                      .explanation("Can't find connector " + input)
                      .build();
                }
              })
          .build();
  public static final PropertyDescriptor PROP_CONNECTOR_CONFIG =
      new PropertyDescriptor.Builder()
          .name("connectorConfig")
          .displayName("Connector Configuration")
          .description("JSON object containing configuration to pass to the Connector")
          .required(false)
          .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
          .addValidator(
              ((subject, input, context) -> {
                boolean valid = true;
                try {
                  OBJECT_MAPPER.readValue(input, MAP);
                } catch (IOException e) {
                  valid = false;
                }

                return new ValidationResult.Builder()
                    .subject(subject)
                    .input(input)
                    .valid(valid)
                    .explanation("Configuration must deserialize to a Java Map")
                    .build();
              }))
          .build();

  public static final PropertyDescriptor PROP_EXTRA_RESOURCE =
      new PropertyDescriptor.Builder()
          .name("Extra Resources")
          .description("The path to one or more resources to add to the classpath")
          .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
          .dynamicallyModifiesClasspath(true)
          .build();

  public static final Relationship REL_SUCCESS =
      new Relationship.Builder().name("success").description("Successfully translated").build();
  public static final Relationship REL_FAILURE =
      new Relationship.Builder().name("failure").description("Failed to translate").build();

  private List<PropertyDescriptor> descriptors;
  private Set<Relationship> relationships;

  private MTConnectorApi connector = null;
  private String config = null;

  @Override
  protected void init(final ProcessorInitializationContext context) {
    final List<PropertyDescriptor> descriptors = new ArrayList<>();
    descriptors.add(PROP_SOURCE_LANGUAGE);
    descriptors.add(PROP_TARGET_LANGUAGE);
    descriptors.add(PROP_CONNECTOR);
    descriptors.add(PROP_CONNECTOR_CONFIG);
    descriptors.add(PROP_EXTRA_RESOURCE);
    this.descriptors = Collections.unmodifiableList(descriptors);

    final Set<Relationship> relationships = new HashSet<>();
    relationships.add(REL_SUCCESS);
    relationships.add(REL_FAILURE);
    this.relationships = Collections.unmodifiableSet(relationships);
  }

  @Override
  public Set<Relationship> getRelationships() {
    return this.relationships;
  }

  @Override
  public final List<PropertyDescriptor> getSupportedPropertyDescriptors() {
    return descriptors;
  }

  @Override
  public void onTrigger(final ProcessContext context, final ProcessSession session)
      throws ProcessException {
    // Get Flow File
    FlowFile flowFile = session.get();
    if (flowFile == null) {
      return;
    }

    String src = context.getProperty(PROP_SOURCE_LANGUAGE).evaluateAttributeExpressions(flowFile).getValue();
    String tgt = context.getProperty(PROP_TARGET_LANGUAGE).evaluateAttributeExpressions(flowFile).getValue();

    /*
     *  In NiFi, there's no event for property modification after validation, only before.
     *  So we'll check here whether they've changed, and if so update the connector as required.
     */

    String connectorClass = context.getProperty(PROP_CONNECTOR).getValue();
    boolean connectorChanged = false;
    if (connector == null || !connector.getClass().getName().equals(connectorClass)) {
      // Connector has changed
      try {
        Class<?> c = Class.forName(connectorClass);
        connector = (MTConnectorApi) c.getConstructor().newInstance();
      } catch (Exception e) {
        throw new ProcessException("Unable to instantiate new connector", e);
      }

      connectorChanged = true;
      LOGGER.info("Connector successfully changed in configuration");
    }

    String currConfig = context.getProperty(PROP_CONNECTOR_CONFIG).getValue();
    if (connectorChanged || !Objects.equals(config, currConfig)) {
      // Config has changed, or connector has and we need to configure it
      LOGGER.info("Reconfiguring connector");

      Map<String, Object> configMap;
      if (currConfig == null) {
        configMap = Collections.emptyMap();
      } else {
        try {
          configMap = OBJECT_MAPPER.readValue(currConfig, MAP);
        } catch (IOException e) {
          throw new ProcessException("Unable to parse connector configuration", e);
        }
      }

      try {
        connector.configure(configMap);
      } catch (ConfigurationException e) {
        throw new ProcessException("Unable to configure connector", e);
      }
      config = currConfig;

      // Check that the languages are supported, if supported and auto isn't set
      if (connector.queryEngine().isSupportedLanguagesSupported()) {
        LOGGER.debug("Supported Languages is enabled, checking configuration is valid");

        Collection<LanguagePair> languagePairs;
        try {
          languagePairs = connector.supportedLanguages();
        } catch (ConnectorException e) {
          throw new ProcessException("Unable to retrieve supported languages", e);
        }

        boolean matchFound;
        if (ConnectorUtils.LANGUAGE_AUTO.equals(src)) {
          matchFound = languagePairs.stream().anyMatch(lp -> lp.getTargetLanguage().equals(tgt));
        } else {
          LanguagePair configPair = new LanguagePair(src, tgt);
          matchFound = languagePairs.stream().anyMatch(lp -> lp.equals(configPair));
        }

        if (!matchFound) {
          throw new ProcessException("Requested languages aren't supported");
        }
      }
    }

    // Read content
    LOGGER.debug("Reading content from FlowFile");
    String originalContent;
    try (InputStream is = session.read(flowFile)) {
      originalContent = IOUtils.toString(is, StandardCharsets.UTF_8);
    } catch (IOException ioe) {
      session.transfer(flowFile, REL_FAILURE);
      throw new ProcessException("Unable to read flow file content", ioe);
    }

    // Perform translation
    LOGGER.debug("Performing translation");
    Translation t;
    try {
      t =
          connector.translate(
              src,
              tgt,
              originalContent);
    } catch (ConnectorException ce) {
      LOGGER.warn("Translation failed", ce);
      session.transfer(flowFile, REL_FAILURE);
      return;
    }

    // Write results back
    LOGGER.debug("Writing results back to FlowFile");
    session.write(
        flowFile,
        outputStream -> IOUtils.write(t.getContent(), outputStream, StandardCharsets.UTF_8));
    session.transfer(flowFile, REL_SUCCESS);
  }
}
