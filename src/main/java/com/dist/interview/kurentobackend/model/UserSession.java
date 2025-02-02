package com.dist.interview.kurentobackend.model;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.kurento.client.IceCandidate;
import org.kurento.client.WebRtcEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import com.google.gson.JsonObject;

public class UserSession {
    private static final Logger log = LoggerFactory.getLogger(UserSession.class);

    private final String name;
    private final WebSocketSession session;
    private String sdpOffer;
    private String callingTo;
    private String callingFrom;
    private WebRtcEndpoint webRtcEndpoint;
    private final List<IceCandidate> candidateList = new ArrayList<>();

    public UserSession(WebSocketSession session, String name) {
        this.session = session;
        this.name = name;
    }

    public WebSocketSession getSession() {
        return session;
    }

    public String getName() {
        return name;
    }

    public String getSdpOffer() {
        return sdpOffer;
    }

    public void setSdpOffer(String sdpOffer) {
        this.sdpOffer = sdpOffer;
    }

    public String getCallingTo() {
        return callingTo;
    }

    public void setCallingTo(String callingTo) {
        this.callingTo = callingTo;
    }

    public String getCallingFrom() {
        return callingFrom;
    }

    public void setCallingFrom(String callingFrom) {
        this.callingFrom = callingFrom;
    }

    public void sendMessage(JsonObject message) throws IOException {
        log.debug("Sending message from user '{}': {}", name, message);
        session.sendMessage(new TextMessage(message.toString()));
    }

    public String getSessionId() {
        return session.getId();
    }

    public void setWebRtcEndpoint(WebRtcEndpoint webRtcEndpoint) {
        this.webRtcEndpoint = webRtcEndpoint;

        for (IceCandidate e : candidateList) {
            this.webRtcEndpoint.addIceCandidate(e);
        }
        this.candidateList.clear();
    }

    public void addCandidate(IceCandidate candidate) {
        if (this.webRtcEndpoint != null) {
            this.webRtcEndpoint.addIceCandidate(candidate);
        } else {
            candidateList.add(candidate);
        }
    }

    public void clear() {
        this.webRtcEndpoint = null;
        this.candidateList.clear();
    }
}
