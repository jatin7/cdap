package com.continuuity.data.operation.ttqueue;

import com.continuuity.common.conf.CConfiguration;
import com.continuuity.common.io.BinaryDecoder;
import com.continuuity.common.io.BinaryEncoder;
import com.continuuity.data.operation.executor.ReadPointer;
import com.continuuity.data.operation.ttqueue.TTQueueNewOnVCTable.DequeueEntry;
import com.continuuity.data.operation.ttqueue.TTQueueNewOnVCTable.DequeuedEntrySet;
import com.continuuity.data.operation.ttqueue.TTQueueNewOnVCTable.TransientWorkingSet;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.apache.hadoop.hbase.util.Bytes;
import org.junit.Ignore;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 *
 */
public abstract class TestTTQueueNew extends TestTTQueue {

  private static final int MAX_CRASH_DEQUEUE_TRIES = 10;

  protected void updateCConfiguration(CConfiguration conf) {
    conf.setLong(TTQueueNewOnVCTable.TTQUEUE_EVICT_INTERVAL_SECS, 0); // Setting evict interval to be zero seconds for testing, so that evictions can be asserted immediately in tests.
    conf.setInt(TTQueueNewOnVCTable.TTQUEUE_MAX_CRASH_DEQUEUE_TRIES, MAX_CRASH_DEQUEUE_TRIES);
  }

  // Test DequeueEntry
  @Test
  public void testDequeueEntryEncode() throws Exception {
    DequeueEntry expectedEntry = new DequeueEntry(1, 2);

    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    expectedEntry.encode(new BinaryEncoder(bos));
    byte[] encodedValue = bos.toByteArray();

    DequeueEntry actualEntry = DequeueEntry.decode(new BinaryDecoder(new ByteArrayInputStream(encodedValue)));
    assertEquals(expectedEntry.getEntryId(), actualEntry.getEntryId());
    assertEquals(expectedEntry.getTries(), actualEntry.getTries());
    assertEquals(expectedEntry, actualEntry);
  }

  @Test
  public void testDequeueEntryEquals() throws Exception {
    // DequeueEntry is only compared using entryId, tries is ignored
    assertEquals(new DequeueEntry(12, 10), new DequeueEntry(12));
    assertEquals(new DequeueEntry(12, 10), new DequeueEntry(12, 10));
    assertEquals(new DequeueEntry(12, 10), new DequeueEntry(12, 15));

    assertNotEquals(new DequeueEntry(12, 10), new DequeueEntry(13, 10));
    assertNotEquals(new DequeueEntry(12, 10), new DequeueEntry(13, 11));
  }

  @Test
  public void testDequeueEntryCompare() throws Exception {
    // DequeueEntry is compared only on entryId, tries is ignored
    SortedSet<DequeueEntry> sortedSet = new TreeSet<DequeueEntry>();
    sortedSet.add(new DequeueEntry(5, 1));
    sortedSet.add(new DequeueEntry(2, 3));
    sortedSet.add(new DequeueEntry(1, 2));
    sortedSet.add(new DequeueEntry(3, 2));
    sortedSet.add(new DequeueEntry(4, 3));
    sortedSet.add(new DequeueEntry(4, 5));
    sortedSet.add(new DequeueEntry(0, 2));

    int expected = 0;
    for(Iterator<DequeueEntry> iterator = sortedSet.iterator(); iterator.hasNext(); ) {
      assertEquals(expected++, iterator.next().getEntryId());
    }
  }

  @Test
  public void testQueueEntrySetEncode() throws Exception {
    final int MAX = 10;
    DequeuedEntrySet expectedEntrySet = new DequeuedEntrySet();
    for(int i = 0; i < MAX; ++i) {
      expectedEntrySet.add(new DequeueEntry(i, i % 2));
    }

    assertEquals(MAX, expectedEntrySet.size());

    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    expectedEntrySet.encode(new BinaryEncoder(bos));
    byte[] encodedValue = bos.toByteArray();

    DequeuedEntrySet actualEntrySet = DequeuedEntrySet.decode(new BinaryDecoder(new ByteArrayInputStream(encodedValue)));
    assertEquals(expectedEntrySet.size(), actualEntrySet.size());
    for(int i = 0; i < MAX; ++i) {
      DequeueEntry expectedEntry = expectedEntrySet.min();
      expectedEntrySet.remove(expectedEntry.getEntryId());

      DequeueEntry actualEntry = actualEntrySet.min();
      actualEntrySet.remove(actualEntry.getEntryId());

      assertEquals(expectedEntry.getEntryId(), actualEntry.getEntryId());
      assertEquals(expectedEntry.getTries(), actualEntry.getTries());
    }
  }

  @Test
  public void testQueueEntrySet() throws Exception {
    final int MAX = 10;
    DequeuedEntrySet entrySet = new DequeuedEntrySet();
    List<Long> expectedEntryIds = Lists.newArrayListWithCapacity(MAX);
    List<DequeueEntry> expectedEntryList = Lists.newArrayListWithCapacity(MAX);
    Set<Long> expectedDroppedEntries = Sets.newHashSetWithExpectedSize(MAX);

    for(int i = 0; i < MAX; ++i) {
      entrySet.add(new DequeueEntry(i, i %2));
      expectedEntryIds.add((long) i);
      expectedEntryList.add(new DequeueEntry(i, i % 2));

      if(i % 2 == 1) {
        expectedDroppedEntries.add((long) i);
      }
    }

    // Verify created lists
    assertEquals(MAX, entrySet.size());
    assertEquals(MAX, expectedEntryIds.size());
    assertEquals(MAX, expectedEntryList.size());
    assertEquals(MAX/2, expectedDroppedEntries.size());

    // Verify QueueEntrySet
    assertEquals(expectedEntryIds, entrySet.getEntryIds());
    assertEquals(expectedEntryList, entrySet.getEntryList());

    Set<Long> actualDroppedEntries = entrySet.startNewTry(1);
    assertEquals(expectedDroppedEntries, actualDroppedEntries);

    List<Long> actualRemainingEntries = Lists.newArrayListWithCapacity(MAX);
    for(int i = 0; i < MAX; ++i) {
      if(i % 2 == 0) {
        actualRemainingEntries.add((long) i);
      }
    }
    assertEquals(actualRemainingEntries, entrySet.getEntryIds());
  }

  @Test
  public void testWorkingEntryList() {
    final int MAX = 10;
    TTQueueNewOnVCTable.TransientWorkingSet transientWorkingSet = new TransientWorkingSet(Lists.<Long>newArrayList(), Collections.EMPTY_MAP);
    assertFalse(transientWorkingSet.hasNext());

    List<Long> workingSet = Lists.newArrayList();
    Map<Long, byte[]> cache = Maps.newHashMap();
    for(long i = 0; i < MAX; ++i) {
      workingSet.add(i);
      cache.put(i, Bytes.toBytes(i));
    }
    transientWorkingSet = new TransientWorkingSet(workingSet, cache);

    for(int i = 0; i < MAX; ++i) {
      assertTrue(transientWorkingSet.hasNext());
      assertEquals(new DequeueEntry(i), transientWorkingSet.peekNext());
      assertEquals(new DequeueEntry(i), transientWorkingSet.next());
    }
    assertFalse(transientWorkingSet.hasNext());
  }

