-- EventBob Service Registry Schema
-- Version 1: Core registry tables for capability-based routing

-- =============================================================================
-- 1. DEPLOYMENT STATES
-- =============================================================================

CREATE TYPE deployment_state AS ENUM ('green', 'blue', 'gray', 'retired');

CREATE TYPE instance_status AS ENUM ('healthy', 'unhealthy', 'draining', 'terminated');

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

  -- Deployment tracking
  deployment_version INTEGER NOT NULL,
  deployment_state deployment_state NOT NULL,
  jar_version VARCHAR(100) NOT NULL,  -- e.g., "1.2.3"

  -- Rollout lifecycle timestamps
  registered_at TIMESTAMP NOT NULL DEFAULT NOW(),
  became_blue_at TIMESTAMP,
  became_green_at TIMESTAMP,
  became_gray_at TIMESTAMP,
  retired_at TIMESTAMP,

  -- Rollout configuration (only used when state = blue)
  rollout_started_at TIMESTAMP,
  rollout_policy JSONB DEFAULT '{"strategy": "linear", "step_pct": 10, "interval_minutes": 6, "cutover_minutes": 60}',

  -- Metadata
  metadata JSONB,

  -- Constraints
  CONSTRAINT chk_capability_version_positive CHECK (capability_version > 0),
  CONSTRAINT chk_deployment_version_positive CHECK (deployment_version > 0),

  -- Idempotency: same capability can only exist once per deployment version
  UNIQUE(service_name, capability, capability_version, method, path_pattern, deployment_version)
);

-- Only one green deployment per routing key
CREATE UNIQUE INDEX idx_single_green_capability
  ON service_capabilities(service_name, capability, method, path_pattern)
  WHERE deployment_state = 'green';

-- Only one blue deployment per routing key
CREATE UNIQUE INDEX idx_single_blue_capability
  ON service_capabilities(service_name, capability, method, path_pattern)
  WHERE deployment_state = 'blue';

-- Fast routing queries (green + blue only)
CREATE INDEX idx_active_capabilities
  ON service_capabilities(service_name, capability, method, path_pattern, deployment_state)
  WHERE deployment_state IN ('green', 'blue');

-- Cleanup candidates (gray versions older than grace period)
CREATE INDEX idx_gray_capabilities
  ON service_capabilities(service_name, became_gray_at)
  WHERE deployment_state = 'gray';


-- =============================================================================
-- 3. SERVICE INSTANCES (Which physical instances exist)
-- =============================================================================

CREATE TABLE service_instances (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

  -- Identity
  macro_name VARCHAR(255) NOT NULL,
  instance_id VARCHAR(255) NOT NULL,  -- e.g., "messages-readonly-pod-1"
  endpoint VARCHAR(500) NOT NULL,  -- e.g., "http://10.0.1.5:8080"

  -- Deployment tracking
  deployment_version INTEGER NOT NULL,

  -- Health tracking
  status instance_status NOT NULL DEFAULT 'healthy',
  last_heartbeat TIMESTAMP NOT NULL DEFAULT NOW(),

  -- Lifecycle
  registered_at TIMESTAMP NOT NULL DEFAULT NOW(),
  terminated_at TIMESTAMP,

  -- Metadata
  metadata JSONB,

  CONSTRAINT chk_deployment_version_positive CHECK (deployment_version > 0),

  -- One instance ID per macro
  UNIQUE(macro_name, instance_id)
);

-- Fast health checks
CREATE INDEX idx_healthy_instances
  ON service_instances(deployment_version, status, last_heartbeat)
  WHERE status = 'healthy' AND terminated_at IS NULL;

-- Find instances by macro
CREATE INDEX idx_instances_by_macro
  ON service_instances(macro_name, deployment_version, status);


-- =============================================================================
-- 4. INSTANCE CAPABILITIES (Join: which instances provide which capabilities)
-- =============================================================================

CREATE TABLE instance_capabilities (
  instance_id UUID NOT NULL REFERENCES service_instances(id) ON DELETE CASCADE,
  capability_id UUID NOT NULL REFERENCES service_capabilities(id) ON DELETE CASCADE,

  linked_at TIMESTAMP NOT NULL DEFAULT NOW(),

  PRIMARY KEY (instance_id, capability_id)
);

-- Find all instances for a capability (load balancing)
CREATE INDEX idx_capability_instances
  ON instance_capabilities(capability_id);

-- Find all capabilities for an instance (introspection)
CREATE INDEX idx_instance_capabilities
  ON instance_capabilities(instance_id);


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

CREATE TRIGGER trigger_instances_version
  AFTER INSERT OR UPDATE OR DELETE ON service_instances
  FOR EACH STATEMENT EXECUTE FUNCTION bump_registry_version();


-- =============================================================================
-- 6. CONFLICT DETECTION VIEW
-- =============================================================================

CREATE VIEW capability_conflicts AS
SELECT
  sc1.service_name,
  sc1.capability,
  sc1.method,
  sc1.path_pattern,
  sc1.capability_version AS version_1,
  sc2.capability_version AS version_2,
  sc1.deployment_version AS deployment_1,
  sc2.deployment_version AS deployment_2,
  sc1.deployment_state AS state_1,
  sc2.deployment_state AS state_2
FROM service_capabilities sc1
JOIN service_capabilities sc2 ON
  sc1.service_name = sc2.service_name AND
  sc1.capability = sc2.capability AND
  sc1.method = sc2.method AND
  sc1.path_pattern = sc2.path_pattern AND
  sc1.deployment_version != sc2.deployment_version AND
  sc1.capability_version != sc2.capability_version
WHERE
  sc1.deployment_state IN ('green', 'blue') AND
  sc2.deployment_state IN ('green', 'blue');


-- =============================================================================
-- 7. DEPLOYMENT HISTORY (Audit log)
-- =============================================================================

CREATE TABLE deployment_history (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

  service_name VARCHAR(255) NOT NULL,
  macro_name VARCHAR(255) NOT NULL,
  deployment_version INTEGER NOT NULL,

  event_type VARCHAR(50) NOT NULL,  -- 'deployed', 'rollout_started', 'rollout_completed', 'rolled_back', 'retired'
  from_state deployment_state,
  to_state deployment_state,

  triggered_by VARCHAR(255),  -- instance_id or 'system' or 'operator'
  reason TEXT,

  occurred_at TIMESTAMP NOT NULL DEFAULT NOW(),

  metadata JSONB
);

CREATE INDEX idx_deployment_history_service
  ON deployment_history(service_name, occurred_at DESC);

CREATE INDEX idx_deployment_history_macro
  ON deployment_history(macro_name, occurred_at DESC);
