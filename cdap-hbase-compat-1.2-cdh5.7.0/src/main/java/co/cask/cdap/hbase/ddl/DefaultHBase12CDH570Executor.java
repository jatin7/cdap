/*
 * Copyright © 2017 Cask Data, Inc.
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

package co.cask.cdap.hbase.ddl;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HColumnDescriptor;
import org.apache.hadoop.hbase.HTableDescriptor;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.io.ImmutableBytesWritable;
import org.apache.hadoop.hbase.io.compress.Compression;
import org.apache.hadoop.hbase.util.Bytes;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Implementation of the {@link HBaseDDLExecutor} for HBase version 1.2 CDH 5.7.0
 */
public class DefaultHBase12CDH570Executor extends DefaultHBaseDDLExecutor {

  public DefaultHBase12CDH570Executor(Configuration hConf) {
    super(hConf);
  }

  private HColumnDescriptor getHColumnDesciptor(ColumnFamilyDescriptor descriptor) {
    HColumnDescriptor hFamily = new HColumnDescriptor(descriptor.getName());
    hFamily.setMaxVersions(descriptor.getMaxVersions());
    hFamily.setCompressionType(Compression.Algorithm.valueOf(descriptor.getCompressionType().name()));
    hFamily.setBloomFilterType(org.apache.hadoop.hbase.regionserver.BloomType.valueOf(
      descriptor.getBloomType().name()));
    for (Map.Entry<String, String> property : descriptor.getProperties().entrySet()) {
      hFamily.setValue(property.getKey(), property.getValue());
    }
    return hFamily;
  }

  @Override
  public HTableDescriptor getHTableDescriptor(TableDescriptor descriptor) {
    TableName tableName = TableName.valueOf(descriptor.getNamespace(), descriptor.getName());
    HTableDescriptor htd = new HTableDescriptor(tableName);
    for (Map.Entry<String, ColumnFamilyDescriptor> family : descriptor.getFamilies().entrySet()) {
      htd.addFamily(getHColumnDesciptor(family.getValue()));
    }

    for (Map.Entry<String, CoprocessorDescriptor> coprocessor : descriptor.getCoprocessors().entrySet()) {
      CoprocessorDescriptor cpd = coprocessor.getValue();
      try {
        htd.addCoprocessor(cpd.getClassName(), cpd.getPath(), cpd.getPriority(), cpd.getProperties());
      } catch (IOException e) {
        LOG.error("Error adding coprocessor.", e);
      }
    }

    for (Map.Entry<String, String> property : descriptor.getProperties().entrySet()) {
      // TODO: should not add coprocessor related properties since those were already be added
      // using addCoprocessor call.
      htd.setValue(property.getKey(), property.getValue());
    }
    return htd;
  }

  @Override
  public TableDescriptor getTableDescriptor(HTableDescriptor descriptor) {
    Set<ColumnFamilyDescriptor> families = new HashSet<>();
    for (HColumnDescriptor family : descriptor.getColumnFamilies()) {
      families.add(getColumnFamilyDescriptor(family));
    }

    Set<CoprocessorDescriptor> coprocessors = new HashSet<>();
    coprocessors.addAll(CoprocessorDescriptor.getCoprocessors(descriptor).values());

    Map<String, String> properties = new HashMap<>();
    for (Map.Entry<ImmutableBytesWritable, ImmutableBytesWritable> value : descriptor.getValues().entrySet()) {
      properties.put(Bytes.toString(value.getKey().get()), Bytes.toString(value.getValue().get()));
    }

    // TODO: should add configurations as well!

    return new TableDescriptor(descriptor.getTableName().getNamespaceAsString(),
                               descriptor.getTableName().getQualifierAsString(), families, coprocessors, properties);

  }

  private ColumnFamilyDescriptor getColumnFamilyDescriptor(HColumnDescriptor descriptor) {
    String name = descriptor.getNameAsString();
    int maxVersions = descriptor.getMaxVersions();
    // TODO check this

    LOG.info("SAGAR--------- compression {}, {}, {}", descriptor.getCompression().getName(),
             descriptor.getCompressionType().getName(), descriptor.getCompressionType().getName().toUpperCase());
    ColumnFamilyDescriptor.CompressionType compressionType
      = ColumnFamilyDescriptor.CompressionType.valueOf(descriptor.getCompressionType().getName().toUpperCase());
    ColumnFamilyDescriptor.BloomType bloomType
      = ColumnFamilyDescriptor.BloomType.valueOf(descriptor.getBloomFilterType().name().toUpperCase());

    Map<String, String> properties = new HashMap<>();
    for (Map.Entry<ImmutableBytesWritable, ImmutableBytesWritable> value : descriptor.getValues().entrySet()) {
      properties.put(Bytes.toString(value.getKey().get()), Bytes.toString(value.getValue().get()));
    }
    return new ColumnFamilyDescriptor(name, maxVersions, compressionType, bloomType, properties);
  }
}