  @Test
  public void testReconfigPartitionInstance() throws Exception {
    final TTQueueNewOnVCTable.ReconfigPartitionInstance reconfigPartitionInstance1 =
      new TTQueueNewOnVCTable.ReconfigPartitionInstance(3, 100L);

    final TTQueueNewOnVCTable.ReconfigPartitionInstance reconfigPartitionInstance2 =
      new TTQueueNewOnVCTable.ReconfigPartitionInstance(2, 100L);

    // Verify equals
    assertEquals(reconfigPartitionInstance1, reconfigPartitionInstance1);
    assertNotEquals(reconfigPartitionInstance1, reconfigPartitionInstance2);

    // Encode to bytes
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    reconfigPartitionInstance1.encode(new BinaryEncoder(bos));
    byte[] bytes = bos.toByteArray();

    // Decode from bytes
    TTQueueNewOnVCTable.ReconfigPartitionInstance actual =
      TTQueueNewOnVCTable.ReconfigPartitionInstance.decode(new BinaryDecoder(new ByteArrayInputStream(bytes)));

    // Verify
    assertEquals(reconfigPartitionInstance1, actual);
  }

  @Test
  public void testReconfigPartitionerEncode() throws Exception {
    TTQueueNewOnVCTable.ReconfigPartitioner partitioner1 =
      new TTQueueNewOnVCTable.ReconfigPartitioner(3, QueuePartitioner.PartitionerType.HASH);
    partitioner1.add(0, 5);
    partitioner1.add(1, 10);
    partitioner1.add(2, 12);

    TTQueueNewOnVCTable.ReconfigPartitioner partitioner2 =
      new TTQueueNewOnVCTable.ReconfigPartitioner(3, QueuePartitioner.PartitionerType.HASH);
    partitioner2.add(0, 5);
    partitioner2.add(1, 15);
    partitioner2.add(2, 12);

    // Verify equals
    assertEquals(partitioner1, partitioner1);
    assertNotEquals(partitioner1, partitioner2);

    // Encode to bytes
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    partitioner1.encode(new BinaryEncoder(bos));
    byte[] bytes = bos.toByteArray();

    // Decode from bytes
    TTQueueNewOnVCTable.ReconfigPartitioner actual =
      TTQueueNewOnVCTable.ReconfigPartitioner.decode(new BinaryDecoder(new ByteArrayInputStream(bytes)));

    // Verify
    assertEquals(partitioner1, actual);
  }

  @Test
  public void testReconfigPartitionerEmit1() throws Exception {
    // Partition
    // Entries        :1  2  3  4  5  6  7  8  9  10  11  12
    // Consumers Ack  :      0           1  2
    // Partition      :1  2  0  1  2  0  1  2  0  1   2   0

    int groupSize = 3;
    int lastEntry = 12;

    long zeroAck = 3L;
    long oneAck = 7L;
    long twoAck = 8L;

    TTQueueNewOnVCTable.ReconfigPartitioner partitioner =
      new TTQueueNewOnVCTable.ReconfigPartitioner(groupSize, QueuePartitioner.PartitionerType.ROUND_ROBIN);
    partitioner.add(0, zeroAck);
    partitioner.add(1, oneAck);
    partitioner.add(2, twoAck);

    verifyTestReconfigPartitionerEmit(lastEntry, ImmutableSet.of(1L, 2L, 3L, 4L, 5L, 7L, 8L), partitioner);
  }

  @Test
  public void testReconfigPartitionerEmit2() throws Exception {
    // Partition
    // Entries        :1  2  3  4  5  6  7  8  9  10  11  12
    // Consumers Ack  :1  2  0
    // Partition      :1  2  0  1  2  0  1  2  0  1   2   0

    int groupSize = 3;
    int lastEntry = 12;

    long zeroAck = 3L;
    long oneAck = 1L;
    long twoAck = 2L;

    TTQueueNewOnVCTable.ReconfigPartitioner partitioner =
      new TTQueueNewOnVCTable.ReconfigPartitioner(groupSize, QueuePartitioner.PartitionerType.ROUND_ROBIN);
    partitioner.add(0, zeroAck);
    partitioner.add(1, oneAck);
    partitioner.add(2, twoAck);

    verifyTestReconfigPartitionerEmit(lastEntry, ImmutableSet.of(1L, 2L, 3L), partitioner);
  }

  @Test
  public void testReconfigPartitionerEmit3() throws Exception {
    // Partition
    // Entries        :1  2  3  4  5  6  7  8  9  10  11  12
    // Consumers Ack  :1  2                               0
    // Partition      :1  2  0  1  2  0  1  2  0  1   2   0

    int groupSize = 3;
    int lastEntry = 12;

    long zeroAck = 12L;
    long oneAck = 1L;
    long twoAck = 2L;

    TTQueueNewOnVCTable.ReconfigPartitioner partitioner =
      new TTQueueNewOnVCTable.ReconfigPartitioner(groupSize, QueuePartitioner.PartitionerType.ROUND_ROBIN);
    partitioner.add(0, zeroAck);
    partitioner.add(1, oneAck);
    partitioner.add(2, twoAck);

    verifyTestReconfigPartitionerEmit(lastEntry, ImmutableSet.of(1L, 2L, 3L, 6L, 9L, 12L), partitioner);
  }

