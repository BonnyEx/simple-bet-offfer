package com.xjy;



import com.xjy.model.Session;
import com.xjy.util.Base62Util;

import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/**
 * Session管理器：负责Session的创建、校验、过期清理
 */
public class SessionManager {
    private static final long SESSION_TTL = 10 * 60 * 1000L; // 10分钟有效期（毫秒）
    private static final String AVAILABLE_CHARSET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int SESSION_KEY_LENGTH = 8; // 8位字母数字混合Key

    // 客户ID → Session（正向映射）
    private final Map<Integer, Session> customerSessionMap = new ConcurrentHashMap<>();

    // 定时清理线程池（单线程足够，避免并发修改问题）
    private final ScheduledExecutorService cleaner = Executors.newSingleThreadScheduledExecutor();

    // 锁池：按客户ID余数分散到不同读写锁，减少竞争
    private final ReentrantReadWriteLock[] customerLocks;
    // 锁池大小（建议为2的幂）
    private static final int LOCK_POOL_SIZE = 32;



    public SessionManager() {
        // 初始化锁池
        customerLocks = new ReentrantReadWriteLock[LOCK_POOL_SIZE];
        for (int i = 0; i < LOCK_POOL_SIZE; i++) {
            customerLocks[i] = new ReentrantReadWriteLock();
        }

        // 启动定时清理：延迟1分钟开始，每1分钟执行1次
        cleaner.scheduleAtFixedRate(
                this::cleanExpiredSessions,
                1, 1, TimeUnit.MINUTES
        );
    }

    /**
     * 获取客户id对应的锁（通过余数计算）
     */
    private ReentrantReadWriteLock getLockForCustomer(int customerId) {
        // 哈希值取绝对值，避免负索引
        int index = Math.abs(customerId % LOCK_POOL_SIZE);
        return customerLocks[index];
    }

    /**
     * 获取或创建客户的Session
     * @param customerId 客户ID
     * @return 有效SessionKey
     */
    public String getOrCreateSession(int customerId) {
        long now = System.currentTimeMillis();
        long expireTime = now + SESSION_TTL;

        ReentrantReadWriteLock lock = getLockForCustomer(customerId);
        // 检查是否有已存在的会话，申请读锁
        lock.readLock().lock();
        try {
            // 1. 检查现有Session是否有效
            Session existing = customerSessionMap.get(customerId);
            if (existing != null && existing.getExpireTime() >= now) {
                return existing.getSessionKey();
            }
        } finally {
            lock.readLock().unlock();
        }

        // 无有效会话，需要重新生成新会话，申请写锁
        lock.writeLock().lock();
        String newKey = generateSessionKey(customerId);
        try {
            // 重新检查获得写锁前，是否有新的有效会话
            Session existing = customerSessionMap.get(customerId);
            if (existing != null && existing.getExpireTime() >= now) {
                return existing.getSessionKey();
            }

            // 2. 生成新Session并原子更新
            Session newSession = new Session(newKey, expireTime);

            // 替换旧Session（若存在）
            Session oldSession = customerSessionMap.put(customerId, newSession);


        } finally {
            lock.writeLock().unlock();
        }

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
        Integer customerId = getCustomerIdFromSessionKey(sessionKey);
        if (customerId == null) {
            return null;
        }

        // 检查前获取读锁
        ReentrantReadWriteLock lock = getLockForCustomer(customerId);
        lock.readLock().lock();
        try {
            // 验证Session是否匹配且未过期
            Session session = customerSessionMap.get(customerId);
            if (session == null
                    || !session.getSessionKey().equals(sessionKey)
                    || session.getExpireTime() < System.currentTimeMillis()) {
                return null;
            }
        } finally {
            lock.readLock().unlock();
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
        Map<ReentrantReadWriteLock, List<Integer>> map = expired.stream()
                .collect(Collectors.groupingBy(entry -> getLockForCustomer(entry.getKey()),
                        Collectors.mapping(Map.Entry::getKey,
                                Collectors.toList())));

        // 按分段锁批量清理，降低锁占用
        for (Map.Entry<ReentrantReadWriteLock, List<Integer>> entry : map.entrySet()) {
            entry.getKey().writeLock().lock();
            try {
                for (Integer customerId : entry.getValue()) {
                    // 进入写锁后二次检查
                    if (customerSessionMap.containsKey(customerId) &&  customerSessionMap.get(customerId).getExpireTime() < now) {
                        customerSessionMap.remove(customerId);
                    }
                }
            } finally {
                entry.getKey().writeLock().unlock();
            }
        }

        // 日志：便于调试
        if (!expired.isEmpty()) {
            System.out.printf("[%s] 清理过期Session数量：%d%n", new Date(now), expired.size());
        }
    }

    /**
     * 生成8位字母数字混合的SessionKey
     */
    private String generateSessionKey(int customerId) {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(SESSION_KEY_LENGTH);
        for (int i = 0; i < SESSION_KEY_LENGTH; i++) {
            sb.append(AVAILABLE_CHARSET.charAt(random.nextInt(AVAILABLE_CHARSET.length())));
        }
        sb.append(Base62Util.encode(customerId));
        return sb.toString();
    }

    private Integer getCustomerIdFromSessionKey(String sessionKey) {
        if (sessionKey == null || sessionKey.isBlank() || sessionKey.length() <= SESSION_KEY_LENGTH) {
            return null;
        }
        String encodedSessionKey = sessionKey.substring(SESSION_KEY_LENGTH);
        try {
            return Base62Util.decode(encodedSessionKey);
        } catch (Exception e) {
            return null;
        }
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
