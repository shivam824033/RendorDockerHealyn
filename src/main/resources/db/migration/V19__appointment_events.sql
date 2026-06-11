-- Healyn V19: appointment_events (append-only per-appointment lifecycle timeline).
-- Reference: docs/APPOINTMENT_FLOW.md §3, docs/DATABASE_SCHEMA.md §3.8a,
-- docs/FEATURE_ROADMAP.md "Identifiers & lifecycle note" + §4 Phase-3 enabler
-- ("all clinical writes already produce a domain event" — realized here, not new scope).
--
-- One row per lifecycle action on an appointment: creation (of a root or of a lineage
-- child), schedule/confirm, start, complete, cancel, no-show, and "this row was replaced
-- by a reschedule". Together with the V18 lineage columns this is the full story a
-- unified timeline renders: in-place changes are events; actions that spawn a new
-- bookable row are events on both sides of the parent-child link.
--
-- PHI-free by construction: IDs, enums and timestamps only — never names, reasons or
-- notes (free text stays on the appointments row). Append-only like audit.audit_log:
-- rows are never updated or deleted, so there is no updated_at / deleted_at; an event's
-- visibility follows its (soft-deletable) appointment. Additive only (hard rule #8).

DO $$ BEGIN
    -- REJECTED is reserved for the approved request-rejection flow (a later chunk adds
    -- the appointment_status value); creating it here avoids a second enum migration.
    CREATE TYPE appointment_event_type AS ENUM (
        'CREATED', 'SCHEDULED', 'STARTED', 'COMPLETED',
        'CANCELLED', 'NO_SHOW', 'RESCHEDULED', 'REJECTED');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

CREATE TABLE appointment_events (
    id                      BIGSERIAL    PRIMARY KEY,
    appointment_id          UUID         NOT NULL REFERENCES appointments(id) ON DELETE RESTRICT,
    event_type              appointment_event_type NOT NULL,
    occurred_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    -- Null when the actor is unknowable (pre-V19 backfill of cancellations).
    actor_account_id        UUID         REFERENCES accounts(id) ON DELETE RESTRICT,
    actor_role              account_role,
    -- The other side of a parent-child action: for RESCHEDULED the replacement row,
    -- for a child's CREATED the source it derived from.
    related_appointment_id  UUID         REFERENCES appointments(id) ON DELETE RESTRICT,
    child_kind              appointment_child_kind,
    cancel_reason           appointment_cancel_reason
);

-- Leading column covers the appointment FK; the full key matches the timeline ordering.
CREATE INDEX idx_appointment_events_appointment
    ON appointment_events (appointment_id, occurred_at, id);

CREATE INDEX idx_appointment_events_actor
    ON appointment_events (actor_account_id)
    WHERE actor_account_id IS NOT NULL;

CREATE INDEX idx_appointment_events_related
    ON appointment_events (related_appointment_id)
    WHERE related_appointment_id IS NOT NULL;

-- ---------------------------------------------------------------------------
-- Backfill: reconstruct events for pre-V19 appointments from their lifecycle
-- timestamps. Best effort — actors are inferred (creator from booked_by_account_id;
-- schedule/start/complete/no-show were physio-only actions; a cancellation's actor
-- is unknowable and stays NULL) and NO_SHOW has no dedicated timestamp (updated_at
-- is the closest record). Soft-deleted appointments get events too: the timeline
-- query scopes visibility through the appointments row.
-- ---------------------------------------------------------------------------

-- Every appointment was created; children carry how and from what they derived.
INSERT INTO appointment_events
       (appointment_id, event_type, occurred_at, actor_account_id, actor_role,
        related_appointment_id, child_kind)
SELECT a.id, 'CREATED', a.created_at, a.booked_by_account_id, acc.role,
       a.source_appointment_id, a.child_kind
  FROM appointments a
  LEFT JOIN accounts acc ON acc.id = a.booked_by_account_id;

-- A separate schedule step only happened when confirmation came after creation;
-- rows born CONFIRMED (follow-ups, physio reschedules) confirm within the creating
-- transaction, so their CREATED event already tells the story.
INSERT INTO appointment_events
       (appointment_id, event_type, occurred_at, actor_account_id, actor_role)
SELECT a.id, 'SCHEDULED', a.confirmed_at, a.physiotherapist_id, 'ROLE_PHYSIO'
  FROM appointments a
 WHERE a.confirmed_at IS NOT NULL
   AND a.confirmed_at > a.created_at;

INSERT INTO appointment_events
       (appointment_id, event_type, occurred_at, actor_account_id, actor_role)
SELECT a.id, 'STARTED', a.started_at, a.physiotherapist_id, 'ROLE_PHYSIO'
  FROM appointments a
 WHERE a.started_at IS NOT NULL;

INSERT INTO appointment_events
       (appointment_id, event_type, occurred_at, actor_account_id, actor_role)
SELECT a.id, 'COMPLETED', a.completed_at, a.physiotherapist_id, 'ROLE_PHYSIO'
  FROM appointments a
 WHERE a.completed_at IS NOT NULL;

INSERT INTO appointment_events
       (appointment_id, event_type, occurred_at, cancel_reason)
SELECT a.id, 'CANCELLED', a.cancelled_at, a.cancel_reason
  FROM appointments a
 WHERE a.cancelled_at IS NOT NULL;

INSERT INTO appointment_events
       (appointment_id, event_type, occurred_at, actor_account_id, actor_role)
SELECT a.id, 'NO_SHOW', a.updated_at, a.physiotherapist_id, 'ROLE_PHYSIO'
  FROM appointments a
 WHERE a.status = 'NO_SHOW';

-- The replaced side of a reschedule: the moment its (earliest) RESCHEDULE child was
-- created, by whoever created that child.
INSERT INTO appointment_events
       (appointment_id, event_type, occurred_at, actor_account_id, actor_role,
        related_appointment_id)
SELECT a.id, 'RESCHEDULED', c.created_at, c.booked_by_account_id, acc.role, c.id
  FROM appointments a
  JOIN LATERAL (
        SELECT child.id, child.created_at, child.booked_by_account_id
          FROM appointments child
         WHERE child.source_appointment_id = a.id
           AND child.child_kind = 'RESCHEDULE'
         ORDER BY child.created_at
         LIMIT 1
       ) c ON TRUE
  LEFT JOIN accounts acc ON acc.id = c.booked_by_account_id
 WHERE a.status = 'RESCHEDULED';
