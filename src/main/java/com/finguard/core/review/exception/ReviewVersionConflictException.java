package com.finguard.core.review.exception;

public class ReviewVersionConflictException extends RuntimeException {

    public ReviewVersionConflictException(Long reviewTaskId) {
        super("Review task version conflict: " + reviewTaskId);
    }
}
