package com.xjy;



import com.xjy.model.Session;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Session管理器：负责Session的创建、校验、过期清理
 */
public class SessionManager {
    private static final long SESSION_TTL = 10 * 60 * 1000L; // 10分钟有效期（毫秒）
    private static final String AVAILABLE_CHARSET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int SESSION_KEY_LENGTH = 8; // 8位字母数字混合Key

    // 客户ID → Session（正向映射）
    private final Map<Integer, Session> customerSessionMap = new ConcurrentHashMap<>();
    // SessionKey → 客户ID（反向映射，用于快速校验）
    private final Map<String, Integer> sessionToCustomerMap = new ConcurrentHashMap<>();
    // 定时清理线程池（单线程足够，避免并发修改问题）
    private final ScheduledExecutorService cleaner = Executors.newSingleThreadScheduledExecutor();

    public SessionManager() {
        // 启动定时清理：延迟1分钟开始，每1分钟执行1次
        cleaner.scheduleAtFixedRate(
                this::cleanExpiredSessions,
                1, 1, TimeUnit.MINUTES
        );
    }

    /**
     * 获取或创建客户的Session
     * @param customerId 客户ID
     * @return 有效SessionKey
     */
    public String getOrCreateSession(int customerId) {
        long now = System.currentTimeMillis();
        long expireTime = now + SESSION_TTL;

        // 1. 检查现有Session是否有效
        Session existing = customerSessionMap.get(customerId);
        if (existing != null && existing.getExpireTime() >= now) {
            return existing.getSessionKey();
        }

        // 2. 生成新Session并原子更新
        String newKey = generateSessionKey();
        Session newSession = new Session(newKey, expireTime);

        // 替换旧Session（若存在）
        Session oldSession = customerSessionMap.put(customerId, newSession);
        if (oldSession != null) {
            sessionToCustomerMap.remove(oldSession.getSessionKey()); // 清理旧反向映射
        }

        // 3. 建立新反向映射
        sessionToCustomerMap.put(newKey, customerId);
        return newKey;
    }

    /**
     * 验证Session有效性
     * @param sessionKey 待验证的SessionKey
     * @return 有效则返回客户ID，无效则返回null
     */
    public Integer validateSession(String sessionKey) {
        if (sessionKey == null || sessionKey.isBlank()) {
            return null;
        }

        // 查询客户ID
        Integer customerId = sessionToCustomerMap.get(sessionKey);
        if (customerId == null) {
            return null;
        }

        // 二次验证Session是否匹配且未过期
        Session session = customerSessionMap.get(customerId);
        if (session == null
                || !session.getSessionKey().equals(sessionKey)
                || session.getExpireTime() < System.currentTimeMillis()) {
            sessionToCustomerMap.remove(sessionKey); // 清理无效映射
            return null;
        }

        return customerId;
    }

    /**
     * 清理过期Session
     */
    private void cleanExpiredSessions() {
        long now = System.currentTimeMillis();
        List<Map.Entry<Integer, Session>> expired = new ArrayList<>();

        // 收集过期Session
        for (Map.Entry<Integer, Session> entry : customerSessionMap.entrySet()) {
            if (entry.getValue().getExpireTime() < now) {
                expired.add(entry);
            }
        }

        // 批量清理（避免循环中修改集合）
        for (Map.Entry<Integer, Session> entry : expired) {
            customerSessionMap.remove(entry.getKey());
            sessionToCustomerMap.remove(entry.getValue().getSessionKey());
        }

        // 日志：便于调试
        if (!expired.isEmpty()) {
            System.out.printf("[%s] 清理过期Session数量：%d%n", new Date(now), expired.size());
        }
    }

    /**
     * 生成8位字母数字混合的SessionKey
     */
    private String generateSessionKey() {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(SESSION_KEY_LENGTH);
        for (int i = 0; i < SESSION_KEY_LENGTH; i++) {
            sb.append(AVAILABLE_CHARSET.charAt(random.nextInt(AVAILABLE_CHARSET.length())));
        }
        return sb.toString();
    }

    /**
     * 优雅关闭清理线程池
     */
    public void shutdown() {
        cleaner.shutdown();
        try {
            if (!cleaner.awaitTermination(1, TimeUnit.SECONDS)) {
                cleaner.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleaner.shutdownNow();
        }
    }
}
