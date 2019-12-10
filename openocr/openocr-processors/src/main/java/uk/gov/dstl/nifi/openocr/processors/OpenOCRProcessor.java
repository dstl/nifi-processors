package uk.gov.dstl.nifi.openocr.processors;

/*-
 * #%L
 * OpenOCR Processors
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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.*;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.components.AllowableValue;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.expression.ExpressionLanguageScope;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.processor.*;
import org.apache.nifi.processor.exception.ProcessException;
import org.apache.nifi.processor.util.StandardValidators;
import uk.gov.dstl.openocr.OpenOCRRequestBase64;

/**
 * Uses an external OpenOCR (https://github.com/tleyden/open-ocr) instance to extract text from
 * images
 */
@Tags({"ocr", "openocr", "dstl", "image", "text"})
@CapabilityDescription("Use OpenOCR to extract text from images")
public class OpenOCRProcessor extends AbstractProcessor {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  public static final AllowableValue HTTP =
      new AllowableValue(
          "http", "HTTP", "Communication with OpenOCR server will be done over HTTP");
  public static final AllowableValue HTTPS =
      new AllowableValue(
          "https", "HTTPS", "Communication with OpenOCR server will be done over HTTPS");

  public static final PropertyDescriptor PROPERTY_OPENOCR_SCHEME =
      new PropertyDescriptor.Builder()
          .name("OPENOCR_SCHEME")
          .displayName("Scheme")
          .description("Scheme for connecting to OpenOCR")
          .allowableValues(HTTP, HTTPS)
          .defaultValue(HTTP.getValue())
          .required(true)
          .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
          .build();

  public static final PropertyDescriptor PROPERTY_OPENOCR_HOST =
      new PropertyDescriptor.Builder()
          .name("OPENOCR_HOST")
          .displayName("Host")
          .description("Hostname of the OpenOCR Server")
          .defaultValue("localhost")
          .required(true)
          .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
          .build();

  public static final PropertyDescriptor PROPERTY_OPENOCR_PORT =
      new PropertyDescriptor.Builder()
          .name("OPENOCR_PORT")
          .displayName("Port")
          .description("Port of the OpenOCR Server")
          .defaultValue("9292")
          .required(true)
          .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
          .addValidator(StandardValidators.PORT_VALIDATOR)
          .build();

  public static final PropertyDescriptor PROPERTY_PREPROCESSORS =
      new PropertyDescriptor.Builder()
          .name("OPENOCR_PREPROCESSORS")
          .displayName("Pre-processors")
          .description(
              "Comma-separated list of pre-processors in the order you want them to run (e.g. stroke-width-transform)")
          .required(false)
          .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
          .build();

  public static final PropertyDescriptor PROPERTY_ENGINE_ARGS =
      new PropertyDescriptor.Builder()
          .name("OPENOCR_ENGINE_ARGS")
          .displayName("Engine Arguments")
          .description("JSON object containing additional arguments to pass to the OpenOCR engine")
          .required(false)
          .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
          .expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
          // TODO: Can we validate it's a JSON object?
          .build();

  public static final Relationship RELATIONSHIP_EXTRACTED =
      new Relationship.Builder().name("extracted").description("Text extracted by OpenOCR").build();
  public static final Relationship RELATIONSHIP_ORIGINAL_SUCCESS =
      new Relationship.Builder()
          .name("success")
          .description("The original input file after successful text extraction by OpenOCR")
          .build();
  public static final Relationship RELATIONSHIP_ORIGINAL_FAILURE =
      new Relationship.Builder()
          .name("failure")
          .description("The original input file after unsuccessful text extraction by OpenOCR")
          .build();

  private List<PropertyDescriptor> descriptors;
  private Set<Relationship> relationships;

