package com.healyn.patients.domain;

import com.healyn.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "patients")
public class Patient extends BaseEntity {

    @Column(name = "full_name", nullable = false, length = 160)
    private String fullName;

    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "sex", nullable = false, columnDefinition = "patient_sex")
    private PatientSex sex = PatientSex.UNDISCLOSED;

    @Column(name = "phone_e164", length = 20)
    private String phoneE164;

    @Column(name = "email", columnDefinition = "citext")
    private String email;

    @Column(name = "blood_group", length = 3)
    private String bloodGroup;

    @Column(name = "allergies")
    private String allergies;

    @Column(name = "notes")
    private String notes;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected Patient() {}

    public Patient(UUID id, String fullName, LocalDate dateOfBirth, PatientSex sex) {
        this.id = id;
        this.fullName = fullName;
        this.dateOfBirth = dateOfBirth;
        if (sex != null) this.sex = sex;
    }

    public String getFullName() { return fullName; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public PatientSex getSex() { return sex; }
    public String getPhoneE164() { return phoneE164; }
    public String getEmail() { return email; }
    public String getBloodGroup() { return bloodGroup; }
    public String getAllergies() { return allergies; }
    public String getNotes() { return notes; }
    public Instant getDeletedAt() { return deletedAt; }

    public void rename(String fullName) { this.fullName = fullName; }
    public void setDateOfBirth(LocalDate dob) { this.dateOfBirth = dob; }
    public void setSex(PatientSex sex) { if (sex != null) this.sex = sex; }
    public void setPhoneE164(String phoneE164) { this.phoneE164 = phoneE164; }
    public void setEmail(String email) { this.email = email; }
    public void setBloodGroup(String bloodGroup) { this.bloodGroup = bloodGroup; }
    public void setAllergies(String allergies) { this.allergies = allergies; }
    public void setNotes(String notes) { this.notes = notes; }

    public void softDelete(Instant when) {
        this.deletedAt = when;
    }
}
