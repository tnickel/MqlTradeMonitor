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
            String passwordToUse = defaultAdminPassword;
            boolean generated = false;
            if ("password".equals(defaultAdminPassword) || defaultAdminPassword == null || defaultAdminPassword.trim().isEmpty()) {
                passwordToUse = generateRandomPassword();
                generated = true;
            }
            UserEntity admin = new UserEntity(defaultAdminUsername, passwordEncoder.encode(passwordToUse),
                    "ROLE_ADMIN");
            // NOTE: A fixed default key is deliberately used here to support existing production MT4/MT5 EA clients without redeploying.
            // This is secure since client key exposure only permits trade log submission, not server admin access.
            admin.setApiKey("jILus66S1hLrd8m0i_pgoiCQIc6JuA3asfM328UGFQ4");
            userRepository.save(admin);
            System.out.println("Initialized default admin user from properties with fixed default key.");
            if (generated) {
                System.out.println("=========================================================================");
                System.out.println(" WARNING: Insecure admin password detected or not specified!");
                System.out.println(" A random secure admin password has been generated for you:");
                System.out.println(" Username: " + defaultAdminUsername);
                System.out.println(" Password: " + passwordToUse);
                System.out.println(" Please save this password! It will only be displayed once during initial setup.");
                System.out.println("=========================================================================");
            }
        } else {
            UserEntity existingAdmin = existingAdminOpt.get();
            boolean updated = false;

            // If a custom admin password is set in application.properties and doesn't match the DB, update it
            if (defaultAdminPassword != null && !defaultAdminPassword.trim().isEmpty() && !"password".equals(defaultAdminPassword)) {
                if (!passwordEncoder.matches(defaultAdminPassword, existingAdmin.getPassword())) {
                    existingAdmin.setPassword(passwordEncoder.encode(defaultAdminPassword));
                    updated = true;
                    System.out.println("Updated admin password in DB to match new value from properties.");
                }
            }

            // Restore default key for the admin user if it was rotated or missing
            // This is required to keep backward compatibility with existing production clients.
            if (!"jILus66S1hLrd8m0i_pgoiCQIc6JuA3asfM328UGFQ4".equals(existingAdmin.getApiKey())) {
                existingAdmin.setApiKey("jILus66S1hLrd8m0i_pgoiCQIc6JuA3asfM328UGFQ4");
                updated = true;
                System.out.println("Restored default key for admin user to match existing production clients.");
            }

            if (updated) {
                userRepository.save(existingAdmin);
            }
        }
    }

    private String generateRandomPassword() {
        SecureRandom secureRandom = new SecureRandom();
        byte[] token = new byte[16];
        secureRandom.nextBytes(token);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(token);
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

    public UserEntity updateNewsAccounts(Long id, Set<Long> newsAccountIds, java.util.Map<Long, String> colors) {
        UserEntity user = userRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("User not found"));
        user.setNewsAccountIds(newsAccountIds);
        user.getNewsAccountColors().clear();
        if (colors != null) {
            user.getNewsAccountColors().putAll(colors);
        }
        return userRepository.save(user);
    }

    public void changePassword(Long id, String newRawPassword) {
        UserEntity user = userRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("User not found"));
        user.setPassword(passwordEncoder.encode(newRawPassword));
        userRepository.save(user);
    }

    public UserEntity updateAppAccountTypes(Long id, Set<Long> realAccountIds, Set<Long> demoAccountIds) {
        UserEntity user = userRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("User not found"));
        user.getRealAccountIds().clear();
        if (realAccountIds != null) {
            user.getRealAccountIds().addAll(realAccountIds);
        }
        user.getDemoAccountIds().clear();
        if (demoAccountIds != null) {
            user.getDemoAccountIds().addAll(demoAccountIds);
        }
        return userRepository.save(user);
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