  @Override
  protected void init(final ProcessorInitializationContext context) {
    this.descriptors =
        List.of(
            PROPERTY_OPENOCR_SCHEME,
            PROPERTY_OPENOCR_HOST,
            PROPERTY_OPENOCR_PORT,
            PROPERTY_PREPROCESSORS,
            PROPERTY_ENGINE_ARGS);

    this.relationships =
        Set.of(
            RELATIONSHIP_EXTRACTED, RELATIONSHIP_ORIGINAL_SUCCESS, RELATIONSHIP_ORIGINAL_FAILURE);
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
    // Get input
    FlowFile flowFile = session.get();
    if (flowFile == null) {
      return;
    }
    // Convert input to an OpenOCR Request
    // TODO: Handle URLs as well?
    OpenOCRRequestBase64 request;
    try (InputStream is = session.read(flowFile)) {
      request = OpenOCRRequestBase64.fromUnencoded(is);
    } catch (IOException ioe) {
      session.transfer(flowFile, RELATIONSHIP_ORIGINAL_FAILURE);
      throw new ProcessException("Unable to encode data as Base64", ioe);
    }

    // Check there's content
    if (request.isEmpty()) {
      session.transfer(flowFile, RELATIONSHIP_ORIGINAL_FAILURE);
      throw new ProcessException("Can't process empty data");
    }

    // Parse preprocessors
    if (context.getProperty(PROPERTY_PREPROCESSORS).isSet()) {
      List<String> preprocessors =
          Arrays.asList(context.getProperty(PROPERTY_PREPROCESSORS).getValue().split("\\s*,\\s*"));

      if (preprocessors.size() > 0) {
        request.setPreprocessors(preprocessors);
      }
    }

    // Parse engine arguments
    if (context.getProperty(PROPERTY_ENGINE_ARGS).isSet()) {
      try {
        // TODO: We could optimise this by only doing it when the configuration changes
        Map<String, Object> arguments =
            OBJECT_MAPPER.readValue(
                context
                    .getProperty(PROPERTY_ENGINE_ARGS)
                    .evaluateAttributeExpressions(flowFile)
                    .getValue(),
                new TypeReference<HashMap<String, Object>>() {});
        request.setEngineArgs(arguments);
      } catch (IOException e) {
        session.transfer(flowFile, RELATIONSHIP_ORIGINAL_FAILURE);
        throw new ProcessException("Can't parse engine arguments", e);
      }
    }

    // Serialize request object to JSON
    String json;
    try {
      json = OBJECT_MAPPER.writeValueAsString(request);
    } catch (JsonProcessingException e) {
      session.transfer(flowFile, RELATIONSHIP_ORIGINAL_FAILURE);
      throw new ProcessException("Could not serialize request", e);
    }

    // Send request to OpenOCR
    CloseableHttpClient httpClient = HttpClients.createDefault();
    CloseableHttpResponse response = null;

    String extracted;
    Charset charset;
    try {
      URL url =
          new URL(
              context.getProperty(PROPERTY_OPENOCR_SCHEME).getValue(),
              context.getProperty(PROPERTY_OPENOCR_HOST).getValue(),
              context.getProperty(PROPERTY_OPENOCR_PORT).asInteger(),
              "/ocr");

      HttpPost postRequest = new HttpPost(url.toURI());
      postRequest.setEntity(new StringEntity(json, ContentType.APPLICATION_JSON));

      // Check we get the expected response
      response = httpClient.execute(postRequest);
      if (response.getStatusLine().getStatusCode() != 200) {
        session.transfer(flowFile, RELATIONSHIP_ORIGINAL_FAILURE);
        throw new ProcessException(
            "OpenOCR Server responded with status code "
                + response.getStatusLine().getStatusCode()
                + " ("
                + response.getStatusLine().getReasonPhrase()
                + ")");
      }

      // Extract content from response
      HttpEntity entity = response.getEntity();

      charset = ContentType.getOrDefault(entity).getCharset();
      extracted = IOUtils.toString(entity.getContent(), charset);

    } catch (IOException e) {
      session.transfer(flowFile, RELATIONSHIP_ORIGINAL_FAILURE);
      throw new ProcessException("Could not communicate with OpenOCR Server", e);
    } catch (URISyntaxException e) {
      session.transfer(flowFile, RELATIONSHIP_ORIGINAL_FAILURE);
      throw new ProcessException("Could not parse URI", e);
    } finally {
      silentlyClose(response);
      silentlyClose(httpClient);
    }

    // Write response back to FlowFile
    FlowFile f = session.create(flowFile);

    f = session.write(f, outputStream -> IOUtils.write(extracted, outputStream, charset));

    session.transfer(f, RELATIONSHIP_EXTRACTED);
    session.transfer(flowFile, RELATIONSHIP_ORIGINAL_SUCCESS);

    session.commit();
  }

  /**
   * Silently closes a Closeable, by ignoring any exceptions thrown. Also performs a null pointer
   * check.
   */
  private void silentlyClose(Closeable c) {
    if (c == null) return;

    try {
      c.close();
    } catch (Exception e) {
      // Do nothing
    }
  }
}
