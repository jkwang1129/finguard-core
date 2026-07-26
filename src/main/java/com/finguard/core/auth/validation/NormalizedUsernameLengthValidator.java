package com.finguard.core.auth.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class NormalizedUsernameLengthValidator implements
        ConstraintValidator<NormalizedUsernameLength, String> {

    private int min;
    private int max;

    @Override
    public void initialize(NormalizedUsernameLength constraintAnnotation) {
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

        String normalizedUsername =
                AuthInputNormalizer.normalizeUsername(value);
        if (normalizedUsername.isEmpty()) {
            return true;
        }

        int normalizedLength = normalizedUsername.length();
        return normalizedLength >= min && normalizedLength <= max;
    }
}
