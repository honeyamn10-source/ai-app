BEGIN;
CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TYPE user_role AS ENUM ('user','admin','support');
CREATE TYPE message_role AS ENUM ('system','user','assistant','tool');
CREATE TYPE subscription_status AS ENUM ('pending','active','grace','cancelled','expired','refunded');

CREATE TABLE users (id uuid PRIMARY KEY DEFAULT gen_random_uuid(), email text NOT NULL UNIQUE, role user_role NOT NULL DEFAULT 'user', password_hash text, deleted_at timestamptz, created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE user_profiles (user_id uuid PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE, display_name text NOT NULL, avatar_url text, locale text NOT NULL DEFAULT 'en', memory_enabled boolean NOT NULL DEFAULT false, preferences jsonb NOT NULL DEFAULT '{}');
CREATE TABLE oauth_accounts (id uuid PRIMARY KEY DEFAULT gen_random_uuid(), user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE, provider text NOT NULL, provider_subject text NOT NULL, metadata jsonb NOT NULL DEFAULT '{}', created_at timestamptz NOT NULL DEFAULT now(), UNIQUE(provider, provider_subject));
CREATE TABLE devices (id uuid PRIMARY KEY DEFAULT gen_random_uuid(), user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE, name text NOT NULL, platform text NOT NULL, last_seen_at timestamptz NOT NULL DEFAULT now(), revoked_at timestamptz);
CREATE TABLE sessions (id uuid PRIMARY KEY DEFAULT gen_random_uuid(), user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE, device_id uuid REFERENCES devices(id) ON DELETE SET NULL, refresh_token_hash text NOT NULL, expires_at timestamptz NOT NULL, revoked_at timestamptz, rotated_from uuid REFERENCES sessions(id), created_at timestamptz NOT NULL DEFAULT now());

CREATE TABLE ai_providers (id uuid PRIMARY KEY DEFAULT gen_random_uuid(), slug text NOT NULL UNIQUE, display_name text NOT NULL, kind text NOT NULL, base_url text, capabilities jsonb NOT NULL DEFAULT '{}', enabled boolean NOT NULL DEFAULT true, created_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE ai_provider_credentials (id uuid PRIMARY KEY DEFAULT gen_random_uuid(), user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE, provider_id uuid NOT NULL REFERENCES ai_providers(id), encrypted_secret bytea NOT NULL, key_version integer NOT NULL DEFAULT 1, suffix text NOT NULL, created_at timestamptz NOT NULL DEFAULT now(), rotated_at timestamptz);
CREATE TABLE ai_models (id uuid PRIMARY KEY DEFAULT gen_random_uuid(), provider_id uuid NOT NULL REFERENCES ai_providers(id) ON DELETE CASCADE, model_id text NOT NULL, display_name text NOT NULL, context_window integer, capabilities jsonb NOT NULL DEFAULT '{}', input_price numeric(16,8), output_price numeric(16,8), enabled boolean NOT NULL DEFAULT true, updated_at timestamptz NOT NULL DEFAULT now(), UNIQUE(provider_id, model_id));
CREATE TABLE user_provider_connections (id uuid PRIMARY KEY DEFAULT gen_random_uuid(), user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE, provider_id uuid NOT NULL REFERENCES ai_providers(id), credential_id uuid REFERENCES ai_provider_credentials(id) ON DELETE SET NULL, custom_base_url text, default_model_id uuid REFERENCES ai_models(id) ON DELETE SET NULL, enabled boolean NOT NULL DEFAULT true, created_at timestamptz NOT NULL DEFAULT now());

CREATE TABLE projects (id uuid PRIMARY KEY DEFAULT gen_random_uuid(), owner_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE, name text NOT NULL, description text NOT NULL DEFAULT '', instructions text NOT NULL DEFAULT '', preferred_model_id uuid REFERENCES ai_models(id), deleted_at timestamptz, created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE project_members (project_id uuid NOT NULL REFERENCES projects(id) ON DELETE CASCADE, user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE, role text NOT NULL CHECK (role IN ('owner','editor','viewer')), created_at timestamptz NOT NULL DEFAULT now(), PRIMARY KEY(project_id,user_id));
CREATE TABLE project_settings (project_id uuid PRIMARY KEY REFERENCES projects(id) ON DELETE CASCADE, enabled_tools jsonb NOT NULL DEFAULT '[]', memory jsonb NOT NULL DEFAULT '{}', settings jsonb NOT NULL DEFAULT '{}');
CREATE TABLE conversations (id uuid PRIMARY KEY DEFAULT gen_random_uuid(), user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE, project_id uuid REFERENCES projects(id) ON DELETE SET NULL, title text NOT NULL, provider_connection_id uuid REFERENCES user_provider_connections(id) ON DELETE SET NULL, model_id uuid REFERENCES ai_models(id) ON DELETE SET NULL, pinned boolean NOT NULL DEFAULT false, temporary boolean NOT NULL DEFAULT false, archived_at timestamptz, deleted_at timestamptz, created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE conversation_messages (id uuid PRIMARY KEY DEFAULT gen_random_uuid(), conversation_id uuid NOT NULL REFERENCES conversations(id) ON DELETE CASCADE, role message_role NOT NULL, content jsonb NOT NULL, parent_id uuid REFERENCES conversation_messages(id) ON DELETE SET NULL, status text NOT NULL DEFAULT 'complete', citations jsonb NOT NULL DEFAULT '[]', token_usage jsonb NOT NULL DEFAULT '{}', created_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE conversation_branches (id uuid PRIMARY KEY DEFAULT gen_random_uuid(), conversation_id uuid NOT NULL REFERENCES conversations(id) ON DELETE CASCADE, root_message_id uuid NOT NULL REFERENCES conversation_messages(id) ON DELETE CASCADE, name text, created_at timestamptz NOT NULL DEFAULT now());

CREATE TABLE files (id uuid PRIMARY KEY DEFAULT gen_random_uuid(), user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE, project_id uuid REFERENCES projects(id) ON DELETE SET NULL, object_key text NOT NULL UNIQUE, original_name text NOT NULL, mime_type text NOT NULL, byte_size bigint NOT NULL CHECK (byte_size >= 0), sha256 text NOT NULL, status text NOT NULL, deleted_at timestamptz, created_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE file_chunks (id uuid PRIMARY KEY DEFAULT gen_random_uuid(), file_id uuid NOT NULL REFERENCES files(id) ON DELETE CASCADE, chunk_index integer NOT NULL, content text NOT NULL, token_count integer, metadata jsonb NOT NULL DEFAULT '{}', UNIQUE(file_id,chunk_index));
CREATE TABLE file_embeddings (chunk_id uuid PRIMARY KEY REFERENCES file_chunks(id) ON DELETE CASCADE, model text NOT NULL, embedding vector(1536) NOT NULL, created_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE knowledge_bases (id uuid PRIMARY KEY DEFAULT gen_random_uuid(), user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE, project_id uuid REFERENCES projects(id) ON DELETE CASCADE, name text NOT NULL, configuration jsonb NOT NULL DEFAULT '{}', created_at timestamptz NOT NULL DEFAULT now());

CREATE TABLE tools (id uuid PRIMARY KEY DEFAULT gen_random_uuid(), slug text NOT NULL UNIQUE, description text NOT NULL, input_schema jsonb NOT NULL, risk text NOT NULL, enabled boolean NOT NULL DEFAULT true);
CREATE TABLE tool_permissions (id uuid PRIMARY KEY DEFAULT gen_random_uuid(), user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE, project_id uuid REFERENCES projects(id) ON DELETE CASCADE, tool_id uuid NOT NULL REFERENCES tools(id) ON DELETE CASCADE, permission text NOT NULL CHECK (permission IN ('deny','ask','allow')), UNIQUE(user_id,project_id,tool_id));
CREATE TABLE tool_executions (id uuid PRIMARY KEY DEFAULT gen_random_uuid(), user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE, tool_id uuid NOT NULL REFERENCES tools(id), conversation_id uuid REFERENCES conversations(id) ON DELETE SET NULL, input_redacted jsonb NOT NULL, output_summary jsonb, status text NOT NULL, started_at timestamptz NOT NULL DEFAULT now(), finished_at timestamptz);

CREATE TABLE subscriptions (id uuid PRIMARY KEY DEFAULT gen_random_uuid(), user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE, platform text NOT NULL, product_id text NOT NULL, purchase_token_encrypted bytea, status subscription_status NOT NULL, starts_at timestamptz, expires_at timestamptz, updated_at timestamptz NOT NULL DEFAULT now(), UNIQUE(platform,purchase_token_encrypted));
CREATE TABLE subscription_events (id uuid PRIMARY KEY DEFAULT gen_random_uuid(), subscription_id uuid REFERENCES subscriptions(id) ON DELETE SET NULL, event_type text NOT NULL, provider_event_id text UNIQUE, payload_redacted jsonb NOT NULL, created_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE entitlements (id uuid PRIMARY KEY DEFAULT gen_random_uuid(), user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE, key text NOT NULL, source text NOT NULL, expires_at timestamptz, UNIQUE(user_id,key));
CREATE TABLE usage_records (id uuid PRIMARY KEY DEFAULT gen_random_uuid(), user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE, provider_id uuid REFERENCES ai_providers(id), model_id uuid REFERENCES ai_models(id), kind text NOT NULL, units bigint NOT NULL CHECK (units >= 0), estimated_cost numeric(16,8), occurred_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE audit_logs (id uuid PRIMARY KEY DEFAULT gen_random_uuid(), actor_user_id uuid REFERENCES users(id) ON DELETE SET NULL, action text NOT NULL, target_type text, target_id uuid, metadata jsonb NOT NULL DEFAULT '{}', occurred_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE security_events (id uuid PRIMARY KEY DEFAULT gen_random_uuid(), user_id uuid REFERENCES users(id) ON DELETE SET NULL, severity text NOT NULL, event_type text NOT NULL, ip_hash text, metadata jsonb NOT NULL DEFAULT '{}', occurred_at timestamptz NOT NULL DEFAULT now());

CREATE INDEX idx_sessions_user_active ON sessions(user_id, expires_at) WHERE revoked_at IS NULL;
CREATE INDEX idx_connections_user ON user_provider_connections(user_id);
CREATE INDEX idx_conversations_user_updated ON conversations(user_id, updated_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_messages_conversation_time ON conversation_messages(conversation_id, created_at);
CREATE INDEX idx_projects_owner ON projects(owner_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_files_user_project ON files(user_id, project_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_embeddings_vector ON file_embeddings USING hnsw (embedding vector_cosine_ops);
CREATE INDEX idx_usage_user_time ON usage_records(user_id, occurred_at DESC);
CREATE INDEX idx_audit_actor_time ON audit_logs(actor_user_id, occurred_at DESC);
CREATE INDEX idx_security_time ON security_events(occurred_at DESC);
COMMIT;

