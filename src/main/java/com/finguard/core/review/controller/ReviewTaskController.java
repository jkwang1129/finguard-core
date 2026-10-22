package com.finguard.core.review.controller;

import com.finguard.core.common.vo.PageResponse;
import com.finguard.core.review.dto.ReviewDecisionRequest;
import com.finguard.core.review.dto.ReviewTaskQueryRequest;
import com.finguard.core.review.service.ReviewTaskService;
import com.finguard.core.review.vo.ReviewTaskContextResponse;
import com.finguard.core.review.vo.ReviewTaskResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/review-tasks")
@Tag(name = "Review Tasks", description = "Exception review workflow")
public class ReviewTaskController {

    private final ReviewTaskService reviewTaskService;

    public ReviewTaskController(ReviewTaskService reviewTaskService) {
        this.reviewTaskService = reviewTaskService;
    }

    @GetMapping
    @Operation(summary = "Query review tasks")
    public PageResponse<ReviewTaskResponse> query(
            @Valid @ModelAttribute ReviewTaskQueryRequest request) {
        return reviewTaskService.query(request);
    }

    @GetMapping("/{reviewTaskId}")
    @Operation(summary = "Get a review task by ID")
    public ReviewTaskResponse getById(
            @PathVariable @Positive Long reviewTaskId) {
        return reviewTaskService.getById(reviewTaskId);
    }

    @GetMapping("/{reviewTaskId}/context")
    @Operation(summary = "Get sanitized review task context")
    public ReviewTaskContextResponse getContext(
            @PathVariable @Positive Long reviewTaskId) {
        return reviewTaskService.getContext(reviewTaskId);
    }

    @PatchMapping("/{reviewTaskId}/decision")
    @Operation(summary = "Confirm or ignore a pending review task")
    public ReviewTaskResponse decide(
            @PathVariable @Positive Long reviewTaskId,
            @Valid @RequestBody ReviewDecisionRequest request,
            @Parameter(hidden = true)
            @AuthenticationPrincipal Jwt jwt) {
        return reviewTaskService.decide(
                reviewTaskId,
                request,
                authenticatedUserId(jwt)
        );
    }

    private Long authenticatedUserId(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) {
            throw new IllegalStateException(
                    "Authenticated JWT subject is missing"
            );
        }
        try {
            long userId = Long.parseLong(jwt.getSubject());
            if (userId <= 0) {
                throw new NumberFormatException(
                        "JWT subject must be positive"
                );
            }
            return userId;
        } catch (NumberFormatException exception) {
            throw new IllegalStateException(
                    "Authenticated JWT subject is invalid"
            );
        }
    }
}
