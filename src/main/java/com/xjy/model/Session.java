package com.xjy.model;

/**
 * Session模型，存储SessionKey和过期时间
 */
public class Session {
    private final String sessionKey;
    private final long expireTime; // 毫秒级时间戳

    public Session(String sessionKey, long expireTime) {
        this.sessionKey = sessionKey;
        this.expireTime = expireTime;
    }

    public String getSessionKey() {
        return sessionKey;
    }

    public long getExpireTime() {
        return expireTime;
    }
}
