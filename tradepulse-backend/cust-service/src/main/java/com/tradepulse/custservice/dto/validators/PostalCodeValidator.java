package com.tradepulse.custservice.dto.validators;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class PostalCodeValidator implements ConstraintValidator<ValidPostalCode, String> {

    // Accepts common postal formats while preventing plain alphabetic names.
    private static final String POSTAL_CODE_PATTERN = "^(?=.*\\d)[A-Za-z0-9][A-Za-z0-9\\- ]{2,19}$";

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return false;
        }

        return value.trim().matches(POSTAL_CODE_PATTERN);
    }
}

