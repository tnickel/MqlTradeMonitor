package de.trademonitor.entity;

import jakarta.persistence.*;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "app_users") // 'users' is sometimes a reserved word in SQL
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String role; // e.g., "ROLE_ADMIN", "ROLE_USER"

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_allowed_accounts", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "account_id")
    private Set<Long> allowedAccountIds = new HashSet<>();

    public UserEntity() {
    }

    public UserEntity(String username, String password, String role) {
        this.username = username;
        this.password = password;
        this.role = role;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public Set<Long> getAllowedAccountIds() {
        return allowedAccountIds;
    }

    public void setAllowedAccountIds(Set<Long> allowedAccountIds) {
        this.allowedAccountIds = allowedAccountIds;
    }
}
