package com.healyn.patients.repository;

import com.healyn.patients.domain.Patient;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PatientRepository extends JpaRepository<Patient, UUID> {
}
