/*
 * Copyright © 2016 Cask Data, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package co.cask.cdap.app.runtime.spark;

import co.cask.cdap.api.TaskLocalizationContext;
import co.cask.cdap.internal.app.runtime.DefaultTaskLocalizationContext;

import java.io.File;
import java.io.Serializable;
import java.util.Map;

/**
 * A {@link TaskLocalizationContext} that is {@link Serializable}.
 */
public class SparkTaskLocalizationContext extends DefaultTaskLocalizationContext implements Serializable {

  public SparkTaskLocalizationContext(Map<String, File> localizedResources) {
    super(localizedResources);
  }
}