package com.finguard.core.review.exception;

public class ReviewTaskNotFoundException extends RuntimeException {

    public ReviewTaskNotFoundException(Long reviewTaskId) {
        super("Review task not found: " + reviewTaskId);
    }
}
