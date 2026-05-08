-- ============================================================
-- 数据库初始化脚本
-- 用途: 行级安全策略（RLS）落地 + 索引优化
-- 说明: 首次部署执行一次即可，后续增量修改请新建 .sql 文件
-- ============================================================

-- ==================== 1. 索引优化 ====================

-- conversationId 查询加速（已有则跳过）
CREATE INDEX IF NOT EXISTS idx_conversations_conv_id ON conversations (conversation_id);
-- userId 分页查询加速
CREATE INDEX IF NOT EXISTS idx_conversations_user_id ON conversations (user_id);
-- conversation_messages 按对话查询加速
CREATE INDEX IF NOT EXISTS idx_conv_messages_conv_id ON conversation_messages (conversation_id);


-- ==================== 2. 行级安全策略（RLS） ====================

-- 开启 RLS（幂等：RLS 已开启则忽略）
ALTER TABLE conversations     ENABLE ROW LEVEL SECURITY;
ALTER TABLE conversation_messages ENABLE ROW LEVEL SECURITY;

-- conversations 表策略：用户仅可见自身数据
-- 使用 current_setting(_, true) 避免未设置时抛错
DROP POLICY IF EXISTS user_isolation ON conversations;
CREATE POLICY user_isolation ON conversations
    FOR ALL
    USING (user_id = COALESCE(NULLIF(current_setting('app.current_user_id', true), ''), '0')::bigint);

-- conversation_messages 表策略：用户仅可见自身对话的消息
-- 通过子查询关联 conversations 表进行归属判断
DROP POLICY IF EXISTS msg_isolation ON conversation_messages;
CREATE POLICY msg_isolation ON conversation_messages
    FOR ALL
    USING (
        EXISTS (
            SELECT 1 FROM conversations
            WHERE conversations.conversation_id = conversation_messages.conversation_id
              AND conversations.user_id = COALESCE(NULLIF(current_setting('app.current_user_id', true), ''), '0')::bigint
        )
    );

COMMENT ON POLICY user_isolation ON conversations         IS 'RLS: 用户仅可操作自身的对话记录';
COMMENT ON POLICY msg_isolation  ON conversation_messages  IS 'RLS: 用户仅可访问自身对话的消息';
