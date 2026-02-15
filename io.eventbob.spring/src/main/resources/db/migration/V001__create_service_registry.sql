-- EventBob Service Registry Schema
-- Version 1: Core registry tables for capability-based routing

-- =============================================================================
-- 1. ENUMS
-- =============================================================================

CREATE TYPE capability_type AS ENUM ('READ', 'WRITE', 'ADMIN');


-- =============================================================================
-- 2. SERVICE CAPABILITIES (What operations are available)
-- =============================================================================

CREATE TABLE service_capabilities (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

  -- Identity
  service_name VARCHAR(255) NOT NULL,
  capability capability_type NOT NULL,
  capability_version INTEGER NOT NULL,

  -- Operation definition
  method VARCHAR(10) NOT NULL,  -- GET, POST, PUT, DELETE, etc.
  path_pattern VARCHAR(500) NOT NULL,  -- /content, /bulk-content/{ids}

  -- Metadata
  metadata JSONB,

  -- Constraints
  CONSTRAINT chk_capability_version_positive CHECK (capability_version > 0),

  -- Routing key uniqueness: each operation can only be registered once
  CONSTRAINT uq_capability_routing_key
    UNIQUE(service_name, capability, capability_version, method, path_pattern)
);

-- Fast routing queries by service + capability
CREATE INDEX idx_capabilities_by_service
  ON service_capabilities(service_name, capability);


-- =============================================================================
-- 3. SERVICE MACROLITHS (Which logical deployment units exist)
-- =============================================================================

CREATE TABLE service_macroliths (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

  -- Identity
  macrolith_name VARCHAR(255) NOT NULL UNIQUE,
  endpoint VARCHAR(500) NOT NULL,  -- Logical service URL (e.g., "http://messages-service")

  -- Metadata
  metadata JSONB
);

-- Fast lookup by macrolith name
CREATE INDEX idx_macroliths_by_name
  ON service_macroliths(macrolith_name);


-- =============================================================================
-- 4. MACROLITH CAPABILITIES (Join: which macroliths provide which capabilities)
-- =============================================================================

CREATE TABLE macrolith_capabilities (
  macrolith_id UUID NOT NULL REFERENCES service_macroliths(id) ON DELETE CASCADE,
  capability_id UUID NOT NULL REFERENCES service_capabilities(id) ON DELETE CASCADE,

  linked_at TIMESTAMP NOT NULL DEFAULT NOW(),

  PRIMARY KEY (macrolith_id, capability_id)
);

-- Find all macroliths for a capability (routing)
CREATE INDEX idx_capability_macroliths
  ON macrolith_capabilities(capability_id);

-- Find all capabilities for a macrolith (introspection)
CREATE INDEX idx_macrolith_capabilities
  ON macrolith_capabilities(macrolith_id);


-- =============================================================================
-- 5. REGISTRY VERSION (Cache invalidation)
-- =============================================================================

CREATE TABLE registry_version (
  id INTEGER PRIMARY KEY DEFAULT 1,
  version BIGINT NOT NULL DEFAULT 1,
  updated_at TIMESTAMP NOT NULL DEFAULT NOW(),

  CONSTRAINT chk_single_row CHECK (id = 1)
);

-- Initialize with version 1
INSERT INTO registry_version (id, version) VALUES (1, 1);

-- Function to bump version (called after any registry change)
CREATE OR REPLACE FUNCTION bump_registry_version()
RETURNS TRIGGER AS $$
BEGIN
  UPDATE registry_version SET version = version + 1, updated_at = NOW() WHERE id = 1;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Triggers to auto-bump version
CREATE TRIGGER trigger_capabilities_version
  AFTER INSERT OR UPDATE OR DELETE ON service_capabilities
  FOR EACH STATEMENT EXECUTE FUNCTION bump_registry_version();

CREATE TRIGGER trigger_macroliths_version
  AFTER INSERT OR UPDATE OR DELETE ON service_macroliths
  FOR EACH STATEMENT EXECUTE FUNCTION bump_registry_version();
