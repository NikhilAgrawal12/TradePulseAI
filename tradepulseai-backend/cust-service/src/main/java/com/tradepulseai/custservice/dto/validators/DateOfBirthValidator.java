package com.tradepulseai.custservice.dto.validators;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public class DateOfBirthValidator implements ConstraintValidator<ValidDateOfBirth, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isEmpty()) {
            return false;
        }

        try {
            LocalDate dateOfBirth = LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE);
            LocalDate today = LocalDate.now();
            LocalDate eighteenYearsAgo = today.minusYears(18);

            // Check if user is at least 18 years old
            return !dateOfBirth.isAfter(eighteenYearsAgo);
        } catch (DateTimeParseException e) {
            return false;
        }
    }
}

