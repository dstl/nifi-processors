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

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import uk.gov.dstl.machinetranslation.connector.api.*;
import uk.gov.dstl.machinetranslation.connector.api.exceptions.ConnectorException;
import uk.gov.dstl.machinetranslation.connector.api.utils.ConnectorUtils;

public class TestConnector implements MTConnectorApi {
  private boolean configureCalled = false;

  @Override
  public void configure(Map<String, Object> map) {
    configureCalled = true;
  }

  @Override
  public Collection<LanguagePair> supportedLanguages() throws ConnectorException {
    if (!configureCalled) throw new ConnectorException("Configure must be called first");

    return Arrays.asList(new LanguagePair("fr", "en"), new LanguagePair("de", "en"));
  }

  @Override
  public List<LanguageDetection> identifyLanguage(String content) throws ConnectorException {
    if (!configureCalled) throw new ConnectorException("Configure must be called first");

    throw new UnsupportedOperationException("Not supported");
  }

  @Override
  public Translation translate(String sourceLanguage, String targetLanguage, String content)
      throws ConnectorException {
    if (!configureCalled) throw new ConnectorException("Configure must be called first");

    if (ConnectorUtils.LANGUAGE_AUTO.equals(sourceLanguage))
      return new Translation("fr", "Hello world");

    return new Translation(sourceLanguage, "Hello world");
  }

  @Override
  public EngineDetails queryEngine() {
    return new EngineDetails("Test Connector", ConnectorUtils.VERSION_UNKNOWN);
  }
}
