package com.zhixing.training.auth;

import com.zhixing.training.api.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import java.util.HashMap;
import java.util.Map;

/** Single-instance safeguard; use a shared rate limiter before scaling horizontally. */
@Component
public class LoginRateLimiter {
    private record Window(long until, int count) { }
    private final Map<String, Window> windows = new HashMap<>();
    public synchronized void check(String address) {
        long now = System.currentTimeMillis();
        windows.entrySet().removeIf(e -> e.getValue().until() < now);
        Window old = windows.get(address);
        if ((old == null && windows.size() >= 10000) || (old != null && old.count() >= 30))
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,"RATE_LIMITED","请求过于频繁，请一分钟后重试");
        windows.put(address, new Window(old == null ? now + 60000 : old.until(), old == null ? 1 : old.count() + 1));
    }
}
