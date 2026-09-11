CREATE TABLE organization (
    id UUID PRIMARY KEY,
    name TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE person (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    registration_number TEXT NOT NULL,
    name TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (organization_id, registration_number)
);

CREATE TABLE finger (
    id UUID PRIMARY KEY,
    person_id UUID NOT NULL REFERENCES person(id),
    position SMALLINT NOT NULL CHECK (position BETWEEN 1 AND 10),
    UNIQUE (person_id, position)
);

CREATE TABLE fingerprint_sample (
    id UUID PRIMARY KEY,
    finger_id UUID NOT NULL REFERENCES finger(id),
    dpi INTEGER NOT NULL CHECK (dpi > 0),
    captured_at TIMESTAMPTZ NOT NULL,
    capture_device TEXT NOT NULL,
    image_object_key TEXT NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX fingerprint_sample_finger_idx ON fingerprint_sample(finger_id);

CREATE TABLE fingerprint_template (
    id UUID PRIMARY KEY,
    sample_id UUID NOT NULL REFERENCES fingerprint_sample(id),
    sourceafis_version TEXT NOT NULL,
    template BYTEA NOT NULL CHECK (octet_length(template) > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (sample_id, sourceafis_version)
);

CREATE TABLE comparison_history (
    id UUID PRIMARY KEY,
    probe_template_id UUID NOT NULL REFERENCES fingerprint_template(id),
    candidate_template_id UUID NOT NULL REFERENCES fingerprint_template(id),
    sourceafis_version TEXT NOT NULL,
    score DOUBLE PRECISION NOT NULL CHECK (score >= 0 AND score < 'Infinity'::double precision),
    threshold DOUBLE PRECISION NOT NULL CHECK (threshold > 0 AND threshold < 'Infinity'::double precision),
    threshold_version TEXT NOT NULL,
    matched BOOLEAN NOT NULL,
    compared_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (matched = (score >= threshold))
);
CREATE INDEX comparison_history_probe_idx ON comparison_history(probe_template_id, compared_at);
CREATE INDEX comparison_history_candidate_idx ON comparison_history(candidate_template_id, compared_at);
