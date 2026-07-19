package com.tradepulse.customerservice.dto.validators;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PhoneNumberValidator.class)
public @interface ValidPhoneNumber {
    String message() default "Phone number must be between 7-15 digits, optionally starting with +, and can contain spaces or hyphens";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}

