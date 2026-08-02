package com.finguard.core.review.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.finguard.core.reconciliation.model.ReconciliationResultType;
import com.finguard.core.review.dto.ReviewDecisionRequest;
import com.finguard.core.review.dto.ReviewTaskQueryRequest;
import com.finguard.core.review.entity.ReviewTask;
import com.finguard.core.review.exception.InvalidReviewOperationException;
import com.finguard.core.review.exception.InvalidReviewRequestException;
import com.finguard.core.review.exception.ReviewTaskNotFoundException;
import com.finguard.core.review.exception.ReviewVersionConflictException;
import com.finguard.core.review.mapper.ReviewTaskMapper;
import com.finguard.core.review.model.ReviewDecision;
import com.finguard.core.review.model.ReviewTaskSourceType;
import com.finguard.core.review.model.ReviewTaskStatus;
import com.finguard.core.review.model.ReviewTaskView;
import com.finguard.core.review.service.impl.ReviewTaskServiceImpl;
import com.finguard.core.risk.model.RiskRuleCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewTaskServiceImplTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-08-02T04:30:15.123Z"),
            ZoneId.of("Asia/Shanghai")
    );

    @Mock
    private ReviewTaskMapper reviewTaskMapper;

    private ReviewTaskServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ReviewTaskServiceImpl(
                reviewTaskMapper,
                FIXED_CLOCK
        );
    }

    @Test
    void queryInfersRiskSourceAndMapsStablePage() {
        ReviewTaskQueryRequest request = new ReviewTaskQueryRequest(
                null,
                null,
                ReviewTaskStatus.PENDING,
                null,
                null,
                RiskRuleCode.LARGE_AMOUNT
        );
        Page<ReviewTaskView> page = new Page<>(1, 20, 1);
        page.setRecords(List.of(view(11L, ReviewTaskStatus.PENDING)));
        when(reviewTaskMapper.selectTaskPage(
                org.mockito.ArgumentMatchers
                        .<Page<ReviewTaskView>>any(),
                eq(ReviewTaskStatus.PENDING),
                eq(ReviewTaskSourceType.RISK_HIT),
                eq(null),
                eq(RiskRuleCode.LARGE_AMOUNT)
        )).thenReturn(page);

        var response = service.query(request);

        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(20);
        assertThat(response.total()).isEqualTo(1);
        assertThat(response.records()).singleElement()
                .satisfies(record -> assertThat(record.id())
                        .isEqualTo(11L));
    }

    @Test
    void queryRejectsContradictoryAndNonExceptionFilters() {
        assertThatThrownBy(() -> service.query(
                new ReviewTaskQueryRequest(
                        1L,
                        20L,
                        null,
                        null,
                        ReconciliationResultType.UNMATCHED,
                        RiskRuleCode.LARGE_AMOUNT
                )
        )).isInstanceOf(InvalidReviewRequestException.class);
        assertThatThrownBy(() -> service.query(
                new ReviewTaskQueryRequest(
                        1L,
                        20L,
                        null,
                        null,
                        ReconciliationResultType.MATCHED,
                        null
                )
        )).isInstanceOf(InvalidReviewRequestException.class);
        verifyNoInteractions(reviewTaskMapper);
    }

    @Test
    void decideNormalizesNoteAndReturnsPersistedTerminalTask() {
        ReviewTask task = pendingTask(31L, 0);
        ReviewTaskView persisted = view(
                31L,
                ReviewTaskStatus.CONFIRMED
        );
        persisted.setVersion(1);
        persisted.setReviewedBy(7L);
        persisted.setReviewedAt(
                LocalDateTime.of(2026, 8, 2, 12, 30, 15, 123_000_000)
        );
        persisted.setDecisionNote("checked");
        when(reviewTaskMapper.selectById(31L)).thenReturn(task);
        when(reviewTaskMapper.updateDecision(
                eq(31L),
                eq(ReviewTaskStatus.CONFIRMED),
                eq(0),
                eq(7L),
                any(LocalDateTime.class),
                eq("checked")
        )).thenReturn(1);
        when(reviewTaskMapper.selectTaskViewById(31L))
                .thenReturn(persisted);

        var response = service.decide(
                31L,
                new ReviewDecisionRequest(
                        ReviewDecision.CONFIRMED,
                        0,
                        "  checked  "
                ),
                7L
        );

        assertThat(response.status())
                .isEqualTo(ReviewTaskStatus.CONFIRMED);
        assertThat(response.version()).isEqualTo(1);
        assertThat(response.reviewedBy()).isEqualTo(7L);
        assertThat(response.decisionNote()).isEqualTo("checked");
        verify(reviewTaskMapper).updateDecision(
                31L,
                ReviewTaskStatus.CONFIRMED,
                0,
                7L,
                LocalDateTime.of(
                        2026,
                        8,
                        2,
                        12,
                        30,
                        15,
                        123_000_000
                ),
                "checked"
        );
    }

    @Test
    void decideClassifiesMissingTerminalStaleAndLostRace() {
        when(reviewTaskMapper.selectById(41L)).thenReturn(null);
        assertThatThrownBy(() -> decide(41L, 0))
                .isInstanceOf(ReviewTaskNotFoundException.class);

        when(reviewTaskMapper.selectById(42L)).thenReturn(
                terminalTask(42L)
        );
        assertThatThrownBy(() -> decide(42L, 0))
                .isInstanceOf(InvalidReviewOperationException.class);

        when(reviewTaskMapper.selectById(43L)).thenReturn(
                pendingTask(43L, 1)
        );
        assertThatThrownBy(() -> decide(43L, 0))
                .isInstanceOf(ReviewVersionConflictException.class);

        when(reviewTaskMapper.selectById(44L)).thenReturn(
                pendingTask(44L, 0)
        );
        when(reviewTaskMapper.updateDecision(
                eq(44L),
                any(),
                eq(0),
                eq(7L),
                any(),
                eq(null)
        )).thenReturn(0);
        assertThatThrownBy(() -> decide(44L, 0))
                .isInstanceOf(ReviewVersionConflictException.class);
    }

    @Test
    void decideAccepts255UnicodeCodePointsAndRejects256() {
        ReviewTask task = pendingTask(51L, 0);
        when(reviewTaskMapper.selectById(51L)).thenReturn(task);
        when(reviewTaskMapper.updateDecision(
                eq(51L),
                any(),
                eq(0),
                eq(7L),
                any(),
                any()
        )).thenReturn(0);

        String allowed = "界".repeat(255);
        assertThatThrownBy(() -> service.decide(
                51L,
                new ReviewDecisionRequest(
                        ReviewDecision.CONFIRMED,
                        0,
                        allowed
                ),
                7L
        )).isInstanceOf(ReviewVersionConflictException.class);

        String rejected = "界".repeat(256);
        assertThatThrownBy(() -> service.decide(
                51L,
                new ReviewDecisionRequest(
                        ReviewDecision.CONFIRMED,
                        0,
                        rejected
                ),
                7L
        )).isInstanceOf(InvalidReviewRequestException.class);
    }

    private void decide(Long id, int version) {
        service.decide(
                id,
                new ReviewDecisionRequest(
                        ReviewDecision.CONFIRMED,
                        version,
                        null
                ),
                7L
        );
    }

    private ReviewTask pendingTask(Long id, int version) {
        ReviewTask task = new ReviewTask();
        task.setId(id);
        task.setStatus(ReviewTaskStatus.PENDING);
        task.setVersion(version);
        return task;
    }

    private ReviewTask terminalTask(Long id) {
        ReviewTask task = pendingTask(id, 1);
        task.setStatus(ReviewTaskStatus.CONFIRMED);
        return task;
    }

    private ReviewTaskView view(Long id, ReviewTaskStatus status) {
        ReviewTaskView view = new ReviewTaskView();
        view.setId(id);
        view.setSourceType(ReviewTaskSourceType.RISK_HIT);
        view.setRiskHitId(9L);
        view.setCsvTransactionId(10L);
        view.setResultType(ReconciliationResultType.UNMATCHED);
        view.setRuleCode(RiskRuleCode.LARGE_AMOUNT);
        view.setStatus(status);
        view.setVersion(0);
        view.setCreatedAt(LocalDateTime.of(2026, 8, 2, 12, 0));
        view.setUpdatedAt(LocalDateTime.of(2026, 8, 2, 12, 0));
        return view;
    }
}
