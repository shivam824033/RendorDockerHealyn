-- Healyn V25: move the availability grid from 30-minute to 15-minute granularity.
-- Reference: docs/APPOINTMENT_FLOW.md §1 (Slot). The assign-time picker now offers
-- 15-minute start times so a physiotherapist can place 15 / 30 / 45 / 60-minute
-- visits on a finer grid. slot_minutes is the grid STEP (and each computed slot's
-- width); an appointment's own duration_minutes stays independent.
--
-- Existing rules created at the old 30-minute default are lowered to 15. This only
-- changes COMPUTED slots — stored appointments are untouched. A start/end aligned
-- to 30 minutes is already aligned to 15 (30 is a multiple of 15), so the
-- availability_rules_slot_alignment check still holds for every migrated row.

ALTER TABLE availability_rules ALTER COLUMN slot_minutes SET DEFAULT 15;

UPDATE availability_rules SET slot_minutes = 15 WHERE slot_minutes = 30;
