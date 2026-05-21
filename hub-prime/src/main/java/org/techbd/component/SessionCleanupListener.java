package org.techbd.component;

import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class SessionCleanupListener implements HttpSessionListener {

    @Autowired
    private SessionRegistry sessionRegistry;

    @Override
    public void sessionDestroyed(HttpSessionEvent se) {
        HttpSession session = se.getSession();

        String userId = (String) session.getAttribute("USER_ID");

        if (userId != null) {
            sessionRegistry.removeSession(userId, session);
        }
    }
}