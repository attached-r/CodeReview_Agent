package rj.agent;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import rj.agent.entity.User;
import rj.agent.mapper.UserMapper;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 数据库测试类 - 仅测试 User 表的 CRUD 操作
 */
@SpringBootTest
public class DataBaseTest {

    @Autowired
    private UserMapper userMapper;

    // ==================== User 表测试 ====================

    /**
     * 测试用户单条插入
     */
    @Test
    void testUserInsert() {
        User user = new User();
        user.setUsername("test_admin");
        user.setEmail("test_admin@example.com");
        user.setPasswordHash("$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi");
        user.setNickname("测试管理员");
        user.setRole("ADMIN");
        user.setEnabled(true);
        user.setCreatedAt(OffsetDateTime.now());
        user.setUpdatedAt(OffsetDateTime.now());

        int result = userMapper.insert(user);
        assertEquals(1, result);
        assertNotNull(user.getId());
        System.out.println("✓ 用户插入成功，用户ID: " + user.getId());
    }

    /**
     * 测试用户批量插入
     */
    @Test
    void testUserBatchInsert() {
        List<User> users = new ArrayList<>();

        for (int i = 1; i <= 5; i++) {
            User user = new User();
            user.setUsername("user_" + i);
            user.setEmail("user_" + i + "@example.com");
            user.setPasswordHash("$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi");
            user.setNickname("用户" + i);
            user.setRole("USER");
            user.setEnabled(true);
            user.setCreatedAt(OffsetDateTime.now());
            user.setUpdatedAt(OffsetDateTime.now());
            users.add(user);
        }

        int count = 0;
        for (User user : users) {
            count += userMapper.insert(user);
        }

        assertEquals(5, count);
        System.out.println("✓ 批量插入成功，共插入: " + count + " 条用户记录");
    }

    /**
     * 测试用户根据 ID 查询
     */
    @Test
    void testUserSelectById() {
        User user = createTestUser("query_test", "query_test@example.com");
        Long userId = user.getId();

        User foundUser = userMapper.selectById(userId);
        assertNotNull(foundUser);
        assertEquals("query_test", foundUser.getUsername());
        assertEquals("query_test@example.com", foundUser.getEmail());
        System.out.println("✓ 用户查询成功: " + foundUser.getNickname());
    }

    /**
     * 测试用户条件查询
     */
    @Test
    void testUserSelectByCondition() {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getRole, "ADMIN")
                .eq(User::getEnabled, true)
                .orderByDesc(User::getCreatedAt);

        List<User> admins = userMapper.selectList(wrapper);
        assertNotNull(admins);
        System.out.println("✓ 查询到管理员数量: " + admins.size());

        admins.forEach(user ->
                System.out.println("  管理员: " + user.getUsername() + " - " + user.getNickname())
        );
    }

    /**
     * 测试用户分页查询
     */
    @Test
    void testUserSelectPage() {
        Page<User> page = new Page<>(1, 10);
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(User::getCreatedAt);

        Page<User> resultPage = userMapper.selectPage(page, wrapper);

        assertNotNull(resultPage);
        assertTrue(resultPage.getRecords().size() <= 10);
        System.out.println("✓ 分页查询成功");
        System.out.println("  总记录数: " + resultPage.getTotal());
        System.out.println("  当前页记录数: " + resultPage.getRecords().size());
        System.out.println("  总页数: " + resultPage.getPages());
    }

    /**
     * 测试用户更新
     */
    @Test
    void testUserUpdateById() {
        User user = createTestUser("update_test", "update_test@example.com");
        Long userId = user.getId();

        user.setNickname("更新后的昵称");
        user.setUpdatedAt(OffsetDateTime.now());

        int result = userMapper.updateById(user);
        assertEquals(1, result);

        User updatedUser = userMapper.selectById(userId);
        assertEquals("更新后的昵称", updatedUser.getNickname());
        System.out.println("✓ 用户更新成功，新昵称: " + updatedUser.getNickname());
    }

    /**
     * 测试用户批量更新
     */
    @Test
    void testUserUpdateByCondition() {
        LambdaUpdateWrapper<User> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(User::getRole, "USER")
                .set(User::getEnabled, false)
                .set(User::getUpdatedAt, OffsetDateTime.now());

        int result = userMapper.update(null, updateWrapper);
        System.out.println("✓ 批量更新成功，更新了 " + result + " 条用户记录");
        assertTrue(result >= 0);
    }

    /**
     * 测试用户删除
     */
    @Test
    void testUserDeleteById() {
        User user = createTestUser("delete_test", "delete_test@example.com");
        Long userId = user.getId();

        int result = userMapper.deleteById(userId);
        assertEquals(1, result);

        User deletedUser = userMapper.selectById(userId);
        assertNull(deletedUser);
        System.out.println("✓ 用户删除成功，用户ID: " + userId);
    }

    /**
     * 测试邮箱唯一性约束
     */
    @Test
    void testUserEmailUniqueness() {
        User user1 = createTestUser("unique_test1", "duplicate_email@example.com");

        User user2 = new User();
        user2.setUsername("unique_test2");
        user2.setEmail("duplicate_email@example.com");
        user2.setPasswordHash("password_hash");
        user2.setNickname("用户2");
        user2.setRole("USER");
        user2.setEnabled(true);
        user2.setCreatedAt(OffsetDateTime.now());
        user2.setUpdatedAt(OffsetDateTime.now());

        assertThrows(Exception.class, () -> {
            userMapper.insert(user2);
        });
        System.out.println("✓ 邮箱唯一性约束测试通过");
    }

    // ==================== 辅助方法 ====================

    /**
     * 创建测试用户
     */
    private User createTestUser(String username, String email) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash("$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi");
        user.setNickname("测试用户-" + username);
        user.setRole("USER");
        user.setEnabled(true);
        user.setCreatedAt(OffsetDateTime.now());
        user.setUpdatedAt(OffsetDateTime.now());

        userMapper.insert(user);
        return user;
    }
}
