-- Healyn V2: enumerated types.
-- Reference: docs/DATABASE_SCHEMA.md §2.
-- Rules: append new values; never reorder.

DO $$ BEGIN
    CREATE TYPE account_status AS ENUM ('ACTIVE', 'LOCKED', 'DISABLED');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
    CREATE TYPE account_role AS ENUM ('ROLE_ACCOUNT', 'ROLE_PHYSIO');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
    CREATE TYPE otp_channel AS ENUM ('SMS', 'EMAIL');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
    CREATE TYPE otp_purpose AS ENUM ('REGISTRATION', 'LOGIN', 'PASSWORD_RESET');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
    CREATE TYPE patient_sex AS ENUM ('MALE', 'FEMALE', 'OTHER', 'UNDISCLOSED');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
    CREATE TYPE patient_relationship AS ENUM (
        'SELF', 'SPOUSE', 'PARENT', 'CHILD', 'SIBLING', 'GUARDIAN_OF', 'OTHER'
    );
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
    CREATE TYPE appointment_status AS ENUM (
        'REQUESTED', 'CONFIRMED', 'IN_PROGRESS',
        'COMPLETED', 'CANCELLED', 'NO_SHOW', 'RESCHEDULED'
    );
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
    CREATE TYPE appointment_cancel_reason AS ENUM (
        'PATIENT_CANCELLED', 'PHYSIO_CANCELLED', 'CLINIC_CLOSED', 'OTHER'
    );
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
    CREATE TYPE discussion_message_type AS ENUM (
        'QUESTION', 'REPLY', 'INSTRUCTION', 'ATTACHMENT_ONLY'
    );
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
    CREATE TYPE discussion_sender_role AS ENUM ('PATIENT_SIDE', 'PHYSIO');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
    CREATE TYPE file_kind AS ENUM (
        'REPORT', 'MRI', 'XRAY', 'PRESCRIPTION', 'EXERCISE_PLAN', 'OTHER'
    );
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
    CREATE TYPE file_mime AS ENUM ('application/pdf', 'image/jpeg', 'image/png');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
    CREATE TYPE file_status AS ENUM ('PENDING_UPLOAD', 'AVAILABLE', 'QUARANTINED', 'DELETED');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
    CREATE TYPE notification_channel AS ENUM ('FCM');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
    CREATE TYPE notification_status AS ENUM ('PENDING', 'SENT', 'FAILED', 'DEAD');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
    CREATE TYPE notification_kind AS ENUM (
        'BOOKING_REQUESTED', 'BOOKING_CONFIRMED', 'BOOKING_CANCELLED',
        'APPOINTMENT_REMINDER', 'DISCUSSION_NEW_MESSAGE', 'TREATMENT_NOTE_ADDED'
    );
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
    CREATE TYPE audit_action AS ENUM (
        'READ', 'CREATE', 'UPDATE', 'SOFT_DELETE', 'DOWNLOAD', 'EXPORT'
    );
EXCEPTION WHEN duplicate_object THEN NULL; END $$;
