package com.finguard.core.auth.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.nio.charset.StandardCharsets;

public class Utf8ByteLengthValidator implements
        ConstraintValidator<Utf8ByteLength, String> {

    private int min;
    private int max;

    @Override
    public void initialize(Utf8ByteLength constraintAnnotation) {
        min = constraintAnnotation.min();
        max = constraintAnnotation.max();
    }

    @Override
    public boolean isValid(
            String value,
            ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        if (value.isEmpty()) {
            return true;
        }

        int byteLength = value.getBytes(StandardCharsets.UTF_8).length;
        return byteLength >= min && byteLength <= max;
    }
}
