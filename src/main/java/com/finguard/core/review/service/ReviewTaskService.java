package com.finguard.core.review.service;

import com.finguard.core.common.vo.PageResponse;
import com.finguard.core.review.dto.ReviewDecisionRequest;
import com.finguard.core.review.dto.ReviewTaskQueryRequest;
import com.finguard.core.review.vo.ReviewTaskResponse;

public interface ReviewTaskService {

    PageResponse<ReviewTaskResponse> query(
            ReviewTaskQueryRequest request
    );

    ReviewTaskResponse getById(Long reviewTaskId);

    ReviewTaskResponse decide(
            Long reviewTaskId,
            ReviewDecisionRequest request,
            Long reviewerId
    );
}
