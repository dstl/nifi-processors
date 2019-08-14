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

import java.nio.charset.StandardCharsets;
import org.apache.commons.io.IOUtils;
import org.apache.nifi.util.TestRunner;
import org.apache.nifi.util.TestRunners;
import org.junit.Before;
import org.junit.Test;
import uk.gov.dstl.machinetranslation.connector.api.utils.ConnectorUtils;

public class MachineTranslationProcessorTest {

  private TestRunner testRunner;

  @Before
  public void init() {
    testRunner = TestRunners.newTestRunner(MachineTranslationProcessor.class);
  }

  @Test
  public void testProcessor() {
    testRunner.setProperty(MachineTranslationProcessor.PROP_SOURCE_LANGUAGE.getName(), "fr");
    testRunner.setProperty(MachineTranslationProcessor.PROP_TARGET_LANGUAGE.getName(), "en");
    testRunner.setProperty(
        MachineTranslationProcessor.PROP_CONNECTOR.getName(), TestConnector.class.getName());
    testRunner.enqueue(IOUtils.toInputStream("Bonjour le monde", StandardCharsets.UTF_8));

    testRunner.run();

    testRunner.assertTransferCount(MachineTranslationProcessor.REL_SUCCESS.getName(), 1);
    testRunner.assertTransferCount(MachineTranslationProcessor.REL_FAILURE.getName(), 0);
  }

  @Test(expected = AssertionError.class) // Actually a ProcessException, but NiFi wraps it
  public void testNotSupportedLanguage() {
    testRunner.setProperty(MachineTranslationProcessor.PROP_SOURCE_LANGUAGE.getName(), "it");
    testRunner.setProperty(MachineTranslationProcessor.PROP_TARGET_LANGUAGE.getName(), "en");
    testRunner.setProperty(
        MachineTranslationProcessor.PROP_CONNECTOR.getName(), TestConnector.class.getName());
    testRunner.enqueue(IOUtils.toInputStream("Ciao mondo", StandardCharsets.UTF_8));

    testRunner.run();
  }

  @Test
  public void testAutoLanguage() {
    testRunner.setProperty(
        MachineTranslationProcessor.PROP_SOURCE_LANGUAGE.getName(), ConnectorUtils.LANGUAGE_AUTO);
    testRunner.setProperty(MachineTranslationProcessor.PROP_TARGET_LANGUAGE.getName(), "en");
    testRunner.setProperty(
        MachineTranslationProcessor.PROP_CONNECTOR.getName(), TestConnector.class.getName());
    testRunner.enqueue(IOUtils.toInputStream("Bonjour le monde", StandardCharsets.UTF_8));

    testRunner.run();

    testRunner.assertTransferCount(MachineTranslationProcessor.REL_SUCCESS.getName(), 1);
    testRunner.assertTransferCount(MachineTranslationProcessor.REL_FAILURE.getName(), 0);
  }

  @Test
  public void testConfiguration() {
    testRunner.setProperty(MachineTranslationProcessor.PROP_SOURCE_LANGUAGE.getName(), "fr");
    testRunner.setProperty(MachineTranslationProcessor.PROP_TARGET_LANGUAGE.getName(), "en");
    testRunner.setProperty(
        MachineTranslationProcessor.PROP_CONNECTOR.getName(), TestConnector.class.getName());
    testRunner.setProperty(
        MachineTranslationProcessor.PROP_CONNECTOR_CONFIG.getName(), "{\"a\":\"test\",\"b\":123}");
    testRunner.enqueue(IOUtils.toInputStream("Bonjour le monde", StandardCharsets.UTF_8));

    testRunner.run();

    testRunner.assertTransferCount(MachineTranslationProcessor.REL_SUCCESS.getName(), 1);
    testRunner.assertTransferCount(MachineTranslationProcessor.REL_FAILURE.getName(), 0);
  }

  @Test(expected = AssertionError.class)
  public void testBadConfiguration() {
    testRunner.setProperty(MachineTranslationProcessor.PROP_SOURCE_LANGUAGE.getName(), "fr");
    testRunner.setProperty(MachineTranslationProcessor.PROP_TARGET_LANGUAGE.getName(), "en");
    testRunner.setProperty(
        MachineTranslationProcessor.PROP_CONNECTOR.getName(), TestConnector.class.getName());
    testRunner.setProperty(MachineTranslationProcessor.PROP_CONNECTOR_CONFIG.getName(), "Not JSON");
    testRunner.enqueue(IOUtils.toInputStream("Bonjour le monde", StandardCharsets.UTF_8));

    testRunner.run();
  }

  @Test
  public void testFailingProcessor() {
    testRunner.setProperty(MachineTranslationProcessor.PROP_SOURCE_LANGUAGE.getName(), "fr");
    testRunner.setProperty(MachineTranslationProcessor.PROP_TARGET_LANGUAGE.getName(), "en");
    testRunner.setProperty(
        MachineTranslationProcessor.PROP_CONNECTOR.getName(), FailingTestConnector.class.getName());
    testRunner.enqueue(IOUtils.toInputStream("Bonjour le monde", StandardCharsets.UTF_8));

    testRunner.run();

    testRunner.assertTransferCount(MachineTranslationProcessor.REL_SUCCESS.getName(), 0);
    testRunner.assertTransferCount(MachineTranslationProcessor.REL_FAILURE.getName(), 1);
  }

  @Test
  public void testBadConnector() {
    testRunner.setProperty(MachineTranslationProcessor.PROP_CONNECTOR.getName(), "HelloWorld");
    testRunner.assertNotValid();

    testRunner.setProperty(
        MachineTranslationProcessor.PROP_CONNECTOR.getName(), Boolean.class.getName());
    testRunner.assertNotValid();
  }

  @Test
  public void testEmptyJson() {
    testRunner.setProperty(MachineTranslationProcessor.PROP_CONNECTOR_CONFIG.getName(), "");
    testRunner.assertNotValid();
  }
}
