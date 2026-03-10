-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ============================================================
-- USERS
-- ============================================================
CREATE TABLE IF NOT EXISTS users (
  id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
  username      VARCHAR(30)  UNIQUE NOT NULL,
  email         VARCHAR(255) UNIQUE NOT NULL,
  password_hash TEXT         NOT NULL,
  avatar_url    VARCHAR(512),
  bio           TEXT,
  status        VARCHAR(20)  DEFAULT 'offline',
  last_seen_at  TIMESTAMPTZ,
  role          VARCHAR(20)  DEFAULT 'user' CHECK (role IN ('user', 'admin')),
  banned_at     TIMESTAMPTZ,
  created_at    TIMESTAMPTZ  DEFAULT NOW(),
  updated_at    TIMESTAMPTZ  DEFAULT NOW()
);

-- ALTER for live DB (idempotent)
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='users' AND column_name='role') THEN
    ALTER TABLE users ADD COLUMN role VARCHAR(20) DEFAULT 'user' CHECK (role IN ('user', 'admin'));
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='users' AND column_name='banned_at') THEN
    ALTER TABLE users ADD COLUMN banned_at TIMESTAMPTZ;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='users' AND column_name='fcm_token') THEN
    ALTER TABLE users ADD COLUMN fcm_token TEXT;
  END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_users_email    ON users(email);
CREATE INDEX IF NOT EXISTS idx_users_username ON users(username);

-- ============================================================
-- CONVERSATIONS
-- ============================================================
CREATE TABLE IF NOT EXISTS conversations (
  id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
  name        VARCHAR(255),
  type        VARCHAR(10)  NOT NULL CHECK (type IN ('direct', 'group')),
  avatar_url  VARCHAR(512),
  created_by  UUID         REFERENCES users(id) ON DELETE SET NULL,
  created_at  TIMESTAMPTZ  DEFAULT NOW(),
  updated_at  TIMESTAMPTZ  DEFAULT NOW()
);

-- ============================================================
-- PARTICIPANTS
-- ============================================================
CREATE TABLE IF NOT EXISTS conversation_participants (
  id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  conversation_id  UUID        NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
  user_id          UUID        NOT NULL REFERENCES users(id)         ON DELETE CASCADE,
  role             VARCHAR(20) DEFAULT 'member' CHECK (role IN ('admin', 'member')),
  last_read_at     TIMESTAMPTZ,
  joined_at        TIMESTAMPTZ DEFAULT NOW(),
  UNIQUE(conversation_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_cp_user_id ON conversation_participants(user_id);
CREATE INDEX IF NOT EXISTS idx_cp_conv_id ON conversation_participants(conversation_id);

-- ============================================================
-- MESSAGES
-- ============================================================
CREATE TABLE IF NOT EXISTS messages (
  id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  conversation_id  UUID        NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
  user_id          UUID        REFERENCES users(id) ON DELETE SET NULL,
  type             VARCHAR(20) DEFAULT 'text'
                   CHECK (type IN ('text', 'image', 'video', 'voice', 'file')),
  content          TEXT,
  reply_to_id      UUID        REFERENCES messages(id) ON DELETE SET NULL,
  edited_at        TIMESTAMPTZ,
  deleted_at       TIMESTAMPTZ,
  created_at       TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_messages_conv ON messages(conversation_id, created_at DESC)
  WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_messages_user ON messages(user_id);

-- ============================================================
-- FILE ATTACHMENTS (Phase 3 — prepared now)
-- ============================================================
CREATE TABLE IF NOT EXISTS file_attachments (
  id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
  message_id        UUID         NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
  file_name         VARCHAR(512) NOT NULL,
  file_size_bytes   BIGINT       NOT NULL,
  mime_type         VARCHAR(100) NOT NULL,
  file_type         VARCHAR(20)  NOT NULL CHECK (file_type IN ('image','video','voice','file')),
  s3_key            VARCHAR(512) NOT NULL,
  thumbnail_s3_key  VARCHAR(512),
  duration_seconds  DECIMAL,
  waveform_data     JSONB,
  image_width       INT,
  image_height      INT,
  created_at        TIMESTAMPTZ  DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_attachments_message ON file_attachments(message_id);

-- ============================================================
-- BLOCKED USERS
-- ============================================================
CREATE TABLE IF NOT EXISTS blocked_users (
  id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  blocker_id  UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  blocked_id  UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  created_at  TIMESTAMPTZ DEFAULT NOW(),
  UNIQUE(blocker_id, blocked_id)
);

CREATE INDEX IF NOT EXISTS idx_blocked_blocker ON blocked_users(blocker_id);

-- ============================================================
-- CALLS (history)
-- ============================================================
CREATE TABLE IF NOT EXISTS calls (
  id              UUID         PRIMARY KEY,
  caller_id       UUID         REFERENCES users(id) ON DELETE SET NULL,
  callee_id       UUID         REFERENCES users(id) ON DELETE SET NULL,
  call_type       VARCHAR(10)  NOT NULL CHECK (call_type IN ('audio', 'video')),
  end_reason      VARCHAR(50),
  duration_sec    INT,
  started_at      TIMESTAMPTZ  NOT NULL,
  answered_at     TIMESTAMPTZ,
  ended_at        TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_calls_caller ON calls(caller_id, started_at DESC);
CREATE INDEX IF NOT EXISTS idx_calls_callee ON calls(callee_id, started_at DESC);

-- ============================================================
-- Extend messages.type to include 'call' (idempotent)
-- ============================================================
DO $$
BEGIN
  EXECUTE (
    SELECT 'ALTER TABLE messages DROP CONSTRAINT ' || quote_ident(conname)
    FROM pg_constraint
    WHERE conrelid = 'messages'::regclass
      AND contype = 'c'
      AND pg_get_constraintdef(oid) LIKE '%type%'
    LIMIT 1
  );
EXCEPTION WHEN OTHERS THEN NULL;
END $$;
DO $$ BEGIN
  ALTER TABLE messages ADD CONSTRAINT messages_type_check
    CHECK (type IN ('text', 'image', 'video', 'voice', 'file', 'call', 'system'));
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

-- ============================================================
-- PASSWORD RESET TOKENS
-- ============================================================
CREATE TABLE IF NOT EXISTS password_reset_tokens (
  id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id    UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  token_hash TEXT        NOT NULL UNIQUE,
  expires_at TIMESTAMPTZ NOT NULL,
  used_at    TIMESTAMPTZ,
  created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_prt_token ON password_reset_tokens(token_hash);
CREATE INDEX IF NOT EXISTS idx_prt_user  ON password_reset_tokens(user_id);

-- ============================================================
-- AUTO updated_at TRIGGER
-- ============================================================
CREATE OR REPLACE FUNCTION update_updated_at()
RETURNS TRIGGER AS $$
BEGIN
  NEW.updated_at = NOW();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS users_updated_at ON users;
CREATE TRIGGER users_updated_at
  BEFORE UPDATE ON users
  FOR EACH ROW EXECUTE FUNCTION update_updated_at();

DROP TRIGGER IF EXISTS conversations_updated_at ON conversations;
CREATE TRIGGER conversations_updated_at
  BEFORE UPDATE ON conversations
  FOR EACH ROW EXECUTE FUNCTION update_updated_at();
