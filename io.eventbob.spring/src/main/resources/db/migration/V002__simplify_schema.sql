-- EventBob Service Registry Schema Simplification
-- Version 2: Remove deployment state machinery, align with macro-based model

-- =============================================================================
-- 1. DROP DEPLOYMENT STATE MACHINERY
-- =============================================================================

-- Drop views and tables that depend on deployment state
DROP VIEW IF EXISTS capability_conflicts;
DROP TABLE IF EXISTS deployment_history;

-- Drop deployment state indexes
DROP INDEX IF EXISTS idx_single_green_capability;
DROP INDEX IF EXISTS idx_single_blue_capability;
DROP INDEX IF EXISTS idx_active_capabilities;
DROP INDEX IF EXISTS idx_gray_capabilities;
DROP INDEX IF EXISTS idx_healthy_instances;

-- Remove deployment state columns from service_capabilities
ALTER TABLE service_capabilities
  DROP COLUMN IF EXISTS deployment_version,
  DROP COLUMN IF EXISTS deployment_state,
  DROP COLUMN IF EXISTS jar_version,
  DROP COLUMN IF EXISTS became_blue_at,
  DROP COLUMN IF EXISTS became_green_at,
  DROP COLUMN IF EXISTS became_gray_at,
  DROP COLUMN IF EXISTS retired_at,
  DROP COLUMN IF EXISTS rollout_started_at,
  DROP COLUMN IF EXISTS rollout_policy;

-- Simplify service_capabilities unique constraint
-- Capability is now uniquely identified by routing key components only
ALTER TABLE service_capabilities
  DROP CONSTRAINT IF EXISTS service_capabilities_service_name_capability_capability_version_key;

-- Add new unique constraint (routing key = service_name + capability + method + path_pattern + version)
ALTER TABLE service_capabilities
  ADD CONSTRAINT uq_capability_routing_key
    UNIQUE(service_name, capability, capability_version, method, path_pattern);

-- Create index for fast routing queries
CREATE INDEX idx_capability_routing
  ON service_capabilities(service_name, capability, method, path_pattern);

-- =============================================================================
-- 2. CONVERT SERVICE_INSTANCES TO MACRO-BASED MODEL
-- =============================================================================

-- Remove instance tracking columns from service_instances
ALTER TABLE service_instances
  DROP COLUMN IF EXISTS instance_id,
  DROP COLUMN IF EXISTS deployment_version,
  DROP COLUMN IF EXISTS status,
  DROP COLUMN IF EXISTS last_heartbeat,
  DROP COLUMN IF EXISTS terminated_at;

-- Rename to service_macros (table now tracks macros, not instances)
ALTER TABLE service_instances RENAME TO service_macros;

-- Drop old constraints
ALTER TABLE service_macros
  DROP CONSTRAINT IF EXISTS service_instances_macro_name_instance_id_key,
  DROP CONSTRAINT IF EXISTS chk_deployment_version_positive;

-- Add new unique constraint (one entry per macro)
ALTER TABLE service_macros
  ADD CONSTRAINT uq_macro_name UNIQUE(macro_name);

-- Recreate index for macro lookups
DROP INDEX IF EXISTS idx_instances_by_macro;
CREATE INDEX idx_macros_by_name ON service_macros(macro_name);

-- =============================================================================
-- 3. RENAME JUNCTION TABLE
-- =============================================================================

-- Rename instance_capabilities to macro_capabilities
ALTER TABLE instance_capabilities RENAME TO macro_capabilities;

-- Update foreign key constraint name (points to service_macros now)
ALTER TABLE macro_capabilities
  DROP CONSTRAINT IF EXISTS instance_capabilities_instance_id_fkey;

ALTER TABLE macro_capabilities
  ADD CONSTRAINT macro_capabilities_macro_id_fkey
    FOREIGN KEY (instance_id) REFERENCES service_macros(id) ON DELETE CASCADE;

-- Rename instance_id column to macro_id
ALTER TABLE macro_capabilities RENAME COLUMN instance_id TO macro_id;

-- Recreate indexes with new names
DROP INDEX IF EXISTS idx_capability_instances;
DROP INDEX IF EXISTS idx_instance_capabilities;
CREATE INDEX idx_capability_macros ON macro_capabilities(capability_id);
CREATE INDEX idx_macro_capabilities ON macro_capabilities(macro_id);

-- =============================================================================
-- 4. UPDATE REGISTRY VERSION TRIGGER
-- =============================================================================

-- Drop old triggers
DROP TRIGGER IF EXISTS trigger_instances_version ON service_macros;

-- Recreate trigger for service_macros
CREATE TRIGGER trigger_macros_version
  AFTER INSERT OR UPDATE OR DELETE ON service_macros
  FOR EACH STATEMENT EXECUTE FUNCTION bump_registry_version();

-- =============================================================================
-- 5. DROP UNUSED ENUMS
-- =============================================================================

-- Drop deployment state enums (keep capability_type)
DROP TYPE IF EXISTS deployment_state;
DROP TYPE IF EXISTS instance_status;
