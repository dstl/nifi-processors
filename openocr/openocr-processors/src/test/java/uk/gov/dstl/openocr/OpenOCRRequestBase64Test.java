package uk.gov.dstl.openocr;

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

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.apache.commons.io.IOUtils;
import org.junit.Test;

public class OpenOCRRequestBase64Test {

  @Test
  public void testGetterSetter() {
    OpenOCRRequestBase64 o = new OpenOCRRequestBase64();

    assertNotNull(o.getEngine());
    assertNotEquals("Test engine", o.getEngine());
    o.setEngine("Test engine");
    assertEquals("Test engine", o.getEngine());

    assertNull(o.getContent());
    o.setContent("Test content");
    assertEquals("Test content", o.getContent());
  }

  @Test
  public void testIsEmpty() {
    OpenOCRRequestBase64 o = new OpenOCRRequestBase64();

    assertTrue(o.isEmpty());

    o.setContent("Hello world");
    assertFalse(o.isEmpty());
  }

  @Test
  public void testFromUnencoded() {
    try (InputStream is = IOUtils.toInputStream("Hello", StandardCharsets.UTF_8)) {
      OpenOCRRequestBase64 o = OpenOCRRequestBase64.fromUnencoded(is);

      assertEquals("SGVsbG8=", o.getContent());
    } catch (IOException e) {
    }
  }
}
