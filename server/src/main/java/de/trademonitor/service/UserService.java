package de.trademonitor.service;

import de.trademonitor.entity.UserEntity;
import de.trademonitor.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Value("${app.admin.username:admin}")
    private String defaultAdminUsername;

    @Value("${app.admin.password:password}")
    private String defaultAdminPassword;

    @PostConstruct
    public void initDefaultAdmin() {
        Optional<UserEntity> existingAdminOpt = userRepository.findByUsername(defaultAdminUsername);

        if (existingAdminOpt.isEmpty()) {
            UserEntity admin = new UserEntity(defaultAdminUsername, passwordEncoder.encode(defaultAdminPassword),
                    "ROLE_ADMIN");
            admin.setApiKey(generateApiKey());
            userRepository.save(admin);
            System.out.println("Initialized default admin user from properties: " + defaultAdminUsername);
        } else {
            // Update the password of the existing admin if they exist, so the user can
            // easily log in
            // if they forgot their password and restarted the app with default settings
            UserEntity existingAdmin = existingAdminOpt.get();
            existingAdmin.setPassword(passwordEncoder.encode(defaultAdminPassword));
            if (existingAdmin.getApiKey() == null || existingAdmin.getApiKey().isEmpty()) {
                existingAdmin.setApiKey(generateApiKey());
            }
            userRepository.save(existingAdmin);
            System.out.println("Reset password for existing admin user from properties: " + defaultAdminUsername);
        }
    }

    public List<UserEntity> getAllUsers() {
        return userRepository.findAll();
    }

    public Optional<UserEntity> getUserById(Long id) {
        return userRepository.findById(id);
    }

    public Optional<UserEntity> getUserByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    public UserEntity createUser(String username, String rawPassword, String role, Set<Long> allowedAccountIds) {
        if (userRepository.findByUsername(username).isPresent()) {
            throw new IllegalArgumentException("Username already exists");
        }
        UserEntity user = new UserEntity(username, passwordEncoder.encode(rawPassword), role);
        user.setAllowedAccountIds(allowedAccountIds);
        user.setApiKey(generateApiKey());
        return userRepository.save(user);
    }

    public UserEntity updateUser(Long id, String role, Set<Long> allowedAccountIds) {
        UserEntity user = userRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("User not found"));
        user.setRole(role);
        user.setAllowedAccountIds(allowedAccountIds);
        return userRepository.save(user);
    }

    public void changePassword(Long id, String newRawPassword) {
        UserEntity user = userRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("User not found"));
        user.setPassword(passwordEncoder.encode(newRawPassword));
        userRepository.save(user);
    }

    public void deleteUser(Long id) {
        userRepository.deleteById(id);
    }

    private String generateApiKey() {
        SecureRandom secureRandom = new SecureRandom();
        byte[] token = new byte[32];
        secureRandom.nextBytes(token);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(token);
    }
}
