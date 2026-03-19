package com.procurehub.notification.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.notification.rate-limit")
public class NotificationRateLimitProperties {

    private boolean enabled = true;
    private int capacity = 20;
    private int leakPerSecond = 5;
    private int bucketKeyTtlSeconds = 3600;
    private int orderUserTtlSeconds = 604800;
    private String userBucketKeyPrefix = "notification:bucket:user:";
    private String orderBucketKeyPrefix = "notification:bucket:order:";
    private String orderUserKeyPrefix = "notification:order-user:";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public int getLeakPerSecond() {
        return leakPerSecond;
    }

    public void setLeakPerSecond(int leakPerSecond) {
        this.leakPerSecond = leakPerSecond;
    }

    public int getBucketKeyTtlSeconds() {
        return bucketKeyTtlSeconds;
    }

    public void setBucketKeyTtlSeconds(int bucketKeyTtlSeconds) {
        this.bucketKeyTtlSeconds = bucketKeyTtlSeconds;
    }

    public int getOrderUserTtlSeconds() {
        return orderUserTtlSeconds;
    }

    public void setOrderUserTtlSeconds(int orderUserTtlSeconds) {
        this.orderUserTtlSeconds = orderUserTtlSeconds;
    }

    public String getUserBucketKeyPrefix() {
        return userBucketKeyPrefix;
    }

    public void setUserBucketKeyPrefix(String userBucketKeyPrefix) {
        this.userBucketKeyPrefix = userBucketKeyPrefix;
    }

    public String getOrderBucketKeyPrefix() {
        return orderBucketKeyPrefix;
    }

    public void setOrderBucketKeyPrefix(String orderBucketKeyPrefix) {
        this.orderBucketKeyPrefix = orderBucketKeyPrefix;
    }

    public String getOrderUserKeyPrefix() {
        return orderUserKeyPrefix;
    }

    public void setOrderUserKeyPrefix(String orderUserKeyPrefix) {
        this.orderUserKeyPrefix = orderUserKeyPrefix;
    }
}

