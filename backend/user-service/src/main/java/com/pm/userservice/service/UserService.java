package com.pm.userservice.service;

import com.pm.userservice.dto.UserRequestDTO;
import com.pm.userservice.dto.UserResponseDTO;
import com.pm.userservice.exception.EmailAlreadyExistsException;
import com.pm.userservice.exception.UserNotFoundException;
import com.pm.userservice.mapper.UserMapper;
import com.pm.userservice.model.AppUser;
import com.pm.userservice.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public List<UserResponseDTO> getUsers() {
        List<AppUser> users = userRepository.findAll();

        return users.stream().map(UserMapper::toDTO).toList();
    }

    public UserResponseDTO createUser(UserRequestDTO userRequestDTO) {

        if (userRepository.existsByEmail(userRequestDTO.getEmail())) {
            throw new EmailAlreadyExistsException("A user with this email already exists " + userRequestDTO.getEmail());
        }

        AppUser user = userRepository.save(UserMapper.toModel(userRequestDTO));
        return UserMapper.toDTO(user);
    }

    public UserResponseDTO updateUser(UUID id, UserRequestDTO userRequestDTO) {

        AppUser user = userRepository.findById(id).orElseThrow(() -> new UserNotFoundException("Patient not found with ID: " + id));

        if (userRepository.existsByEmailAndIdNot(userRequestDTO.getEmail(),id)) {
            throw new EmailAlreadyExistsException("A user with this email already exists " + userRequestDTO.getEmail());
        }

        user.setFirstName(userRequestDTO.getFirstName());
        user.setLastName(userRequestDTO.getLastName());
        user.setEmail(userRequestDTO.getEmail());
        user.setAddress(userRequestDTO.getAddress());
        user.setCity(userRequestDTO.getCity());
        user.setState(userRequestDTO.getState());
        user.setCountry(userRequestDTO.getCountry());
        user.setDateOfBirth(LocalDate.parse(userRequestDTO.getDateOfBirth()));
        user.setPhoneNumber(userRequestDTO.getPhoneNumber());

        AppUser updatedUser = userRepository.save(user);
        return UserMapper.toDTO(updatedUser);

    }

    public void deleteUser(UUID id) {
        userRepository.deleteById(id);
    }


}
