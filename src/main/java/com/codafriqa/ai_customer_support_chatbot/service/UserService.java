package com.codafriqa.ai_customer_support_chatbot.service;

import com.codafriqa.ai_customer_support_chatbot.dto.UserRequestDto;
import com.codafriqa.ai_customer_support_chatbot.dto.UserResponseDto;
import com.codafriqa.ai_customer_support_chatbot.model.User;
import com.codafriqa.ai_customer_support_chatbot.repository.UserRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    public UserResponseDto createUser(UserRequestDto requestDto) {
        User user = new User();
        user.setEmail(requestDto.email());
        user.setPasswordHash(passwordEncoder.encode(requestDto.password()));
        
        if (requestDto.role() != null) {
            user.setRole(requestDto.role());
        }
        
        user.setCreatedAt(LocalDateTime.now());

        User savedUser = userRepository.save(user);
        return mapToResponseDto(savedUser);
    }

    public List<UserResponseDto> getAllUsers() {
        return userRepository.findAll()
                .stream()
                .map(this::mapToResponseDto)
                .toList();
    }

    public Optional<UserResponseDto> getUserById(Long id) {
        return userRepository.findById(id)
                .map(this::mapToResponseDto);
    }

    private UserResponseDto mapToResponseDto(User user) {
        return new UserResponseDto(
            user.getId(),
            user.getEmail(),
            user.getRole(),
            user.getCreatedAt()
        );
    }
}