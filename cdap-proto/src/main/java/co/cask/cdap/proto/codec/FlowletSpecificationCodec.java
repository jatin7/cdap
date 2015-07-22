/*
 * Copyright © 2014 Cask Data, Inc.
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

package co.cask.cdap.proto.codec;

import co.cask.cdap.api.Resources;
import co.cask.cdap.api.data.stream.StreamSpecification;
import co.cask.cdap.api.flow.flowlet.FailurePolicy;
import co.cask.cdap.api.flow.flowlet.FlowletSpecification;
import co.cask.cdap.internal.dataset.DatasetCreationSpec;
import co.cask.cdap.internal.flowlet.DefaultFlowletSpecification;
import com.google.common.collect.Maps;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.Set;

/**
 *
 */
public final class FlowletSpecificationCodec extends AbstractSpecificationCodec<FlowletSpecification> {

  @Override
  public JsonElement serialize(FlowletSpecification src, Type typeOfSrc, JsonSerializationContext context) {
    JsonObject jsonObj = new JsonObject();

    jsonObj.add("className", new JsonPrimitive(src.getClassName()));
    jsonObj.add("name", new JsonPrimitive(src.getName()));
    jsonObj.add("description", new JsonPrimitive(src.getDescription()));
    jsonObj.add("failurePolicy", new JsonPrimitive(src.getFailurePolicy().name()));
    jsonObj.add("datasets", serializeSet(src.getDataSets(), context, String.class));
    jsonObj.add("properties", serializeMap(src.getProperties(), context, String.class));
    jsonObj.add("resources", context.serialize(src.getResources(), Resources.class));
    jsonObj.add("streams", serializeMap(src.getStreams(), context, StreamSpecification.class));
    jsonObj.add("dataSetModules", serializeMap(src.getDatasetModules(), context, String.class));
    jsonObj.add("dataSetInstances", serializeMap(src.getDatasetSpecs(), context, DatasetCreationSpec.class));

    return jsonObj;
  }

  @Override
  public FlowletSpecification deserialize(JsonElement json, Type typeOfT,
                                          JsonDeserializationContext context) throws JsonParseException {
    JsonObject jsonObj = json.getAsJsonObject();

    String className = jsonObj.get("className").getAsString();
    String name = jsonObj.get("name").getAsString();
    String description = jsonObj.get("description").getAsString();
    FailurePolicy policy = FailurePolicy.valueOf(jsonObj.get("failurePolicy").getAsString());
    Set<String> dataSets = deserializeSet(jsonObj.get("datasets"), context, String.class);
    Map<String, String> properties = deserializeMap(jsonObj.get("properties"), context, String.class);
    Resources resources = context.deserialize(jsonObj.get("resources"), Resources.class);

    JsonElement streamElement = jsonObj.get("streams");
    Map<String, StreamSpecification> streams = (streamElement == null) ?
      Maps.<String, StreamSpecification>newHashMap() : deserializeMap(streamElement, context,
                                                                      StreamSpecification.class);
    JsonElement dataSetModElement = jsonObj.get("dataSetModules");
    Map<String, String> dataSetModules = (dataSetModElement == null) ? Maps.<String, String>newHashMap() :
      deserializeMap(dataSetModElement, context, String.class);

    JsonElement dataSetInstElement = jsonObj.get("dataSetInstances");
    Map<String, DatasetCreationSpec> dataSetInstances = (dataSetInstElement == null) ?
      Maps.<String, DatasetCreationSpec>newHashMap() : deserializeMap(dataSetInstElement, context,
                                                                      DatasetCreationSpec.class);

    return new DefaultFlowletSpecification(className, name, description, policy, dataSets, properties, resources,
                                           streams, dataSetModules, dataSetInstances);
  }
}