  @Test
  public void testReconfigPartitionerEmit4() throws Exception {
    // Partition
    // Entries        :1  2  3  4  5  6  7  8  9  10  11  12
    // Consumers Ack  :                           1   2   0
    // Partition      :1  2  0  1  2  0  1  2  0  1   2   0

    int groupSize = 3;
    int lastEntry = 12;

    long zeroAck = 12L;
    long oneAck = 10L;
    long twoAck = 11L;

    TTQueueNewOnVCTable.ReconfigPartitioner partitioner =
      new TTQueueNewOnVCTable.ReconfigPartitioner(groupSize, QueuePartitioner.PartitionerType.ROUND_ROBIN);
    partitioner.add(0, zeroAck);
    partitioner.add(1, oneAck);
    partitioner.add(2, twoAck);

    verifyTestReconfigPartitionerEmit(lastEntry, ImmutableSet.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 11L, 12L),
                                      partitioner);
  }

  @Test
  public void testReconfigPartitionersListEmit1() throws Exception {
    List<TTQueueNewOnVCTable.ReconfigPartitioner> partitionerList = Lists.newArrayList();
    // Partition 1
    // Entries        :1  2  3  4  5  6  7  8  9  10  11  12
    // Consumers Ack  :      0           1  2
    // Partition      :1  2  0  1  2  0  1  2  0  1   2   0

    int groupSize = 3;
    int lastEntry = 12;

    long zeroAck = 3L;
    long oneAck = 7L;
    long twoAck = 8L;

    TTQueueNewOnVCTable.ReconfigPartitioner partitioner =
      new TTQueueNewOnVCTable.ReconfigPartitioner(groupSize, QueuePartitioner.PartitionerType.ROUND_ROBIN);
    partitioner.add(0, zeroAck);
    partitioner.add(1, oneAck);
    partitioner.add(2, twoAck);

    partitionerList.add(partitioner);
    Set<Long> expectedAckedEntries1 = ImmutableSet.of(1L, 2L, 3L, 4L, 5L, 7L, 8L);

    // Partition 2
    // Entries        :1  2  3  4  5  6  7  8  9  10  11  12  13  14  15  16  17  18
    // Consumers Ack  :            1     3        2       0
    // Partition      :1  2  3  0  1  2  3  0  1  2   3   0   1   2   3   0   1   2

    groupSize = 4;
    lastEntry = 18;

    zeroAck = 12L;
    oneAck = 5L;
    twoAck = 10L;
    long threeAck = 7L;

    partitioner =
      new TTQueueNewOnVCTable.ReconfigPartitioner(groupSize, QueuePartitioner.PartitionerType.ROUND_ROBIN);
    partitioner.add(0, zeroAck);
    partitioner.add(1, oneAck);
    partitioner.add(2, twoAck);
    partitioner.add(3, threeAck);

    partitionerList.add(partitioner);
    Set<Long> expectedAckedEntries2 = ImmutableSet.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 10L, 12L);

    verifyTestReconfigPartitionerEmit(lastEntry, Sets.union(expectedAckedEntries1, expectedAckedEntries2),
                                      new TTQueueNewOnVCTable.ReconfigPartitionersList(partitionerList));
  }

  private void verifyTestReconfigPartitionerEmit(long queueSize, Set<Long> ackedEntries,
                                                 QueuePartitioner partitioner) {
    int groupSize = -1; // will be ignored
    int instanceId = -2; // will be ignored
    for(long entryId = 1; entryId <= queueSize; ++entryId) {
      if(ackedEntries.contains(entryId)) {
//        System.out.println("Not Emit:" + entryId);
        assertFalse("Not Emit:" + entryId, partitioner.shouldEmit(groupSize, instanceId, entryId));
      } else {
//        System.out.println("Emit:" + entryId);
        assertTrue("Emit:" + entryId, partitioner.shouldEmit(groupSize, instanceId, entryId));
      }
    }
  }

  @Test
  public void testClaimedEntry() {
    TTQueueNewOnVCTable.ClaimedEntry claimedEntry = TTQueueNewOnVCTable.ClaimedEntry.INVALID_CLAIMED_ENTRY;
    for(int i = 0; i < 10; ++i) {
      assertFalse(claimedEntry.isValid());
      claimedEntry = claimedEntry.move(1);
    }
    assertFalse(claimedEntry.isValid());

    claimedEntry = new TTQueueNewOnVCTable.ClaimedEntry(0, 5);
    for(int i = 0; i < 6; ++i) {
      assertTrue(claimedEntry.isValid());
      claimedEntry = claimedEntry.move(claimedEntry.getBegin() + 1);
    }
    assertFalse(claimedEntry.isValid());

    try {
      claimedEntry = new TTQueueNewOnVCTable.ClaimedEntry(4, 2);
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      // Expected
    }

    try {
      claimedEntry = new TTQueueNewOnVCTable.ClaimedEntry(TTQueueNewOnVCTable.INVALID_ENTRY_ID, 2);
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      // Expected
    }

    try {
      claimedEntry = new TTQueueNewOnVCTable.ClaimedEntry(3, TTQueueNewOnVCTable.INVALID_ENTRY_ID);
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      // Expected
    }
  }

  @Test
  public void testClaimedEntryListMove() {
    TTQueueNewOnVCTable.ClaimedEntryList claimedEntryList = new TTQueueNewOnVCTable.ClaimedEntryList();
    for(int i = 0; i < 10; ++i) {
      assertFalse(claimedEntryList.getClaimedEntry().isValid());
      claimedEntryList.moveForwardTo(1);
    }
    assertFalse(claimedEntryList.getClaimedEntry().isValid());

    claimedEntryList.add(1, 5);
    verifyClaimedEntryListIncrementMove(claimedEntryList, 1, 5);
    assertFalse(claimedEntryList.getClaimedEntry().isValid());

    claimedEntryList.add(2, 9);
    claimedEntryList.add(0, 20);
    verifyClaimedEntryListIncrementMove(claimedEntryList, 2, 9);
    verifyClaimedEntryListIncrementMove(claimedEntryList, 0, 20);
    assertFalse(claimedEntryList.getClaimedEntry().isValid());

    claimedEntryList.add(TTQueueNewOnVCTable.INVALID_ENTRY_ID, TTQueueNewOnVCTable.INVALID_ENTRY_ID);
    claimedEntryList.add(6, 6);
    claimedEntryList.add(2, 30);
    claimedEntryList.add(TTQueueNewOnVCTable.INVALID_ENTRY_ID, TTQueueNewOnVCTable.INVALID_ENTRY_ID);
    claimedEntryList.add(3, 10);
    claimedEntryList.add(4, 20);
    verifyClaimedEntryListIncrementMove(claimedEntryList, 6, 6);
    verifyClaimedEntryListIncrementMove(claimedEntryList, 2, 30);
    verifyClaimedEntryListIncrementMove(claimedEntryList, 3, 10);
    verifyClaimedEntryListIncrementMove(claimedEntryList, 4, 20);
    assertFalse(claimedEntryList.getClaimedEntry().isValid());

    claimedEntryList.add(5, 10);
    claimedEntryList.add(1, 3);
    assertTrue(claimedEntryList.getClaimedEntry().isValid());
    assertEquals(5, claimedEntryList.getClaimedEntry().getBegin());
    assertEquals(10, claimedEntryList.getClaimedEntry().getEnd());

    claimedEntryList.moveForwardTo(5);
    assertTrue(claimedEntryList.getClaimedEntry().isValid());
    assertEquals(5, claimedEntryList.getClaimedEntry().getBegin());
    assertEquals(10, claimedEntryList.getClaimedEntry().getEnd());

    claimedEntryList.moveForwardTo(8);
    assertTrue(claimedEntryList.getClaimedEntry().isValid());
    assertEquals(8, claimedEntryList.getClaimedEntry().getBegin());
    assertEquals(10, claimedEntryList.getClaimedEntry().getEnd());

    claimedEntryList.moveForwardTo(20);
    assertTrue(claimedEntryList.getClaimedEntry().isValid());
    assertEquals(1, claimedEntryList.getClaimedEntry().getBegin());
    assertEquals(3, claimedEntryList.getClaimedEntry().getEnd());

    try {
      claimedEntryList.moveForwardTo(0);
      fail("Exception should be thrown here");
    } catch (IllegalArgumentException e) {
      // Expected
    }

    claimedEntryList.moveForwardTo(4);
    assertFalse(claimedEntryList.getClaimedEntry().isValid());
  }

  @Test
  public void testClaimedEntryListAddAll() throws Exception {
    TTQueueNewOnVCTable.ClaimedEntryList claimedEntryList1 = new TTQueueNewOnVCTable.ClaimedEntryList();
    claimedEntryList1.add(3, 5);
    claimedEntryList1.add(15, 20);
    claimedEntryList1.add(36, 41);

    TTQueueNewOnVCTable.ClaimedEntryList claimedEntryList2 = new TTQueueNewOnVCTable.ClaimedEntryList();
    claimedEntryList2.add(1, 2);
    claimedEntryList2.add(10, 14);
    claimedEntryList2.add(45, 50);

    claimedEntryList1.addAll(claimedEntryList2);

    verifyClaimedEntryListIncrementMove(claimedEntryList1, 3, 5);
    verifyClaimedEntryListIncrementMove(claimedEntryList1, 15, 20);
    verifyClaimedEntryListIncrementMove(claimedEntryList1, 36, 41);
    verifyClaimedEntryListIncrementMove(claimedEntryList1, 1, 2);
    verifyClaimedEntryListIncrementMove(claimedEntryList1, 10, 14);
    verifyClaimedEntryListIncrementMove(claimedEntryList1, 45, 50);
    assertFalse(claimedEntryList1.getClaimedEntry().isValid());
  }

  private void verifyClaimedEntryListIncrementMove(TTQueueNewOnVCTable.ClaimedEntryList claimedEntryList, long begin, long end) {
    assertTrue(claimedEntryList.getClaimedEntry().isValid());
    assertEquals(begin, claimedEntryList.getClaimedEntry().getBegin());
    assertEquals(end, claimedEntryList.getClaimedEntry().getEnd());

    for(long i = begin; i <= end; ++i, claimedEntryList.moveForwardTo(claimedEntryList.getClaimedEntry().getBegin() + 1)) {
      assertTrue(claimedEntryList.getClaimedEntry().isValid());
      assertEquals(i, claimedEntryList.getClaimedEntry().getBegin());
      assertEquals(end, claimedEntryList.getClaimedEntry().getEnd());
    }
  }

  @Test
  public void testClaimedEntryListEncode() throws Exception {
    TTQueueNewOnVCTable.ClaimedEntryList claimedEntryList = new TTQueueNewOnVCTable.ClaimedEntryList();
    verifyClaimedEntryListEncode(claimedEntryList);

    claimedEntryList.add(1, 5);
    verifyClaimedEntryListEncode(claimedEntryList);

    claimedEntryList.add(TTQueueNewOnVCTable.INVALID_ENTRY_ID, TTQueueNewOnVCTable.INVALID_ENTRY_ID);
    claimedEntryList.add(6, 6);
    claimedEntryList.add(2, 30);
    claimedEntryList.add(TTQueueNewOnVCTable.INVALID_ENTRY_ID, TTQueueNewOnVCTable.INVALID_ENTRY_ID);
    claimedEntryList.add(3, 10);
    claimedEntryList.add(4, 20);
    verifyClaimedEntryListEncode(claimedEntryList);
  }

  private void verifyClaimedEntryListEncode(TTQueueNewOnVCTable.ClaimedEntryList expected) throws Exception {
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    expected.encode(new BinaryEncoder(bos));
    byte[] bytes = bos.toByteArray();

    TTQueueNewOnVCTable.ClaimedEntryList actual = TTQueueNewOnVCTable.ClaimedEntryList.decode(
      new BinaryDecoder(new ByteArrayInputStream(bytes)));

    assertEquals(expected, actual);
  }

  @Test
  public void testClaimedEntryListCompare() {
    TTQueueNewOnVCTable.ClaimedEntryList claimedEntryList1 = new TTQueueNewOnVCTable.ClaimedEntryList();
    claimedEntryList1.add(2, 7);
    claimedEntryList1.add(8, 16);
    claimedEntryList1.add(25, 30);

    TTQueueNewOnVCTable.ClaimedEntryList claimedEntryList2 = new TTQueueNewOnVCTable.ClaimedEntryList();
    claimedEntryList2.add(3, 4);
    claimedEntryList2.add(7, 15);

    TTQueueNewOnVCTable.ClaimedEntryList claimedEntryList3 = new TTQueueNewOnVCTable.ClaimedEntryList();
    claimedEntryList3.add(3, 8);
    claimedEntryList3.add(15, 20);
    claimedEntryList3.add(22, 30);
    claimedEntryList3.add(10, 12);
    claimedEntryList3.add(31, 35);

    SortedSet<TTQueueNewOnVCTable.ClaimedEntryList> sortedSet = new TreeSet<TTQueueNewOnVCTable.ClaimedEntryList>();
    sortedSet.add(claimedEntryList1);
    sortedSet.add(claimedEntryList2);
    sortedSet.add(claimedEntryList3);

    assertEquals(claimedEntryList2, sortedSet.first());
    sortedSet.remove(claimedEntryList2);
    assertEquals(claimedEntryList1, sortedSet.first());
    sortedSet.remove(claimedEntryList1);
    assertEquals(claimedEntryList3, sortedSet.first());
    sortedSet.remove(claimedEntryList3);
    assertTrue(sortedSet.isEmpty());
  }

  @Test
  public void testClaimedEntryEncode() throws Exception{
    verifyClaimedEntryEncode(TTQueueNewOnVCTable.ClaimedEntry.INVALID_CLAIMED_ENTRY);
    verifyClaimedEntryEncode(new TTQueueNewOnVCTable.ClaimedEntry(2, 15));
  }

  private void verifyClaimedEntryEncode(TTQueueNewOnVCTable.ClaimedEntry expected) throws Exception {
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    expected.encode(new BinaryEncoder(bos));
    byte[] bytes = bos.toByteArray();

    TTQueueNewOnVCTable.ClaimedEntry actual =
      TTQueueNewOnVCTable.ClaimedEntry.decode(new BinaryDecoder(new ByteArrayInputStream(bytes)));

    assertEquals(expected, actual);
  }

