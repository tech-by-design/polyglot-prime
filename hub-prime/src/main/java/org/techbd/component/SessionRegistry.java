package org.techbd.component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.servlet.http.HttpSession;

import org.springframework.stereotype.Component;

@Component
public class SessionRegistry {

    private final Map<String, Set<HttpSession>> userSessions = new ConcurrentHashMap<>();

    public void addSession(String userId, HttpSession session) {
        userSessions
                .computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet())
                .add(session);
    }

    public Set<HttpSession> getSessions(String userId) {
        return userSessions.getOrDefault(userId, Set.of());
    }

    public void removeSession(String userId, HttpSession session) {
        Set<HttpSession> sessions = userSessions.get(userId);
        if (sessions != null) {
            sessions.remove(session);
            if (sessions.isEmpty()) {
                userSessions.remove(userId);
            }
        }
    }
}
