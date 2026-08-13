package com.pixelfactory.event.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pixelfactory.event.repository.FactoryEventRepository;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class FactoryEventRetentionJobTest {

    @Test
    void 설정한_보존일수보다_오래된_이벤트만_지운다() {
        FactoryEventRepository repository = mock(FactoryEventRepository.class);
        when(repository.deleteByCreatedAtBefore(any())).thenReturn(3);
        FactoryEventRetentionJob job = new FactoryEventRetentionJob(repository, 90);

        job.purgeOldEvents();

        ArgumentCaptor<LocalDateTime> cutoffCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(repository).deleteByCreatedAtBefore(cutoffCaptor.capture());

        // 정확히 "now - 90일"일 필요는 없다(실행 시각이 미세하게 다를 수 있다) — 근처인지만 본다.
        LocalDateTime expectedCutoff = LocalDateTime.now().minusDays(90);
        assertThat(cutoffCaptor.getValue()).isCloseTo(expectedCutoff,
                org.assertj.core.api.Assertions.within(5, ChronoUnit.SECONDS));
    }

    @Test
    void 보존일수_설정을_그대로_따른다() {
        FactoryEventRepository repository = mock(FactoryEventRepository.class);
        when(repository.deleteByCreatedAtBefore(any())).thenReturn(0);
        FactoryEventRetentionJob job = new FactoryEventRetentionJob(repository, 30);

        job.purgeOldEvents();

        ArgumentCaptor<LocalDateTime> cutoffCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(repository).deleteByCreatedAtBefore(cutoffCaptor.capture());

        LocalDateTime expectedCutoff = LocalDateTime.now().minusDays(30);
        assertThat(cutoffCaptor.getValue()).isCloseTo(expectedCutoff,
                org.assertj.core.api.Assertions.within(5, ChronoUnit.SECONDS));
    }
}
