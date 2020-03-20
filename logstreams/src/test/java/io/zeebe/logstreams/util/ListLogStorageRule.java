/*
 * Copyright © 2020  camunda services GmbH (info@camunda.com)
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package io.zeebe.logstreams.util;

import static org.mockito.Mockito.spy;

import io.atomix.protocols.raft.storage.RaftStorage;
import io.atomix.protocols.raft.storage.log.entry.RaftLogEntry;
import io.atomix.protocols.raft.zeebe.ZeebeEntry;
import io.atomix.protocols.raft.zeebe.ZeebeLogAppender;
import io.atomix.storage.journal.Indexed;
import io.atomix.storage.journal.JournalReader;
import io.atomix.storage.journal.JournalReader.Mode;
import io.zeebe.logstreams.spi.LogStorage;
import io.zeebe.logstreams.storage.atomix.AtomixAppenderSupplier;
import io.zeebe.logstreams.storage.atomix.AtomixLogStorage;
import io.zeebe.logstreams.storage.atomix.AtomixReaderFactory;
import io.zeebe.logstreams.storage.atomix.ZeebeIndexAdapter;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.LongConsumer;
import java.util.function.Supplier;
import org.junit.rules.ExternalResource;

public final class ListLogStorageRule extends ExternalResource
    implements AtomixReaderFactory, AtomixAppenderSupplier, ZeebeLogAppender, Supplier<LogStorage> {
  private final int partitionId;

  private RaftStorage raftStorage;
  private List<Indexed<ZeebeEntry>> log;

  private AtomixLogStorage storage;
  private LongConsumer positionListener;
  private volatile int logIndex;

  public ListLogStorageRule() {
    this(0);
  }

  public ListLogStorageRule(final int partitionId) {
    this.partitionId = partitionId;
  }

  @Override
  public void before() {
    open();
  }

  @Override
  public void after() {
    close();
  }

  @Override
  public void appendEntry(
      final long lowestPosition,
      final long highestPosition,
      final ByteBuffer data,
      final AppendListener listener) {
    final var duplicate = data.duplicate();
    final var zeebeEntry =
        new ZeebeEntry(0, System.currentTimeMillis(), lowestPosition, highestPosition, duplicate);
    final var indexedEntry = new Indexed<ZeebeEntry>(++logIndex, zeebeEntry, duplicate.capacity());

    log.add(indexedEntry);
    listener.onWrite(indexedEntry);
    listener.onCommit(indexedEntry);
    if (positionListener != null) {
      positionListener.accept(highestPosition);
    }
  }
  //
  //  public Indexed<ZeebeEntry> appendEntry(
  //      final long lowestPosition, final long highestPosition, final ByteBuffer data) {
  //    final var listener = new NoopListener();
  //    appendEntry(lowestPosition, highestPosition, data, listener);
  //
  //    return listener.lastWrittenEntry;
  //  }

  @Override
  public AtomixLogStorage get() {
    return storage;
  }

  @Override
  public Optional<ZeebeLogAppender> getAppender() {
    return Optional.of(this);
  }

  //  public CompletableFuture<Void> compact(final long index) {
  //    raftLog.compact(index);
  //    return CompletableFuture.completedFuture(null);
  //  }

  @Override
  public JournalReader<RaftLogEntry> create(final long index, final Mode mode) {
    return new JournalReader<>() {

      int readIndex = 0;

      @Override
      public boolean isEmpty() {
        return log.isEmpty();
      }

      @Override
      public long getFirstIndex() {
        if (log.isEmpty()) {
          return -1; // ListLogStorageRule.this.logIndex;
        }

        return log.get(0).index();
      }

      @Override
      public long getLastIndex() {
        if (log.isEmpty()) {
          return -1;
        }

        return log.get(log.size() - 1).index();
      }

      @Override
      public long getCurrentIndex() {
        if (log.isEmpty()) {
          return -1;
        }

        return log.get(readIndex).index();
      }

      @Override
      public Indexed getCurrentEntry() {
        if (log.isEmpty()) {
          return null;
        }
        return log.get(readIndex);
      }

      @Override
      public long getNextIndex() {
        if (log.isEmpty()) {
          return -1;
        }

        return log.get(readIndex).index() + 1;
      }

      @Override
      public boolean hasNext() {
        return !log.isEmpty() && log.size() != readIndex;
      }

      @Override
      public Indexed next() {
        if (log.isEmpty()) {
          throw new NoSuchElementException();
        }
        final var entry = log.get(readIndex);
        readIndex++;
        return entry;
      }

      @Override
      public void reset() {
        readIndex = 0;
      }

      @Override
      public void reset(long l) {
        if (l < 0) {
          return;
        }
        readIndex = (int) l;
      }

      @Override
      public void close() {
        // do nothing
      }
    };
  }

  public void setPositionListener(final LongConsumer positionListener) {
    this.positionListener = positionListener;
  }

  public void open() {
    close();
    logIndex = 0;

    final var zeebeIndexAdapter = ZeebeIndexAdapter.ofDensity(1);
    log = new CopyOnWriteArrayList<>();
    storage = spy(new AtomixLogStorage(zeebeIndexAdapter, this, this));
  }

  public void close() {
    Optional.ofNullable(log).ifPresent(List::clear);
    log = null;
    Optional.ofNullable(storage).ifPresent(AtomixLogStorage::close);
    storage = null;
    Optional.ofNullable(raftStorage).ifPresent(RaftStorage::deleteLog);
    raftStorage = null;
    positionListener = null;
  }

  public int getPartitionId() {
    return partitionId;
  }

  public AtomixLogStorage getStorage() {
    return storage;
  }
}
