package com.continuuity.data.dataset;

import com.continuuity.api.data.OperationException;
import com.continuuity.api.data.OperationResult;

import java.util.Map;

public class KeyValueTable extends DataSet {

  static final byte[] KEY_COLUMN = { 'k', 'e', 'y' };

  private Table table;

  public KeyValueTable(String name) {
    super(name);
    this.table = new Table("kv_" + name);
  }

  public KeyValueTable(DataSetSpecification spec) throws OperationException {
    super(spec);
    this.table = new Table(spec.getSpecificationFor("kv_" + this.getName()));
  }

  @Override
  public DataSetSpecification.Builder configure() {
    return new DataSetSpecification.Builder(this).
        dataset(this.table.configure());
  }

  public byte[] read(byte[] key) throws OperationException {
    OperationResult<Map<byte[], byte[]>> result =
        this.table.read(new Table.Read(key, KEY_COLUMN));
    if (result.isEmpty()) {
      return null;
    } else {
      return result.getValue().get(KEY_COLUMN);
    }
  }

  public void write(byte[] key, byte[] value) throws OperationException {
    this.table.exec(new Table.Write(key, KEY_COLUMN, value));
  }

  public void stage(byte[] key, byte[] value) throws OperationException {
    this.table.stage(new Table.Write(key, KEY_COLUMN, value));
  }
}