/*  @Override
  @Test
  public void testEvictOnAck_ThreeGroups() throws Exception {
    // Note: for now only consumer with consumerId 0 and groupId 0 can run the evict.
    TTQueue queue = createQueue();
    final boolean singleEntry = true;
    long dirtyVersion = getDirtyWriteVersion();
    ReadPointer dirtyReadPointer = getDirtyPointer();

    QueueConfig config = new QueueConfig(QueuePartitioner.PartitionerType.FIFO, singleEntry);
    QueueConsumer consumer1 = new QueueConsumer(0, 0, 1, config);
    QueueConsumer consumer2 = new QueueConsumer(0, 1, 1, config);
    QueueConsumer consumer3 = new QueueConsumer(0, 2, 1, config);

    // enable evict-on-ack for 3 groups
    int numGroups = 3;

    // enqueue 10 things
    for (int i=0; i<10; i++) {
      queue.enqueue(new QueueEntry(Bytes.toBytes(i)), dirtyVersion);
    }

    // Create consumers for checking if eviction happened
    QueueConsumer consumerCheck9thPos = new QueueConsumer(0, 3, 1, config);
    QueueConsumer consumerCheck10thPos = new QueueConsumer(0, 4, 1, config);
    // Move the consumers to 9th and 10 pos
    for(int i = 0; i < 8; i++) {
      DequeueResult result = queue.dequeue(consumerCheck9thPos, dirtyReadPointer);
      assertEquals(i, Bytes.toInt(result.getEntry().getData()));
      queue.ack(result.getEntryPointer(), consumerCheck9thPos, dirtyReadPointer);
      queue.finalize(result.getEntryPointer(), consumerCheck9thPos, -1, dirtyReadPointer.getMaximum()); // No evict
    }
    for(int i = 0; i < 9; i++) {
      DequeueResult result = queue.dequeue(consumerCheck10thPos, dirtyReadPointer);
      assertEquals(i, Bytes.toInt(result.getEntry().getData()));
      queue.ack(result.getEntryPointer(), consumerCheck10thPos, dirtyReadPointer);
      queue.finalize(result.getEntryPointer(), consumerCheck10thPos, -1, dirtyReadPointer.getMaximum()); // No evict
    }


    // dequeue/ack/finalize 8 things w/ group1 and numGroups=3
    for (int i=0; i<8; i++) {
      DequeueResult result =
        queue.dequeue(consumer1, dirtyReadPointer);
      assertEquals(i, Bytes.toInt(result.getEntry().getData()));
      queue.ack(result.getEntryPointer(), consumer1, dirtyReadPointer);
      queue.finalize(result.getEntryPointer(), consumer1, numGroups, dirtyReadPointer.getMaximum());
    }

    // dequeue is not empty, as 9th and 10th entries is still available
    assertFalse(
      queue.dequeue(consumer1, dirtyReadPointer).isEmpty());

    // dequeue with consumer2 still has entries (expected)
    assertFalse(
      queue.dequeue(consumer2, dirtyReadPointer).isEmpty());

    // dequeue everything with consumer2
    for (int i=0; i<10; i++) {
      DequeueResult result =
        queue.dequeue(consumer2, dirtyReadPointer);
      assertEquals(i, Bytes.toInt(result.getEntry().getData()));
      queue.ack(result.getEntryPointer(), consumer2, dirtyReadPointer);
      queue.finalize(result.getEntryPointer(), consumer2, numGroups, dirtyReadPointer.getMaximum());
    }

    // dequeue is empty
    assertTrue(
      queue.dequeue(consumer2, dirtyReadPointer).isEmpty());

    // dequeue with consumer3 still has entries (expected)
    assertFalse(
      queue.dequeue(consumer3, dirtyReadPointer).isEmpty());

    // dequeue everything consumer3
    for (int i=0; i<10; i++) {
      DequeueResult result =
        queue.dequeue(consumer3, dirtyReadPointer);
      assertEquals(i, Bytes.toInt(result.getEntry().getData()));
      queue.ack(result.getEntryPointer(), consumer3, dirtyReadPointer);
      queue.finalize(result.getEntryPointer(), consumer3, numGroups, dirtyReadPointer.getMaximum());
    }

    // dequeue with consumer3 is empty
    assertTrue(
      queue.dequeue(consumer3, dirtyReadPointer).isEmpty());

    // Verify 9th and 10th entries are still present
    DequeueResult result = queue.dequeue(consumerCheck9thPos, dirtyReadPointer);
    assertEquals(8, Bytes.toInt(result.getEntry().getData()));
    result = queue.dequeue(consumerCheck10thPos, dirtyReadPointer);
    assertEquals(9, Bytes.toInt(result.getEntry().getData()));

    // dequeue with consumer 1, should get 8 (9th entry)
    result = queue.dequeue(consumer1, dirtyReadPointer);
    assertEquals(8, Bytes.toInt(result.getEntry().getData()));
    queue.ack(result.getEntryPointer(), consumer1, dirtyReadPointer);
    queue.finalize(result.getEntryPointer(), consumer1, numGroups, dirtyReadPointer.getMaximum());

    // now the first 9 entries should have been physically evicted!
    // since the 9th entry does not exist anymore, exception will be thrown
    try {
    result = queue.dequeue(consumerCheck9thPos, dirtyReadPointer);
    fail("Dequeue should fail");
    } catch (OperationException e) {
      assertEquals(StatusCode.INTERNAL_ERROR, e.getStatus());
      result = null;
    }
    assertNull(result);
    result = queue.dequeue(consumerCheck10thPos, dirtyReadPointer);
    assertEquals(9, Bytes.toInt(result.getEntry().getData()));

    // dequeue with consumer 1, should get 9 (10th entry)
    result = queue.dequeue(consumer1, dirtyReadPointer);
    assertEquals(9, Bytes.toInt(result.getEntry().getData()));
    queue.ack(result.getEntryPointer(), consumer1, dirtyReadPointer);
    queue.finalize(result.getEntryPointer(), consumer1, numGroups, dirtyReadPointer.getMaximum());

    // Consumer 1 should be empty now
    assertTrue(queue.dequeue(consumer1, dirtyReadPointer).isEmpty());

    // Now 10th entry should be evicted too!
    try {
    result = queue.dequeue(consumerCheck10thPos, dirtyReadPointer);
    fail("Dequeue should fail");
    } catch (OperationException e) {
      assertEquals(StatusCode.INTERNAL_ERROR, e.getStatus());
      result = null;
    }
    assertNull(result);
  } */

  @Test
  public void testSingleConsumerWithHashPartitioning() throws Exception {
    final String HASH_KEY = "hashKey";
    final boolean singleEntry = true;
    final int numQueueEntries = 88;
    final int numConsumers = 4;
    final int consumerGroupId = 0;
    TTQueue queue = createQueue();
    long dirtyVersion = getDirtyWriteVersion();

    // enqueue some entries
    for (int i = 0; i < numQueueEntries; i++) {
      QueueEntry queueEntry = new QueueEntry(Bytes.toBytes("value" + i % numConsumers));
      queueEntry.addPartitioningKey(HASH_KEY, i);
      assertTrue(queue.enqueue(queueEntry, dirtyVersion).isSuccess());
    }
    // dequeue it with HASH partitioner
    QueueConfig config = new QueueConfig(QueuePartitioner.PartitionerType.HASH, singleEntry);

    QueueConsumer[] consumers = new QueueConsumer[numConsumers];
    for (int i = 0; i < numConsumers; i++) {
      consumers[i] = new QueueConsumer(i, consumerGroupId, numConsumers, "group1", HASH_KEY, config);
    }

    // dequeue and verify
    dequeuePartitionedEntries(queue, consumers, numConsumers, numQueueEntries);

    // enqueue some more entries
    for (int i = numQueueEntries; i < numQueueEntries * 2; i++) {
      QueueEntry queueEntry = new QueueEntry(Bytes.toBytes("value" + i % numConsumers));
      queueEntry.addPartitioningKey(HASH_KEY, i);
      assertTrue(queue.enqueue(queueEntry, dirtyVersion).isSuccess());
    }
    // dequeue and verify
    dequeuePartitionedEntries(queue, consumers, numConsumers, numQueueEntries);
  }

  @Test
  public void testSingleConsumerWithRoundRobinPartitioning() throws Exception {
    final boolean singleEntry = true;
    final int numQueueEntries = 88;
    final int numConsumers = 4;
    final int consumerGroupId = 0;
    TTQueue queue = createQueue();
    long dirtyVersion = getDirtyWriteVersion();

    // enqueue some entries
    for (int i = 0; i < numQueueEntries; i++) {
      QueueEntry queueEntry = new QueueEntry(Bytes.toBytes("value" + (i + 1) % numConsumers));
      assertTrue(queue.enqueue(queueEntry, dirtyVersion).isSuccess());
    }

    // dequeue it with ROUND_ROBIN partitioner
    QueueConfig config = new QueueConfig(QueuePartitioner.PartitionerType.ROUND_ROBIN, singleEntry);

    QueueConsumer[] consumers = new QueueConsumer[numConsumers];
    for (int i = 0; i < numConsumers; i++) {
      consumers[i] = new QueueConsumer(i, consumerGroupId, numConsumers, "group1", config);
    }

    // dequeue and verify
    dequeuePartitionedEntries(queue, consumers, numConsumers, numQueueEntries);

    // enqueue some more entries
    for (int i = numQueueEntries; i < numQueueEntries * 2; i++) {
      QueueEntry queueEntry = new QueueEntry(Bytes.toBytes("value" + (i + 1) % numConsumers));
      assertTrue(queue.enqueue(queueEntry, dirtyVersion).isSuccess());
    }

    // dequeue and verify
    dequeuePartitionedEntries(queue, consumers, numConsumers, numQueueEntries);
  }

  private void dequeuePartitionedEntries(TTQueue queue, QueueConsumer[] consumers, int numConsumers, int totalEnqueues) throws Exception {
    ReadPointer dirtyReadPointer = getDirtyPointer();
    for (int i = 0; i < numConsumers; i++) {
      for (int j = 0; j < totalEnqueues / (2 * numConsumers); j++) {
        DequeueResult result = queue.dequeue(consumers[i], dirtyReadPointer);
        // verify we got something and it's the first value
        assertTrue(result.toString(), result.isSuccess());
        assertEquals("value" + i, Bytes.toString(result.getEntry().getData()));
        // dequeue again without acking, should still get first value
        result = queue.dequeue(consumers[i], dirtyReadPointer);
        assertTrue(result.isSuccess());
        assertEquals("value" + i, Bytes.toString(result.getEntry().getData()));

        // ack
        queue.ack(result.getEntryPointer(), consumers[i], dirtyReadPointer);
        queue.finalize(result.getEntryPointer(), consumers[i], -1, dirtyReadPointer.getMaximum());

        // dequeue, should get second value
        result = queue.dequeue(consumers[i], dirtyReadPointer);
        assertTrue("Consumer:" + i + " Iteration:" + j, result.isSuccess());
        assertEquals("value" + i, Bytes.toString(result.getEntry().getData()));

        // ack
        queue.ack(result.getEntryPointer(), consumers[i], dirtyReadPointer);
        queue.finalize(result.getEntryPointer(), consumers[i], -1, dirtyReadPointer.getMaximum());
      }

      // verify queue is empty
      DequeueResult result = queue.dequeue(consumers[i], dirtyReadPointer);
      assertTrue(result.isEmpty());
    }
  }

  @Test
  public void testSingleStatefulConsumerWithHashPartitioning() throws Exception {
    final String HASH_KEY = "hashKey";
    final boolean singleEntry = true;
    final int numQueueEntries = 264; // Choose a number that leaves a reminder when divided by batchSize, and be big enough so that it forms a few batches
    final int numConsumers = 4;
    final int consumerGroupId = 0;
    TTQueue queue = createQueue();
    long dirtyVersion = getDirtyWriteVersion();

    // enqueue some entries
    for (int i = 0; i < numQueueEntries; i++) {
      QueueEntry queueEntry = new QueueEntry(Bytes.toBytes(i));
      queueEntry.addPartitioningKey(HASH_KEY, i);
      assertTrue(queue.enqueue(queueEntry, dirtyVersion).isSuccess());
    }
    // dequeue it with HASH partitioner
    // TODO: test with more batch sizes
    QueueConfig config = new QueueConfig(QueuePartitioner.PartitionerType.HASH, singleEntry, 29);

    StatefulQueueConsumer[] consumers = new StatefulQueueConsumer[numConsumers];
    for (int i = 0; i < numConsumers; i++) {
      consumers[i] = new StatefulQueueConsumer(i, consumerGroupId, numConsumers, "group1", HASH_KEY, config);
    }

    // dequeue and verify
    dequeuePartitionedEntries(queue, consumers, numConsumers, numQueueEntries, 0, QueuePartitioner.PartitionerType.HASH);
    System.out.println("Round 1 dequeue done");

    // enqueue some more entries
    for (int i = numQueueEntries; i < numQueueEntries * 2; i++) {
      QueueEntry queueEntry = new QueueEntry(Bytes.toBytes(i));
      queueEntry.addPartitioningKey(HASH_KEY, i);
      assertTrue(queue.enqueue(queueEntry, dirtyVersion).isSuccess());
    }
    // dequeue and verify
    dequeuePartitionedEntries(queue, consumers, numConsumers, numQueueEntries, numQueueEntries, QueuePartitioner.PartitionerType.HASH);
    System.out.println("Round 2 dequeue done");
  }

  @Test
  public void testSingleStatefulConsumerWithRoundRobinPartitioning() throws Exception {
    final boolean singleEntry = true;
    final int numQueueEntries = 264; // Choose a number that doesn't leave a reminder when divided by batchSize, and be big enough so that it forms a few batches
    final int numConsumers = 4;
    final int consumerGroupId = 0;
    TTQueue queue = createQueue();
    long dirtyVersion = getDirtyWriteVersion();

    // enqueue some entries
    for (int i = 0; i < numQueueEntries; i++) {
      QueueEntry queueEntry = new QueueEntry(Bytes.toBytes(i + 1));
      assertTrue(queue.enqueue(queueEntry, dirtyVersion).isSuccess());
    }

    // dequeue it with ROUND_ROBIN partitioner
    // TODO: test with more batch sizes
    QueueConfig config = new QueueConfig(QueuePartitioner.PartitionerType.ROUND_ROBIN, singleEntry, 11);

    StatefulQueueConsumer[] consumers = new StatefulQueueConsumer[numConsumers];
    for (int i = 0; i < numConsumers; i++) {
      consumers[i] = new StatefulQueueConsumer(i, consumerGroupId, numConsumers, "group1", config);
    }

    // dequeue and verify
    dequeuePartitionedEntries(queue, consumers, numConsumers, numQueueEntries, 0, QueuePartitioner.PartitionerType.ROUND_ROBIN);

    // enqueue some more entries
    for (int i = numQueueEntries; i < numQueueEntries * 2; i++) {
      QueueEntry queueEntry = new QueueEntry(Bytes.toBytes(i + 1));
      assertTrue(queue.enqueue(queueEntry, dirtyVersion).isSuccess());
    }

    // dequeue and verify
    dequeuePartitionedEntries(queue, consumers, numConsumers, numQueueEntries, numQueueEntries, QueuePartitioner.PartitionerType.ROUND_ROBIN);
  }

  private void dequeuePartitionedEntries(TTQueue queue, StatefulQueueConsumer[] consumers, int numConsumers,
                                         int numQueueEntries, int startQueueEntry, QueuePartitioner.PartitionerType partitionerType) throws Exception {
    ReadPointer dirtyReadPointer = getDirtyPointer();
    for (int consumer = 0; consumer < numConsumers; consumer++) {
      for (int entry = 0; entry < numQueueEntries / (2 * numConsumers); entry++) {
        DequeueResult result = queue.dequeue(consumers[consumer], dirtyReadPointer);
        // verify we got something and it's the first value
        assertTrue(result.toString(), result.isSuccess());
        int expectedValue = startQueueEntry + consumer + (2 * entry * numConsumers);
        if(partitionerType == QueuePartitioner.PartitionerType.ROUND_ROBIN) {
          if(consumer == 0) {
            // Adjust the expected value for consumer 0
            expectedValue += numConsumers;
          }
        }
//        System.out.println(String.format("Consumer-%d entryid=%d value=%s expectedValue=%s",
//                  consumer, result.getEntryPointer().getEntryId(), Bytes.toInt(result.getEntry().getData()), expectedValue));
        assertEquals(expectedValue, Bytes.toInt(result.getEntry().getData()));
        // dequeue again without acking, should still get first value
        result = queue.dequeue(consumers[consumer], dirtyReadPointer);
        assertTrue(result.isSuccess());
        assertEquals(expectedValue, Bytes.toInt(result.getEntry().getData()));

        // ack
        queue.ack(result.getEntryPointer(), consumers[consumer], dirtyReadPointer);
        queue.finalize(result.getEntryPointer(), consumers[consumer], -1, dirtyReadPointer.getMaximum());

        // dequeue, should get second value
        result = queue.dequeue(consumers[consumer], dirtyReadPointer);
        assertTrue("Consumer:" + consumer + " Entry:" + entry, result.isSuccess());
        expectedValue += numConsumers;
//        System.out.println(String.format("Consumer-%d entryid=%d value=%s expectedValue=%s",
//                  consumer, result.getEntryPointer().getEntryId(), Bytes.toInt(result.getEntry().getData()), expectedValue));
        assertEquals(expectedValue, Bytes.toInt(result.getEntry().getData()));

        // ack
        queue.ack(result.getEntryPointer(), consumers[consumer], dirtyReadPointer);
        queue.finalize(result.getEntryPointer(), consumers[consumer], -1, dirtyReadPointer.getMaximum());
      }

      // verify queue is empty
      DequeueResult result = queue.dequeue(consumers[consumer], dirtyReadPointer);
      assertTrue(result.isEmpty());
    }
  }

  @Test
  public void testSingleStatefulConsumerWithFifo() throws Exception {
    final boolean singleEntry = true;
    final int numQueueEntries = 200;  // Make sure numQueueEntries % batchSize == 0 && numQueueEntries % numConsumers == 0
    final int numConsumers = 4;
    final int consumerGroupId = 0;
    TTQueue queue = createQueue();
    long dirtyVersion = getDirtyWriteVersion();

    // enqueue some entries
    for (int i = 0; i < numQueueEntries; i++) {
      QueueEntry queueEntry = new QueueEntry(Bytes.toBytes(i));
      assertTrue(queue.enqueue(queueEntry, dirtyVersion).isSuccess());
    }

    // dequeue it with ROUND_ROBIN partitioner
    // TODO: test with more batch sizes
    QueueConfig config = new QueueConfig(QueuePartitioner.PartitionerType.FIFO, singleEntry, 10);

    StatefulQueueConsumer[] statefulQueueConsumers = new StatefulQueueConsumer[numConsumers];
    QueueConsumer[] queueConsumers = new QueueConsumer[numConsumers];
    for (int i = 0; i < numConsumers; i++) {
      statefulQueueConsumers[i] = new StatefulQueueConsumer(i, consumerGroupId, numConsumers, "group1", config);
      queueConsumers[i] = new QueueConsumer(i, consumerGroupId, numConsumers, "group1", config);
    }

    // dequeue and verify
    dequeueFifoEntries(queue, statefulQueueConsumers, numConsumers, numQueueEntries, 0);

    // enqueue some more entries
    for (int i = numQueueEntries; i < 2 * numQueueEntries; i++) {
      QueueEntry queueEntry = new QueueEntry(Bytes.toBytes(i));
      assertTrue(queue.enqueue(queueEntry, dirtyVersion).isSuccess());
    }

    // dequeue and verify
    dequeueFifoEntries(queue, statefulQueueConsumers, numConsumers, numQueueEntries, numQueueEntries);

    // Run with stateless QueueConsumer
    for (int i = 2 * numQueueEntries; i < 3 * numQueueEntries; i++) {
      QueueEntry queueEntry = new QueueEntry(Bytes.toBytes(i));
      assertTrue(queue.enqueue(queueEntry, dirtyVersion).isSuccess());
    }

    // dequeue and verify
    dequeueFifoEntries(queue, queueConsumers, numConsumers, numQueueEntries, 2 * numQueueEntries);

  }

  private void dequeueFifoEntries(TTQueue queue, QueueConsumer[] consumers, int numConsumers,
                                  int numQueueEntries, int startQueueEntry) throws Exception {
    ReadPointer dirtyReadPointer = getDirtyPointer();
    int expectedValue = startQueueEntry;
    for (int consumer = 0; consumer < numConsumers; consumer++) {
      for (int entry = 0; entry < numQueueEntries / (2 * numConsumers); entry++, ++expectedValue) {
        DequeueResult result = queue.dequeue(consumers[consumer], dirtyReadPointer);
        // verify we got something and it's the first value
        assertTrue(String.format("Consumer=%d, entry=%d, %s", consumer, entry, result.toString()), result.isSuccess());
//        System.out.println(String.format("Consumer-%d entryid=%d value=%s expectedValue=%s",
//                  consumer, result.getEntryPointer().getEntryId(), Bytes.toInt(result.getEntry().getData()), expectedValue));
        assertEquals(expectedValue, Bytes.toInt(result.getEntry().getData()));
        // dequeue again without acking, should still get first value
        result = queue.dequeue(consumers[consumer], dirtyReadPointer);
        assertTrue(result.isSuccess());
        assertEquals(expectedValue, Bytes.toInt(result.getEntry().getData()));

        // ack
        queue.ack(result.getEntryPointer(), consumers[consumer], dirtyReadPointer);
        queue.finalize(result.getEntryPointer(), consumers[consumer], -1, dirtyReadPointer.getMaximum());

        // dequeue, should get second value
        result = queue.dequeue(consumers[consumer], dirtyReadPointer);
        assertTrue(result.isSuccess());
        ++expectedValue;
//        System.out.println(String.format("Consumer-%d entryid=%d value=%s expectedValue=%s",
//                  consumer, result.getEntryPointer().getEntryId(), Bytes.toInt(result.getEntry().getData()), expectedValue));
        assertEquals(expectedValue, Bytes.toInt(result.getEntry().getData()));

        // ack
        queue.ack(result.getEntryPointer(), consumers[consumer], dirtyReadPointer);
        queue.finalize(result.getEntryPointer(), consumers[consumer], -1, dirtyReadPointer.getMaximum());
      }
    }

    // verify queue is empty for all consumers
    for(int consumer = 0; consumer < numConsumers; ++consumer) {
      DequeueResult result = queue.dequeue(consumers[consumer], dirtyReadPointer);
      assertTrue(result.isEmpty());
    }
  }

  @Test
  public void testMaxCrashDequeueTries() throws Exception {
    TTQueue queue = createQueue();

    assertTrue(queue.enqueue(new QueueEntry(Bytes.toBytes(1)), getDirtyWriteVersion()).isSuccess());
    assertTrue(queue.enqueue(new QueueEntry(Bytes.toBytes(2)), getDirtyWriteVersion()).isSuccess());

    // dequeue it with FIFO partitioner, single entry mode
    QueueConfig config = new QueueConfig(QueuePartitioner.PartitionerType.FIFO, true);

    for(int tries = 0; tries <= MAX_CRASH_DEQUEUE_TRIES; ++tries) {
      // Simulate consumer crashing by sending in empty state every time and not acking the entry
      DequeueResult result = queue.dequeue(new StatefulQueueConsumer(0, 0, 1, "", config), getDirtyPointer());
      assertTrue(result.isSuccess());
      assertEquals(1, Bytes.toInt(result.getEntry().getData()));
    }

    // After max tries, the entry will be ignored
    StatefulQueueConsumer statefulQueueConsumer = new StatefulQueueConsumer(0, 0, 1, "", config);
    for(int tries = 0; tries <= MAX_CRASH_DEQUEUE_TRIES + 10; ++tries) {
      // No matter how many times a dequeue is repeated with state, the same entry needs to be returned
      DequeueResult result = queue.dequeue(statefulQueueConsumer, getDirtyPointer());
      assertTrue(result.isSuccess());
      assertEquals("Tries=" + tries, 2, Bytes.toInt(result.getEntry().getData()));
    }
    DequeueResult result = queue.dequeue(statefulQueueConsumer, getDirtyPointer());
    assertTrue(result.isSuccess());
    assertEquals(2, Bytes.toInt(result.getEntry().getData()));
    queue.ack(result.getEntryPointer(), statefulQueueConsumer, getDirtyPointer());

    result = queue.dequeue(statefulQueueConsumer, getDirtyPointer());
    assertTrue(result.isEmpty());
  }

  @Test
  public void testFifoReconfig() throws Exception {
    Condition condition = new Condition() {
      @Override
      public boolean check(long entryId, int groupSize, long instanceId, int hash) {
        return true;
      }
    };

    QueuePartitioner.PartitionerType partitionerType = QueuePartitioner.PartitionerType.FIFO;

    testReconfig(Lists.newArrayList(0, 3, 2), 54, 5, 6, partitionerType, condition);
    testReconfig(Lists.newArrayList(0, 3, 4, 2), 144, 5, 9, partitionerType, condition);
    testReconfig(Lists.newArrayList(0, 3, 5, 2, 1, 6, 2), 200, 5, 9, partitionerType, condition);
    testReconfig(Lists.newArrayList(0, 3, 5, 2, 1, 6, 2), 200, 9, 9, partitionerType, condition);
    testReconfig(Lists.newArrayList(0, 3, 5, 2, 1, 6, 2), 200, 9, 5, partitionerType, condition);
    testReconfig(Lists.newArrayList(0, 3, 5, 2, 1, 6, 2), 200, 9, 5, partitionerType, condition);
    testReconfig(Lists.newArrayList(0, 1, 2, 3, 4, 5, 4, 3, 2, 1), 300, 9, 5, partitionerType, condition);
  }

  @Test
  public void testRoundRobinReconfig() throws Exception {
    Condition condition = new Condition() {
      @Override
      public boolean check(long entryId, int groupSize, long instanceId, int hash) {
        return entryId % groupSize == instanceId;
      }
    };

    QueuePartitioner.PartitionerType partitionerType = QueuePartitioner.PartitionerType.ROUND_ROBIN;

    testReconfig(Lists.newArrayList(0, 3, 2), 54, 5, 6, partitionerType, condition);
    testReconfig(Lists.newArrayList(0, 3, 4, 2), 144, 5, 9, partitionerType, condition);
    testReconfig(Lists.newArrayList(0, 3, 5, 2, 1, 6, 2), 200, 5, 9, partitionerType, condition);
    testReconfig(Lists.newArrayList(0, 3, 5, 2, 1, 6, 2), 200, 9, 9, partitionerType, condition);
    testReconfig(Lists.newArrayList(0, 3, 5, 2, 1, 6, 2), 200, 9, 5, partitionerType, condition);
    testReconfig(Lists.newArrayList(0, 3, 5, 2, 1, 6, 2), 200, 9, 5, partitionerType, condition);
    testReconfig(Lists.newArrayList(0, 1, 2, 3, 4, 5, 4, 3, 2, 1), 300, 9, 5, partitionerType, condition);
  }

  @Test
  public void testHashReconfig() throws Exception {
    Condition condition = new Condition() {
      @Override
      public boolean check(long entryId, int groupSize, long instanceId, int hash) {
        return hash % groupSize == instanceId;
      }
    };

    QueuePartitioner.PartitionerType partitionerType = QueuePartitioner.PartitionerType.HASH;

    testReconfig(Lists.newArrayList(0, 3, 2), 54, 5, 6, partitionerType, condition);
    testReconfig(Lists.newArrayList(0, 3, 4, 2), 144, 5, 9, partitionerType, condition);
    testReconfig(Lists.newArrayList(0, 3, 5, 2, 1, 6, 2), 200, 5, 9, partitionerType, condition);
    testReconfig(Lists.newArrayList(0, 3, 5, 2, 1, 6, 2), 200, 9, 9, partitionerType, condition);
    testReconfig(Lists.newArrayList(0, 3, 5, 2, 1, 6, 2), 200, 9, 5, partitionerType, condition);
    testReconfig(Lists.newArrayList(0, 3, 5, 2, 1, 6, 2), 200, 9, 5, partitionerType, condition);
    testReconfig(Lists.newArrayList(0, 1, 2, 3, 4, 5, 4, 3, 2, 1), 300, 9, 5, partitionerType, condition);
  }

  private static final String HASH_KEY = "HashKey";
  interface Condition {
    boolean check(long entryId, int groupSize, long instanceId, int hash);
  }

  private void testReconfig(List<Integer> consumerCounts, final int numEntries, final int queueBatchSize,
                            final int perConsumerDequeueBatchSize, QueuePartitioner.PartitionerType partitionerType,
                            Condition condition) throws Exception {
    TTQueue q = createQueue();
    // TODO: add reconfig to TTQueue interface
    TTQueueNewOnVCTable queue = (TTQueueNewOnVCTable) q;

    List<Integer> expectedEntries = Lists.newArrayList();
    // Enqueue numEntries
    for(int i = 0; i < numEntries; ++i) {
      expectedEntries.add(i + 1);
      QueueEntry queueEntry = new QueueEntry(Bytes.toBytes(i + 1));
      queueEntry.addPartitioningKey(HASH_KEY, i + 1);
      assertTrue(queue.enqueue(queueEntry, getDirtyWriteVersion()).isSuccess());
    }

    expectedEntries = ImmutableList.copyOf(expectedEntries);
    assertEquals(numEntries, expectedEntries.size());

    List<Integer> actualEntries = Lists.newArrayList();
    List<Integer> sortedActualEntries = Lists.newArrayList();
    List<StatefulQueueConsumer> consumers = Collections.emptyList();
    // dequeue it with FIFO partitioner, single entry mode
    QueueConfig config = new QueueConfig(partitionerType, true, queueBatchSize);
    long groupId = queue.getGroupID();

    int currentConsumerCount = consumerCounts.remove(0);

    loop:
    while(true) {
      for(Integer newConsumerCount : consumerCounts) {
//        System.out.println(String.format("Current consumer count = %d, new consumer count = %s",
//                                         currentConsumerCount, newConsumerCount));
        queue.reconfigure(config, groupId, currentConsumerCount, newConsumerCount, getDirtyPointer());
        // Create new consumers
        consumers = Lists.newArrayListWithCapacity(newConsumerCount);
        for(int i = 0; i < newConsumerCount; ++i) {
          if(partitionerType != QueuePartitioner.PartitionerType.HASH) {
            consumers.add(new StatefulQueueConsumer(i, groupId, newConsumerCount, config));
          } else {
            consumers.add(new StatefulQueueConsumer(i, groupId, newConsumerCount, "", HASH_KEY, config));
          }
        }

      // Dequeue entries
        int numDequeuesThisRun = 0;
        for(QueueConsumer consumer : consumers) {
          for(int i = 0; i < perConsumerDequeueBatchSize; ++i) {
            DequeueResult result = queue.dequeue(consumer, getDirtyPointer());
            if(result.isEmpty()) {
              break;
            }
            ++numDequeuesThisRun;
            actualEntries.add(Bytes.toInt(result.getEntry().getData()));
            queue.ack(result.getEntryPointer(), consumer, getDirtyPointer());
            assertTrue(
              condition.check(
                result.getEntryPointer().getEntryId(),
                newConsumerCount, consumer.getInstanceId(),
                (int) result.getEntryPointer().getEntryId()
              )
            );
          }
          actualEntries.add(-1);
        }
//        System.out.println(actualEntries);
        sortedActualEntries = Lists.newArrayList(actualEntries);
        Collections.sort(sortedActualEntries);
//        System.out.println(sortedActualEntries);
        currentConsumerCount = newConsumerCount;

        // If all consumers report queue empty then stop
        if(numDequeuesThisRun == 0) {
          break loop;
        }
      }
    }

    // Make sure the queue is empty
    for(QueueConsumer consumer : consumers) {
      DequeueResult result = queue.dequeue(consumer, getDirtyPointer());
      assertTrue(result.isEmpty());
    }

    sortedActualEntries.removeAll(Lists.newArrayList(-1));
    //System.out.println(sortedActualEntries);
    assertEquals(expectedEntries, sortedActualEntries);
  }

  // Tests that do not work on NewTTQueue

  /**
   * Currently not working.  Will be fixed in ENG-???.
   */
  @Override
  @Test
  @Ignore
  public void testSingleConsumerSingleEntryWithInvalid_Empty_ChangeSizeAndToMulti() {
  }

  @Override
  @Test
  @Ignore
  public void testSingleConsumerMultiEntry_Empty_ChangeToSingleConsumerSingleEntry() {
  }

  @Override
  @Test
  @Ignore
  public void testSingleConsumerSingleGroup_dynamicReconfig() {
  }

  @Override
  @Test
  @Ignore
  public void testMultiConsumerSingleGroup_dynamicReconfig() {
  }

  @Override
  @Test
  @Ignore
  public void testMultipleConsumerMultiTimeouts() {
  }

  @Override @Test @Ignore
  public void testMultiConsumerMultiGroup() {}

  @Override
  @Test
  @Ignore
  public void testSingleConsumerAckSemantics() {
  }

  @Override
  @Test @Ignore
  public void testSingleConsumerWithHashValuePartitioning() throws Exception {
  }
}
