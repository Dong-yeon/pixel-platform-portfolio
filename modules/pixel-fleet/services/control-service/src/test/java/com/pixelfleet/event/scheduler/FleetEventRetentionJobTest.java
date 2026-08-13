package com.pixelfleet.event.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pixelfleet.event.repository.FleetEventRepository;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class FleetEventRetentionJobTest {

    @Test
    void 설정한_보존일수보다_오래된_이벤트만_지운다() {
        FleetEventRepository repository = mock(FleetEventRepository.class);
        when(repository.deleteByCreatedAtBefore(any())).thenReturn(5);
        FleetEventRetentionJob job = new FleetEventRetentionJob(repository, 90);

        job.purgeOldEvents();

        ArgumentCaptor<LocalDateTime> cutoffCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(repository).deleteByCreatedAtBefore(cutoffCaptor.capture());

        LocalDateTime expectedCutoff = LocalDateTime.now().minusDays(90);
        assertThat(cutoffCaptor.getValue()).isCloseTo(expectedCutoff,
                org.assertj.core.api.Assertions.within(5, ChronoUnit.SECONDS));
    }
}
