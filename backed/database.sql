-- =============================================================================
-- Phase 2 — 多用户基础平台与租户隔离 · PostgreSQL 建表脚本
-- =============================================================================
-- 适用数据库：PostgreSQL 14+
-- 说明：
--   1. 按依赖顺序建表：users → conversations → conversation_messages
--   2. 启用行级安全（RLS），为多租户隔离做数据库层兜底
--   3. updated_at 通过触发器自动更新
--   4. UUID 主键使用 PostgreSQL 原生 gen_random_uuid()
-- =============================================================================


-- =============================================================================
-- 第一部分：工具函数
-- =============================================================================
-- 自动更新 updated_at 字段的触发器函数
CREATE OR REPLACE FUNCTION update_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;


-- =============================================================================
-- 第二部分：users — 用户表
-- =============================================================================
-- 说明：
--   - username / email 唯一索引（通过 UNIQUE 约束自动创建）
--   - role 用于后续扩展管理员等角色
--   - enabled 用于账号冻结（预留）
--   - 外键引用：被 conversations.user_id 引用
-- =============================================================================
CREATE TABLE users (
    id              BIGSERIAL       PRIMARY KEY,
    username        VARCHAR(50)     NOT NULL UNIQUE,
    email           VARCHAR(100)    NOT NULL UNIQUE,
    password_hash   VARCHAR(255)    NOT NULL,               -- BCrypt 加密后的密码
    nickname        VARCHAR(50),                            -- 显示名称，可选
    role            VARCHAR(20)     NOT NULL DEFAULT 'USER'
                                    CHECK (role IN ('USER', 'ADMIN')),
    enabled         BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE users IS '用户表';
COMMENT ON COLUMN users.password_hash IS 'BCrypt 加密密码哈希';
COMMENT ON COLUMN users.role IS '角色: USER 普通用户, ADMIN 管理员';
COMMENT ON COLUMN users.enabled IS '是否启用：false 表示账号被冻结';

-- updated_at 触发器
CREATE TRIGGER users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();

-- RLS：用户只能看到自己的记录
ALTER TABLE users ENABLE ROW LEVEL SECURITY;
CREATE POLICY users_isolation ON users
    FOR ALL
    USING (id = current_setting('app.current_user_id')::bigint);

-- 管理员可以看所有用户（预留）
CREATE POLICY users_admin ON users
    FOR SELECT
    USING (current_setting('app.current_user_role') = 'ADMIN');


-- =============================================================================
-- 第三部分：conversations — 对话/任务表
-- =============================================================================
-- 说明：
--   - conversation_id 是对外暴露的 UUID，id 是内部自增主键
--   - 对外接口全部使用 conversation_id 进行操作
--   - user_id 关联到 users 表
--   - status 标识对话生命周期状态
--   - 索引：user_id（列表查询）、user_id + status（筛选查询）
-- =============================================================================
CREATE TABLE conversations (
    id               BIGSERIAL       PRIMARY KEY,
    conversation_id  VARCHAR(36)     NOT NULL UNIQUE DEFAULT gen_random_uuid()::text,
    user_id          BIGINT          NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title            VARCHAR(200)    NOT NULL DEFAULT '新任务',
    requirement      TEXT,                                -- 用户原始需求
    status           VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE'
                                     CHECK (status IN ('ACTIVE', 'COMPLETED', 'FAILED', 'DELETED')),
    created_at       TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE conversations IS '对话/任务表';
COMMENT ON COLUMN conversations.conversation_id IS '对外暴露的 UUID，业务层使用';
COMMENT ON COLUMN conversations.status IS 'ACTIVE 进行中, COMPLETED 已完成, FAILED 失败, DELETED 已删除';

-- updated_at 触发器
CREATE TRIGGER conversations_updated_at
    BEFORE UPDATE ON conversations
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();

-- 核心索引：用户维度的对话列表查询
CREATE INDEX idx_conversations_user_id ON conversations(user_id);
CREATE INDEX idx_conversations_user_status ON conversations(user_id, status);

-- RLS：用户只能看到自己的对话
ALTER TABLE conversations ENABLE ROW LEVEL SECURITY;
CREATE POLICY conversations_isolation ON conversations
    FOR ALL
    USING (user_id = current_setting('app.current_user_id')::bigint);


-- =============================================================================
-- 第四部分：conversation_messages — 对话消息表
-- =============================================================================
-- 说明：
--   - 每条消息属于一个 conversation，通过 conversation_id 关联
--   - role 标识消息角色：user / assistant / system / tool
--   - message_index 标识消息在对话中的顺序
--   - parent_message_id 支持分支对话（预留，后续 Human-in-the-Loop 能力）
--   - metadata 用 JSONB 存储工具调用、思维链等扩展信息
--   - RLS 策略通过子查询 conversations.user_id 做权限校验
-- =============================================================================
CREATE TABLE conversation_messages (
    id                  BIGSERIAL       PRIMARY KEY,
    conversation_id     VARCHAR(36)     NOT NULL
                                        REFERENCES conversations(conversation_id) ON DELETE CASCADE,
    message_id          VARCHAR(36)     NOT NULL UNIQUE DEFAULT gen_random_uuid()::text,

    -- 消息内容
    role                VARCHAR(20)     NOT NULL
                                        CHECK (role IN ('user', 'assistant', 'system', 'tool')),
    content             TEXT            NOT NULL,

    -- 元数据
    token_count         INTEGER,                            -- Token 消耗数
    model_name          VARCHAR(100),                       -- 生成该消息的模型名称
    metadata            JSONB,                              -- 扩展信息：工具调用、思维链等

    -- 排序和层级
    message_index       INTEGER         NOT NULL,           -- 对话内消息顺序
    parent_message_id   VARCHAR(36),                        -- 父消息 ID（预留：分支对话）

    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    -- 外键约束
    CONSTRAINT fk_parent_message
        FOREIGN KEY (parent_message_id)
        REFERENCES conversation_messages(message_id)
        ON DELETE SET NULL
);

COMMENT ON TABLE conversation_messages IS '对话消息表 — 持久化 ChatMemory';
COMMENT ON COLUMN conversation_messages.message_index IS '消息在对话中的序号，从 0 递增';
COMMENT ON COLUMN conversation_messages.parent_message_id IS '父消息 ID，支持分支对话/人工干预';
COMMENT ON COLUMN conversation_messages.metadata IS 'JSONB 扩展信息：工具调用参数、思维链内容等';

-- 核心索引
CREATE INDEX idx_messages_conversation_id ON conversation_messages(conversation_id);           -- 按对话加载消息
CREATE INDEX idx_messages_conversation_order ON conversation_messages(conversation_id, message_index); -- 有序加载
CREATE INDEX idx_messages_role ON conversation_messages(role);                                 -- 按角色筛选
CREATE INDEX idx_messages_created_at ON conversation_messages(created_at);                     -- 按时间范围查询
CREATE INDEX idx_messages_metadata ON conversation_messages USING GIN (metadata);              -- JSONB 查询

-- RLS：通过 conversations 表关联判断权限
ALTER TABLE conversation_messages ENABLE ROW LEVEL SECURITY;
CREATE POLICY messages_isolation ON conversation_messages
    FOR ALL
    USING (
        conversation_id IN (
            SELECT conversation_id FROM conversations
            WHERE user_id = current_setting('app.current_user_id')::bigint
        )
    );


-- =============================================================================
-- 第五部分：数据库层 RLS 使用说明（重要）
-- =============================================================================
--
-- RLS 需要应用层在每次数据库连接时设置当前用户身份：
--
--   -- 从连接池获取连接后，立即执行：
--   SET app.current_user_id = '123';
--   SET app.current_user_role = 'USER';
--
-- 在 Spring 中，可以通过 DataSource 代理或 Hibernate 拦截器实现：
--
--   @Component
--   public class RlsFilter implements Filter {
--       @Override
--       public void doFilter(ServletRequest request, ...) {
--           // 从 SecurityContext 提取 userId
--           // 通过 JDBC 执行 SET 语句
--       }
--   }
--
-- =============================================================================


-- =============================================================================
-- 第六部分：建表验证查询
-- =============================================================================
-- 建表后运行以下查询确认所有对象已正确创建：
--
--   SELECT table_name FROM information_schema.tables
--   WHERE table_schema = 'public' AND table_type = 'BASE TABLE';
--
--   SELECT tablename, rowsecurity FROM pg_tables
--   WHERE schemaname = 'public' AND tablename IN ('users', 'conversations', 'conversation_messages');
--   -- rowsecurity = true 表示 RLS 已开启
--
--   SELECT event_object_table, trigger_name FROM information_schema.triggers
--   WHERE trigger_schema = 'public';
--   -- 应看到 users_updated_at, conversations_updated_at
-- =============================================================================
