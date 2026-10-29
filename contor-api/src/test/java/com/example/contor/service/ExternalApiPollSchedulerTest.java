package com.example.contor.service;

import com.example.contor.config.ExternalApiProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.util.unit.DataSize;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExternalApiPollSchedulerTest {

    private static final Instant NOW = Instant.parse("2026-10-29T12:00:00Z");

    @Mock
    private ExternalApiPoller poller;

    @Mock
    private ExternalApiIngestService ingestService;

    private ExternalApiPollScheduler scheduler(boolean enabled, int maxConcurrent) {
        var properties = new ExternalApiProperties(enabled, Duration.ofMinutes(1), Duration.ofSeconds(10), DataSize.ofMegabytes(1),
                maxConcurrent, 30, 5);
        return new ExternalApiPollScheduler(poller, ingestService, properties, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void scheduledPoll_DisabledDoesNothing() {
        scheduler(false, 4).scheduledPoll();

        verifyNoInteractions(poller);
    }

    @Test
    void scheduledPoll_PrunesTheHistoryAtMostOnceAnHour() {
        var scheduler = scheduler(true, 4);
        when(poller.findDue()).thenReturn(List.of());

        scheduler.scheduledPoll();
        scheduler.scheduledPoll();

        verify(poller, times(1)).pruneHistory();
        verify(poller, times(2)).findDue();
    }

    @Test
    void pollDue_PollsEveryDueApp() {
        when(poller.findDue()).thenReturn(List.of(new ExternalApiPoller.DueApi(1L, "a.example"), new ExternalApiPoller.DueApi(2L, "b.example")));

        scheduler(true, 4).pollDue();

        verify(poller).poll(1L);
        verify(poller).poll(2L);
        verify(poller, never()).pruneHistory();
    }

    @Test
    void pollDue_OneFailingPollDoesNotStopTheOthers() {
        when(poller.findDue()).thenReturn(List.of(new ExternalApiPoller.DueApi(1L, "a.example"), new ExternalApiPoller.DueApi(2L, "a.example")));
        doThrow(new IllegalStateException("broken")).when(poller).poll(1L);

        scheduler(true, 4).pollDue();

        verify(poller).poll(2L);
    }

    @Test
    void pollDue_CallsOneHostAtATimeAndAtMostTheConfiguredNumberAtOnce() {
        var due = IntStream.range(0, 12).mapToObj(i -> new ExternalApiPoller.DueApi((long) i, "host" + (i % 4) + ".example")).toList();
        when(poller.findDue()).thenReturn(due);
        var running = new AtomicInteger();
        var mostAtOnce = new AtomicInteger();
        Map<String, AtomicInteger> perHost = new ConcurrentHashMap<>();
        var hostOverlap = new AtomicInteger();
        doAnswer(inv -> {
            long id = inv.getArgument(0);
            var host = perHost.computeIfAbsent("host" + (id % 4), key -> new AtomicInteger());
            if (host.incrementAndGet() > 1) hostOverlap.incrementAndGet();
            mostAtOnce.accumulateAndGet(running.incrementAndGet(), Math::max);
            Thread.sleep(30);
            running.decrementAndGet();
            host.decrementAndGet();
            return null;
        }).when(poller).poll(anyLong());

        scheduler(true, 2).pollDue();

        verify(poller, times(12)).poll(anyLong());
        assertThat(hostOverlap).hasValue(0);
        assertThat(mostAtOnce.get()).isBetween(1, 2);
    }

    @Test
    void pollDue_HandsTheNewAndChangedItemsToTheIngestEvenWhenOneFails() {
        when(poller.findDue()).thenReturn(List.of(new ExternalApiPoller.DueApi(1L, "a.example")));
        when(poller.poll(1L)).thenReturn(List.of(new ExternalApiPoller.ItemChanges(30L, List.of(9L)), new ExternalApiPoller.ItemChanges(31L, List.of(10L))));
        doThrow(new IllegalStateException("broken")).when(ingestService).afterPoll(30L, List.of(9L));

        scheduler(true, 4).pollDue();

        verify(ingestService).afterPoll(31L, List.of(10L));
    }
}
