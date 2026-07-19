package com.tradepulse.customerservice.dto.customer;

import com.tradepulse.customerservice.dto.validators.CreateCustomerValidationGroup;
import com.tradepulse.customerservice.dto.validators.ValidDateOfBirth;
import com.tradepulse.customerservice.dto.validators.ValidName;
import com.tradepulse.customerservice.dto.validators.ValidPostalCode;
import com.tradepulse.customerservice.dto.validators.ValidPhoneNumber;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class CustomerRequestDTO {

    @NotNull(groups = CreateCustomerValidationGroup.class, message = "User id is required")
    private Long userId;

    @NotBlank(message = "Firstname is required")
    @Size(max = 100, message = "Firstname cannot exceed 100 characters")
    @ValidName(message = "First name can only contain letters, spaces, hyphens, and apostrophes")
    private String firstName;

    @NotBlank(message = "Lastname is required")
    @Size(max = 100, message = "Lastname cannot exceed 100 characters")
    @ValidName(message = "Last name can only contain letters, spaces, hyphens, and apostrophes")
    private String lastName;

    @NotBlank(groups = CreateCustomerValidationGroup.class, message = "Email is required")
    @Email(groups = CreateCustomerValidationGroup.class, message = "Email must be valid")
    @Size(max = 255, groups = CreateCustomerValidationGroup.class, message = "Email cannot exceed 255 characters")
    private String email;

    @NotBlank(message = "Phone number is required")
    @ValidPhoneNumber(message = "Phone number must be between 7-15 digits, optionally starting with +, and can contain spaces or hyphens")
    private String phoneNumber;

    @NotBlank(message = "Address line 1 is required")
    @Size(max = 255, message = "Address line 1 cannot exceed 255 characters")
    private String addressLine1;

    @Size(max = 255, message = "Address line 2 cannot exceed 255 characters")
    private String addressLine2;

    @NotBlank(message = "City is required")
    @Size(max = 100, message = "City cannot exceed 100 characters")
    private String city;

    @NotBlank(message = "State is required")
    @Size(max = 100, message = "State cannot exceed 100 characters")
    private String state;

    @NotBlank(message = "Postal code is required")
    @Size(max = 20, message = "Postal code cannot exceed 20 characters")
    @ValidPostalCode(message = "Postal code must be 3-20 characters, include at least one digit, and use only letters, numbers, spaces, or hyphens")
    private String postalCode;

    @NotBlank(message = "Country is required")
    @Size(max = 100, message = "Country cannot exceed 100 characters")
    private String country;

    @NotBlank(message = "Date of birth is required")
    @ValidDateOfBirth(message = "You must be at least 18 years old")
    private String dateOfBirth;

    @NotBlank(groups = CreateCustomerValidationGroup.class, message = "Registration date is required")
    private String registrationDate;


    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getAddressLine1() {
        return addressLine1;
    }

    public void setAddressLine1(String addressLine1) {
        this.addressLine1 = addressLine1;
    }

    public String getAddressLine2() {
        return addressLine2;
    }

    public void setAddressLine2(String addressLine2) {
        this.addressLine2 = addressLine2;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public void setPostalCode(String postalCode) {
        this.postalCode = postalCode;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(String dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    public String getRegistrationDate() {
        return registrationDate;
    }

    public void setRegistrationDate(String registrationDate) {
        this.registrationDate = registrationDate;
    }
}

