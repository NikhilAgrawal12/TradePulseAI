package com.pm.userservice.mapper;

import com.pm.userservice.dto.UserRequestDTO;
import com.pm.userservice.dto.UserResponseDTO;
import com.pm.userservice.model.AppUser;

import java.time.LocalDate;

public class UserMapper {

    public static UserResponseDTO toDTO(AppUser user){
        UserResponseDTO userDTO = new UserResponseDTO();
        userDTO.setUserId(user.getUserId().toString());
        userDTO.setFirstName(user.getFirstName());
        userDTO.setLastName(user.getLastName());
        userDTO.setEmail(user.getEmail());
        userDTO.setPhoneNumber(user.getPhoneNumber());
        userDTO.setAddress(user.getAddress());
        userDTO.setCity(user.getCity());
        userDTO.setState(user.getState());
        userDTO.setCountry(user.getCountry());
        userDTO.setDateOfBirth(user.getDateOfBirth().toString());
        return userDTO;
    }

    public static AppUser toModel(UserRequestDTO userRequestDTO){
        AppUser user = new AppUser();
        user.setFirstName(userRequestDTO.getFirstName());
        user.setLastName(userRequestDTO.getLastName());
        user.setEmail(userRequestDTO.getEmail());
        user.setPhoneNumber(userRequestDTO.getPhoneNumber());
        user.setAddress(userRequestDTO.getAddress());
        user.setCity(userRequestDTO.getCity());
        user.setState(userRequestDTO.getState());
        user.setCountry(userRequestDTO.getCountry());
        user.setDateOfBirth(LocalDate.parse(userRequestDTO.getDateOfBirth()));
        user.setRegistrationDate(LocalDate.parse(userRequestDTO.getRegisteredDate()));
        return user;
    }
}
