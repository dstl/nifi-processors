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

import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.IOException;
import java.io.InputStream;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;

public class OpenOCRRequestBase64 extends OpenOCRRequest {
  private String content;

  @JsonProperty("img_base64")
  public String getContent() {
    return content;
  }

  public void setContent(String content) {
    this.content = content;
  }

  @Override
  public boolean isEmpty() {
    return content == null || content.isEmpty();
  }

  public static OpenOCRRequestBase64 fromUnencoded(InputStream inputStream) throws IOException {
    OpenOCRRequestBase64 request = new OpenOCRRequestBase64();

    byte[] byteArray = IOUtils.toByteArray(inputStream);

    if (byteArray.length > 0) request.setContent(Base64.encodeBase64String(byteArray));

    return request;
  }
}
