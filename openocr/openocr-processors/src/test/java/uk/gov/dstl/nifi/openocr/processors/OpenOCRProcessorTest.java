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

import org.apache.nifi.processor.exception.ProcessException;
import org.apache.nifi.util.TestRunner;
import org.apache.nifi.util.TestRunners;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.integration.ClientAndServer;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertTrue;
import static org.mockserver.integration.ClientAndServer.startClientAndServer;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.StringBody.subString;
import static org.mockserver.verify.VerificationTimes.exactly;

public class OpenOCRProcessorTest {

  private TestRunner testRunner;

  private ClientAndServer mockServer;

  @Before
  public void init() {
    testRunner = TestRunners.newTestRunner(OpenOCRProcessor.class);
    mockServer = startClientAndServer(1080);
  }

  @After
  public void destroy() {
    mockServer.stop();
  }

  @Test
  public void testProcessor() {
    mockServer
        .when(request().withMethod("POST").withPath("/ocr"))
        .respond(
            response()
                .withStatusCode(200)
                .withHeader("Content-Type", "plain/text; charset=utf-8")
                .withBody("This is a test image\n\n" + "Testing, testing... 1... 2...\n" + "3..."));

    testRunner.setProperty(OpenOCRProcessor.PROPERTY_OPENOCR_PORT.getName(), "1080");
    testRunner.enqueue(OpenOCRProcessorTest.class.getResourceAsStream("ocr_test.png"));

    testRunner.run();

    mockServer.verify(request().withPath("/ocr"), exactly(1));

    testRunner.assertTransferCount(OpenOCRProcessor.RELATIONSHIP_ORIGINAL_SUCCESS.getName(), 1);
    testRunner.assertTransferCount(OpenOCRProcessor.RELATIONSHIP_EXTRACTED.getName(), 1);
    testRunner.assertTransferCount(OpenOCRProcessor.RELATIONSHIP_ORIGINAL_FAILURE.getName(), 0);
  }

  @Test
  public void testArguments() {
    mockServer
        .when(request().withMethod("POST").withPath("/ocr").withBody(subString("engine_args")))
        .respond(
            response()
                .withStatusCode(200)
                .withHeader("Content-Type", "plain/text; charset=utf-8")
                .withBody("This is a test image\n\n" + "Testing, testing... 1... 2...\n" + "3..."));

    testRunner.setProperty(OpenOCRProcessor.PROPERTY_OPENOCR_PORT.getName(), "1080");
    testRunner.setProperty(
        OpenOCRProcessor.PROPERTY_ENGINE_ARGS.getName(),
        "{\"config_vars\":{\"tessedit_char_whitelist\":\"0123456789\"},\"psm\":\"3\"}");
    testRunner.enqueue(OpenOCRProcessorTest.class.getResourceAsStream("ocr_test.png"));

    testRunner.run();

    mockServer.verify(request().withPath("/ocr"), exactly(1));

    testRunner.assertTransferCount(OpenOCRProcessor.RELATIONSHIP_ORIGINAL_SUCCESS.getName(), 1);
    testRunner.assertTransferCount(OpenOCRProcessor.RELATIONSHIP_EXTRACTED.getName(), 1);
    testRunner.assertTransferCount(OpenOCRProcessor.RELATIONSHIP_ORIGINAL_FAILURE.getName(), 0);
  }

  @Test
  public void testPreprocessors() {
    mockServer
        .when(
            request()
                .withMethod("POST")
                .withPath("/ocr")
                .withBody(
                    subString(
                        "\"preprocessors\":[\"convert-pdf\",\"stroke-width-transform\",\"mock\"]")))
        .respond(
            response()
                .withStatusCode(200)
                .withHeader("Content-Type", "plain/text; charset=utf-8")
                .withBody("This is a test image\n\n" + "Testing, testing... 1... 2...\n" + "3..."));

    testRunner.setProperty(OpenOCRProcessor.PROPERTY_OPENOCR_PORT.getName(), "1080");
    testRunner.setProperty(
        OpenOCRProcessor.PROPERTY_PREPROCESSORS.getName(),
        "convert-pdf, stroke-width-transform,mock");
    testRunner.enqueue(OpenOCRProcessorTest.class.getResourceAsStream("ocr_test.png"));

    testRunner.run();

    mockServer.verify(request().withPath("/ocr"), exactly(1));

    testRunner.assertTransferCount(OpenOCRProcessor.RELATIONSHIP_ORIGINAL_SUCCESS.getName(), 1);
    testRunner.assertTransferCount(OpenOCRProcessor.RELATIONSHIP_EXTRACTED.getName(), 1);
    testRunner.assertTransferCount(OpenOCRProcessor.RELATIONSHIP_ORIGINAL_FAILURE.getName(), 0);
  }

