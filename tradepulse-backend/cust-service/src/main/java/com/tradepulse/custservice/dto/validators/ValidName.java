package com.tradepulse.custservice.dto.validators;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = NameValidator.class)
public @interface ValidName {
    String message() default "Name can only contain letters, spaces, hyphens, and apostrophes, and must be 1-100 characters";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}

