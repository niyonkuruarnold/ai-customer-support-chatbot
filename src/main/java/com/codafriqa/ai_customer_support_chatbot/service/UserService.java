package com.codafriqa.ai_customer_support_chatbot.service;

import com.codafriqa.ai_customer_support_chatbot.dto.UserRequestDto;
import com.codafriqa.ai_customer_support_chatbot.dto.UserResponseDto;
import com.codafriqa.ai_customer_support_chatbot.model.User;
import com.codafriqa.ai_customer_support_chatbot.model.UserRole;
import com.codafriqa.ai_customer_support_chatbot.repository.UserRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class UserService {

    /**
     * The customer the unauthenticated chat acts on behalf of. The frontend
     * has no registration, so sessions are attached to this fixed account;
     * its email is what agents see as the customer's contact in the
     * workspace. Created on first use / at boot by {@link #ensureAnonymousUser()}.
     */
    public static final String ANONYMOUS_EMAIL = "customer@codafriqa.local";

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    /**
     * Ensure the anonymous customer account exists and return it. The chat
     * has no registration, so this account backs every anonymous session.
     * Idempotent — looks up by email, creates only when missing.
     */
    public User ensureAnonymousUser() {
        return userRepository.findByEmail(ANONYMOUS_EMAIL)
                .orElseGet(() -> userRepository.save(new User(
                        ANONYMOUS_EMAIL,
                        passwordEncoder.encode(java.util.UUID.randomUUID().toString()),
                        UserRole.CUSTOMER)));
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

    public Optional<UserResponseDto> findByEmail(String email) {
        return userRepository.findByEmail(email)
                .map(this::mapToResponseDto);
    }

   private UserResponseDto mapToResponseDto(User user) {
    return new UserResponseDto(
        user.getId(), 
        null, // or remove if you update UserResponseDto constructor
        user.getEmail(), 
        user.getRole() != null ? user.getRole().name() : null
    );
}
}