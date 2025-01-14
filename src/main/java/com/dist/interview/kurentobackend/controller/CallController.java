package com.dist.interview.kurentobackend.controller;



import java.io.IOException;
import org.kurento.client.IceCandidate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.dist.interview.kurentobackend.model.UserSession;
import com.dist.interview.kurentobackend.service.CallService;
import com.dist.interview.kurentobackend.repository.UserRegistryRepository;

@Controller
public class CallController extends TextWebSocketHandler {
    private static final Logger log = LoggerFactory.getLogger(CallController.class);
    private static final Gson gson = new GsonBuilder().create();

    private final CallService callService;
    private final UserRegistryRepository registry;

    public CallController(CallService callService, UserRegistryRepository registry) {
        this.callService = callService;
        this.registry = registry;
    }

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JsonObject jsonMessage = gson.fromJson(message.getPayload(), JsonObject.class);
        UserSession user = registry.getBySession(session);

        if (user != null) {
            log.debug("Incoming message from user '{}': {}", user.getName(), jsonMessage);
        } else {
            log.debug("Incoming message from new user: {}", jsonMessage);
        }

        switch (jsonMessage.get("id").getAsString()) {
            case "register":
                try {
                    callService.register(session, jsonMessage.getAsJsonPrimitive("name").getAsString());
                } catch (Throwable t) {
                    handleError(t, session, "registerResponse");
                }
                break;
            case "call":
                try {
                    String to = jsonMessage.get("to").getAsString();
                    String from = jsonMessage.get("from").getAsString();
                    user.setSdpOffer(jsonMessage.getAsJsonPrimitive("sdpOffer").getAsString());
                    user.setCallingTo(to);
                    callService.call(user, to, from);
                } catch (Throwable t) {
                    handleError(t, session, "callResponse");
                }
                break;

            case "incomingCallResponse":
                try {
                    String callResponse = jsonMessage.get("callResponse").getAsString();
                    String from = jsonMessage.get("from").getAsString();
                    String sdpOffer = jsonMessage.has("sdpOffer") ?
                            jsonMessage.get("sdpOffer").getAsString() : null;
                    callService.handleIncomingCallResponse(user, callResponse, from, sdpOffer);
                } catch (Throwable t) {
                    handleError(t, session, "callResponse");
                }
                break;

            case "onIceCandidate": {
                JsonObject candidate = jsonMessage.get("candidate").getAsJsonObject();
                if (user != null) {
                    IceCandidate cand = new IceCandidate(
                            candidate.get("candidate").getAsString(),
                            candidate.get("sdpMid").getAsString(),
                            candidate.get("sdpMLineIndex").getAsInt());
                    user.addCandidate(cand);
                }
                break;
            }

            case "stop":
                callService.stop(session);
                break;

            default:
                break;
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        callService.stop(session);
        registry.removeBySession(session);
    }

    private void handleError(Throwable throwable, WebSocketSession session, String responseId)
            throws IOException {
        callService.stop(session);
        log.error(throwable.getMessage(), throwable);
        JsonObject response = new JsonObject();
        response.addProperty("id", responseId);
        response.addProperty("response", "rejected");
        response.addProperty("message", throwable.getMessage());
        session.sendMessage(new TextMessage(response.toString()));
    }
}