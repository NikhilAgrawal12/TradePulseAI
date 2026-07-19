package com.tradepulse.customerservice.dto.validators;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = com.tradepulse.customerservice.dto.validators.PostalCodeValidator.class)
public @interface ValidPostalCode {
    String message() default "Postal code must be 3-20 characters, include at least one digit, and use only letters, numbers, spaces, or hyphens";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}


