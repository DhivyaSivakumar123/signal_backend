package com.signal.triage.service;

import com.signal.common.dto.MessageSource;
import com.signal.common.dto.Urgency;
import com.signal.triage.entity.TriageItem;
import com.signal.triage.repository.TriageItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RankingServiceTest {

    @Mock
    private TriageItemRepository triageItemRepository;

    private RankingService rankingService;
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        rankingService = new RankingService(triageItemRepository);
    }

    @Test
    void testRankingSortsByUrgencyDescending() {
        Instant now = Instant.now();
        TriageItem fyi = new TriageItem(userId, UUID.randomUUID(), MessageSource.SLACK, Urgency.FYI, "fyi", "alice", now, "UNSEEN");
        TriageItem today = new TriageItem(userId, UUID.randomUUID(), MessageSource.GMAIL, Urgency.TODAY, "today", "bob", now, "UNSEEN");
        TriageItem immediate = new TriageItem(userId, UUID.randomUUID(), MessageSource.GMAIL, Urgency.NOW, "now", "carol", now, "UNSEEN");
        TriageItem thisWeek = new TriageItem(userId, UUID.randomUUID(), MessageSource.OUTLOOK, Urgency.THIS_WEEK, "this_week", "dave", now, "UNSEEN");

        when(triageItemRepository.findByUserIdAndStatusIn(eq(userId), any())).thenReturn(List.of(fyi, today, immediate, thisWeek));

        List<TriageItem> ranked = rankingService.getRankedFeed(userId);

        assertThat(ranked).hasSize(4);
        assertThat(ranked.get(0).getUrgency()).isEqualTo(Urgency.NOW);
        assertThat(ranked.get(1).getUrgency()).isEqualTo(Urgency.TODAY);
        assertThat(ranked.get(2).getUrgency()).isEqualTo(Urgency.THIS_WEEK);
        assertThat(ranked.get(3).getUrgency()).isEqualTo(Urgency.FYI);
    }

    @Test
    void testThreadCollapsingKeepsHighestUrgencyItem() {
        UUID threadId = UUID.randomUUID();
        Instant now = Instant.now();
        TriageItem todayItem = new TriageItem(userId, threadId, MessageSource.SLACK, Urgency.TODAY, "lower urgency", "alice", now.minus(5, ChronoUnit.MINUTES), "UNSEEN");
        TriageItem nowItem = new TriageItem(userId, threadId, MessageSource.SLACK, Urgency.NOW, "higher urgency", "alice", now, "UNSEEN");

        when(triageItemRepository.findByUserIdAndStatusIn(eq(userId), any())).thenReturn(List.of(todayItem, nowItem));

        List<TriageItem> ranked = rankingService.getRankedFeed(userId);

        assertThat(ranked).hasSize(1);
        assertThat(ranked.get(0).getUrgency()).isEqualTo(Urgency.NOW);
        assertThat(ranked.get(0).getReasonSnippet()).isEqualTo("higher urgency");
    }

    @Test
    void testThreadCollapsingSameUrgencyKeepsNewestItem() {
        UUID threadId = UUID.randomUUID();
        Instant older = Instant.now().minus(10, ChronoUnit.MINUTES);
        Instant newer = Instant.now();

        TriageItem olderItem = new TriageItem(userId, threadId, MessageSource.GMAIL, Urgency.TODAY, "old message", "bob", older, "UNSEEN");
        TriageItem newerItem = new TriageItem(userId, threadId, MessageSource.GMAIL, Urgency.TODAY, "new message", "bob", newer, "UNSEEN");

        when(triageItemRepository.findByUserIdAndStatusIn(eq(userId), any())).thenReturn(List.of(olderItem, newerItem));

        List<TriageItem> ranked = rankingService.getRankedFeed(userId);

        assertThat(ranked).hasSize(1);
        assertThat(ranked.get(0).getReasonSnippet()).isEqualTo("new message");
        assertThat(ranked.get(0).getReceivedAt()).isEqualTo(newer);
    }

    @Test
    void testTieBreakingByTimestampForSameUrgency() {
        Instant t1 = Instant.now().minus(20, ChronoUnit.MINUTES);
        Instant t2 = Instant.now().minus(5, ChronoUnit.MINUTES);
        Instant t3 = Instant.now();

        TriageItem item1 = new TriageItem(userId, UUID.randomUUID(), MessageSource.GMAIL, Urgency.NOW, "earliest", "user1", t1, "UNSEEN");
        TriageItem item2 = new TriageItem(userId, UUID.randomUUID(), MessageSource.SLACK, Urgency.NOW, "middle", "user2", t2, "UNSEEN");
        TriageItem item3 = new TriageItem(userId, UUID.randomUUID(), MessageSource.OUTLOOK, Urgency.NOW, "latest", "user3", t3, "UNSEEN");

        when(triageItemRepository.findByUserIdAndStatusIn(eq(userId), any())).thenReturn(List.of(item1, item2, item3));

        List<TriageItem> ranked = rankingService.getRankedFeed(userId);

        assertThat(ranked).hasSize(3);
        assertThat(ranked.get(0).getReasonSnippet()).isEqualTo("latest");
        assertThat(ranked.get(1).getReasonSnippet()).isEqualTo("middle");
        assertThat(ranked.get(2).getReasonSnippet()).isEqualTo("earliest");
    }
}
