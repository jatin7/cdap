/*
 * Copyright © 2015 Cask Data, Inc.
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

package co.cask.cdap.data2.util.hbase;

import co.cask.cdap.common.conf.CConfiguration;
import co.cask.cdap.common.conf.Constants;
import org.junit.Assert;
import org.junit.Test;

public class HTable10NameConverterTest {
  @Test
  public void testGetSysConfigTablePrefix() throws Exception {
    CConfiguration cConf = CConfiguration.create();
    String tablePrefix = cConf.get(Constants.Dataset.TABLE_PREFIX);
    HTableNameConverter hBaseNameConversionUtil = new HTableNameConverter();
    Assert.assertEquals(tablePrefix + "_system:", hBaseNameConversionUtil.getSysConfigTablePrefix(tablePrefix));
  }
}
