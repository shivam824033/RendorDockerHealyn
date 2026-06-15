/// The physiotherapist's own profile: personal/professional details, clinic
/// information, social links, and an avatar. Single-tenant — there is exactly one
/// ROLE_PHYSIO account (PROJECT_CONTEXT §5.2), so the profile is a single row keyed
/// by the physiotherapist's account id. The physiotherapist edits it; every patient
/// reads it to learn who their physiotherapist is and how to reach the clinic.
///
/// The avatar reuses the object-storage presign mechanism ({@code files.port}
/// FileStorePort + {@code files.domain} FileMime), but is stored under its own key
/// prefix rather than in the patient-scoped {@code file_objects} table.
package com.healyn.physio;
