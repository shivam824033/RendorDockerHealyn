/// First-party clinic content the single physiotherapist publishes to patients:
/// service cards, promotional banners, clinic announcements, and health tips
/// (FEATURE_ROADMAP F1.23). Active, in-window content is read by every patient and
/// surfaced on Home (carousel + sections) → details screen; create / update / delete /
/// reorder / activate are physio-only, enforced in the service via
/// {@link com.healyn.promotions.policy.PromotionPolicy} (CLAUDE.md hard rule #2).
///
/// Cover images reuse the object-storage presign + magic-byte pipeline
/// ({@link com.healyn.files.port.FileStorePort} / {@link com.healyn.files.domain.FileMime})
/// under a {@code promotions/<id>/cover/} key prefix — never the patient-scoped
/// {@code file_objects} table. Not clinical PHI; soft-deleted for an auditable trail.
///
/// {@code clinicId} is a Phase-3 multi-clinic enabler (F3.4): always null and unexposed
/// in Phase 1. The patient query is unscoped.
package com.healyn.promotions;
