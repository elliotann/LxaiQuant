package com.chain.ai.trade.logs.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.websocket.*;
import jakarta.websocket.server.ServerEndpoint;
import java.io.IOException;

@ServerEndpoint("/ws/logs")
public class BusinessLogWebSocketEndpoint {
    private static final Logger logger = LoggerFactory.getLogger(BusinessLogWebSocketEndpoint.class);
    private static final WebSocketSessionManager sessionManager = new WebSocketSessionManager();

    @OnOpen
    public void onOpen(Session session) {
        String sessionId = session.getId();
        sessionManager.addSession(sessionId, session);
        logger.info("WebSocket connection opened: {}", sessionId);
        try {
            session.getBasicRemote().sendText("{\"type\":\"connection\",\"status\":\"connected\"}");
        } catch (IOException e) {
            logger.error("Error sending connection message to session: {}", sessionId, e);
        }
    }

    @OnClose
    public void onClose(Session session) {
        String sessionId = session.getId();
        sessionManager.removeSession(sessionId);
        logger.info("WebSocket connection closed: {}", sessionId);
    }

    @OnMessage
    public void onMessage(String message, Session session) {
        String sessionId = session.getId();
        logger.debug("Received message from session {}: {}", sessionId, message);
        try {
            if ("ping".equals(message)) {
                session.getBasicRemote().sendText("pong");
            } else {
                session.getBasicRemote().sendText("{\"type\":\"ack\",\"message\":\"Message received\"}");
            }
        } catch (IOException e) {
            logger.error("Error sending response to session: {}", sessionId, e);
        }
    }

    @OnError
    public void onError(Session session, Throwable throwable) {
        String sessionId = session != null ? session.getId() : "unknown";
        logger.error("WebSocket error for session: {}", sessionId, throwable);
        if (session != null) {
            sessionManager.removeSession(sessionId);
        }
    }

    public static WebSocketSessionManager getSessionManager() {
        return sessionManager;
    }
}
