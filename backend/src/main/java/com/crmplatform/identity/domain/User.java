package com.crmplatform.identity.domain;

import com.crmplatform.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User extends BaseEntity {

    @Column(nullable = false, length = 254)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "first_name", nullable = false, length = 60)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 60)
    private String lastName;

    @Column(name = "job_title", length = 100)
    private String jobTitle;

    @Column(length = 20)
    private String phone;

    @Column(name = "avatar_file_id")
    private UUID avatarFileId;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "super_admin", nullable = false)
    private boolean superAdmin;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "last_workspace_id")
    private UUID lastWorkspaceId;

    /** {@code ar} / {@code en}; null = follow the active workspace's default language. */
    @Column(name = "preferred_language", length = 5)
    private String preferredLanguage;

    protected User() {
    }

    public String getPreferredLanguage() {
        return preferredLanguage;
    }

    public void setPreferredLanguage(String preferredLanguage) {
        this.preferredLanguage = preferredLanguage;
    }

    /** The language this user sees in a workspace: their preference, else the workspace default. */
    public String effectiveLanguage(Workspace workspace) {
        if (preferredLanguage != null) {
            return preferredLanguage;
        }
        return workspace == null ? Workspace.DEFAULT_LANGUAGE : workspace.getDefaultLanguage();
    }

    public User(String email, String passwordHash, String firstName, String lastName) {
        this.email = normalizeEmail(email);
        this.passwordHash = passwordHash;
        this.firstName = firstName.trim();
        this.lastName = lastName.trim();
    }

    public static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    public void recordLogin(Instant at, UUID workspaceId) {
        this.lastLoginAt = at;
        this.lastWorkspaceId = workspaceId;
    }

    public void changePassword(String newHash) {
        this.passwordHash = newHash;
    }

    /** Changes the sign-in email; callers verify the password and uniqueness first. */
    public void changeEmail(String newEmail) {
        this.email = normalizeEmail(newEmail);
    }

    public void updateProfile(String firstName, String lastName, String jobTitle, String phone, UUID avatarFileId) {
        this.firstName = firstName.trim();
        this.lastName = lastName.trim();
        this.jobTitle = jobTitle;
        this.phone = phone;
        this.avatarFileId = avatarFileId;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public void setSuperAdmin(boolean superAdmin) {
        this.superAdmin = superAdmin;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getFullName() {
        return firstName + " " + lastName;
    }

    public String getJobTitle() {
        return jobTitle;
    }

    public String getPhone() {
        return phone;
    }

    public UUID getAvatarFileId() {
        return avatarFileId;
    }

    public boolean isActive() {
        return active;
    }

    public boolean isSuperAdmin() {
        return superAdmin;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public UUID getLastWorkspaceId() {
        return lastWorkspaceId;
    }
}
