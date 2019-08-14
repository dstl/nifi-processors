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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public abstract class OpenOCRRequest {
  private String engine = "tesseract";
  private Map<String, Object> engineArgs = new HashMap<>();
  private List<String> preprocessors = new ArrayList<>();

  public String getEngine() {
    return engine;
  }

  public void setEngine(String engine) {
    this.engine = engine;
  }

  @JsonProperty("engine_args")
  public Map<String, Object> getEngineArgs() {
    return engineArgs;
  }

  public void setEngineArgs(Map<String, Object> engineArgs) {
    this.engineArgs = engineArgs;
  }

  public List<String> getPreprocessors() {
    return preprocessors;
  }

  public void setPreprocessors(List<String> preprocessors) {
    this.preprocessors = preprocessors;
  }

  @JsonIgnore
  public abstract boolean isEmpty();
}