  @Test
  public void testInternalServerError() {
    mockServer
        .when(request().withMethod("POST").withPath("/ocr"))
        .respond(response().withStatusCode(500));

    testRunner.setProperty(OpenOCRProcessor.PROPERTY_OPENOCR_PORT.getName(), "1080");
    testRunner.enqueue(OpenOCRProcessorTest.class.getResourceAsStream("ocr_test.png"));

    try {
      testRunner.run();
    } catch (AssertionError e) {
      // NiFi wraps ProcessException in an AssertionError
      assertTrue(e.getCause().getClass().equals(ProcessException.class));
    }

    testRunner.assertAllFlowFilesTransferred(
        OpenOCRProcessor.RELATIONSHIP_ORIGINAL_FAILURE.getName());
  }

  @Test
  public void testNoServer() {
    testRunner.setProperty(
        OpenOCRProcessor.PROPERTY_OPENOCR_PORT.getName(), "10"); // Hopefully nothing on Port 10
    testRunner.enqueue(OpenOCRProcessorTest.class.getResourceAsStream("ocr_test.png"));

    try {
      testRunner.run();
    } catch (AssertionError e) {
      // NiFi wraps ProcessException in an AssertionError
      assertTrue(e.getCause().getClass().equals(ProcessException.class));
    }

    testRunner.assertAllFlowFilesTransferred(
        OpenOCRProcessor.RELATIONSHIP_ORIGINAL_FAILURE.getName());
  }

  @Test
  public void testEmptyFlowFile() {
    mockServer
        .when(request().withMethod("POST").withPath("/ocr"))
        .respond(
            response()
                .withStatusCode(200)
                .withHeader("Content-Type", "plain/text; charset=utf-8")
                .withBody("This is a test image\n\n" + "Testing, testing... 1... 2...\n" + "3..."));

    testRunner.setProperty(OpenOCRProcessor.PROPERTY_OPENOCR_PORT.getName(), "1080");
    testRunner.enqueue(new byte[0]);

    try {
      testRunner.run();
    } catch (AssertionError e) {
      // NiFi wraps ProcessException in an AssertionError
      assertTrue(e.getCause().getClass().equals(ProcessException.class));
    }

    testRunner.assertAllFlowFilesTransferred(
        OpenOCRProcessor.RELATIONSHIP_ORIGINAL_FAILURE.getName());
  }

  @Test
  public void testExpression() {
    mockServer
        .when(request().withMethod("POST").withPath("/ocr").withBody(subString("\"lang\":\"fr\"")))
        .respond(
            response()
                .withStatusCode(200)
                .withHeader("Content-Type", "plain/text; charset=utf-8")
                .withBody("This is a test image\n\n" + "Testing, testing... 1... 2...\n" + "3..."));

    testRunner.setProperty(OpenOCRProcessor.PROPERTY_OPENOCR_PORT.getName(), "1080");
    testRunner.setProperty(
        OpenOCRProcessor.PROPERTY_ENGINE_ARGS.getName(),
        "{\"lang\":\"${lang}\"}");

    Map<String, String> attributes = new HashMap<>();
    attributes.put("lang", "fr");
    testRunner.enqueue(OpenOCRProcessorTest.class.getResourceAsStream("ocr_test.png"), attributes);

    testRunner.run();

    mockServer.verify(request().withPath("/ocr"), exactly(1));

    testRunner.assertTransferCount(OpenOCRProcessor.RELATIONSHIP_ORIGINAL_SUCCESS.getName(), 1);
    testRunner.assertTransferCount(OpenOCRProcessor.RELATIONSHIP_EXTRACTED.getName(), 1);
    testRunner.assertTransferCount(OpenOCRProcessor.RELATIONSHIP_ORIGINAL_FAILURE.getName(), 0);
  }
}
