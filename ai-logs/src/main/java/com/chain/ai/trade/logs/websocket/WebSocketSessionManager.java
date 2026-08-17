package com.chain.ai.trade.logs.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import jakarta.websocket.Session;

public class WebSocketSessionManager {
    private static final Logger logger = LoggerFactory.getLogger(WebSocketSessionManager.class);
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    public void addSession(String sessionId, Session session) {
        sessions.put(sessionId, session);
        logger.info("Added WebSocket session: {}", sessionId);
    }

    public void removeSession(String sessionId) {
        sessions.remove(sessionId);
        logger.info("Removed WebSocket session: {}", sessionId);
    }

    public Session getSession(String sessionId) {
        return sessions.get(sessionId);
    }

    public void broadcast(String message) {
        sessions.entrySet().removeIf(entry -> {
            Session session = entry.getValue();
            if (!session.isOpen()) {
                logger.debug("Removing closed session: {}", entry.getKey());
                return true;
            }
            try {
                session.getBasicRemote().sendText(message);
                return false;
            } catch (Exception e) {
                logger.error("Error sending message to session: {}", entry.getKey(), e);
                return true;
            }
        });
        logger.debug("Broadcasted message to {} sessions", sessions.size());
    }

    public void sendToSession(String sessionId, String message) {
        Session session = sessions.get(sessionId);
        if (session != null && session.isOpen()) {
            try {
                session.getBasicRemote().sendText(message);
                logger.debug("Sent message to session: {}", sessionId);
            } catch (Exception e) {
                logger.error("Error sending message to session: {}", sessionId, e);
                removeSession(sessionId);
            }
        }
    }

    public int getSessionCount() {
        return sessions.size();
    }

    public java.util.Set<String> getSessionIds() {
        return sessions.keySet();
    }

    public void clear() {
        sessions.clear();
        logger.info("Cleared all WebSocket sessions");
    }
}
