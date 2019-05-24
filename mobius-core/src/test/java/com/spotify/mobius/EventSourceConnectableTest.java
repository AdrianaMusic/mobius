/*
 * -\-\-
 * Mobius
 * --
 * Copyright (c) 2017-2018 Spotify AB
 * --
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
 * -/-/-
 */
package com.spotify.mobius;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import com.spotify.mobius.disposables.Disposable;
import com.spotify.mobius.functions.Consumer;
import com.spotify.mobius.test.RecordingConsumer;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.annotation.Nonnull;
import org.junit.Before;
import org.junit.Test;

public class EventSourceConnectableTest {

  TestEventSource source;
  Connectable<Integer, String> underTest;
  RecordingConsumer<String> events;

  @Before
  public void setUp() throws Exception {
    source = new TestEventSource();
    underTest = EventSourceConnectable.create(source);
    events = new RecordingConsumer<>();
  }

  public static class SubscriptionsBehavior extends EventSourceConnectableTest {
    @Test
    public void subscribesToEventSourceOnFirstModel() {
      final Connection<Integer> connection = underTest.connect(events);
      connection.accept(0);
      assertThat(source.subscriberCount(), is(1));
    }

    @Test
    public void subscribesToEventSourceOnlyOnce() {
      final Connection<Integer> connection = underTest.connect(events);
      connection.accept(0);
      connection.accept(1);
      assertThat(source.subscriberCount(), is(1));
    }

    @Test
    public void disposingUnsubscribesFromEventSource() {
      final Connection<Integer> connection = underTest.connect(events);
      connection.accept(0);
      connection.dispose();
      assertThat(source.subscriberCount(), is(0));
    }

    @Test
    public void disposingBeforeStartingDoesNothing() {
      final Connection<Integer> connection = underTest.connect(events);
      assertThat(source.subscriberCount(), is(0));
      connection.dispose();
      assertThat(source.subscriberCount(), is(0));
    }

    @Test
    public void disposingThenSubscribingResubscribesToEventSource() {
      Connection<Integer> connection = underTest.connect(events);
      connection.accept(0);
      assertThat(source.subscriberCount(), is(1));
      connection.dispose();
      assertThat(source.subscriberCount(), is(0));
      connection = underTest.connect(events);
      connection.accept(1);
      assertThat(source.subscriberCount(), is(1));
    }
  }

  public static class EmissionsBehavior extends EventSourceConnectableTest {
    @Test
    public void forwardsAllEmittedEvents() {
      final Connection<Integer> connection = underTest.connect(events);
      connection.accept(0);
      source.publishEvent("Hello");
      source.publishEvent("World");
      events.assertValues("Hello", "World");
    }

    @Test
    public void noItemsAreEmittedOnceDisposed() {
      final Connection<Integer> connection = underTest.connect(events);
      connection.accept(0);
      source.publishEvent("Hello");
      connection.dispose();
      source.publishEvent("World");
      events.assertValues("Hello");
    }
  }

  private static class TestEventSource implements EventSource<String> {
    private CopyOnWriteArrayList<Consumer<String>> consumers = new CopyOnWriteArrayList<>();

    @Nonnull
    @Override
    public Disposable subscribe(Consumer<String> eventConsumer) {
      consumers.add(eventConsumer);
      return () -> consumers.remove(eventConsumer);
    }

    public void publishEvent(String event) {
      for (Consumer<String> consumer : consumers) {
        consumer.accept(event);
      }
    }

    public int subscriberCount() {
      return consumers.size();
    }
  }
}
