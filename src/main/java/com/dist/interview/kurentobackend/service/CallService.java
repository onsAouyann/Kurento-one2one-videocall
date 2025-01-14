package com.dist.interview.kurentobackend.service;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

import org.kurento.client.*;
import org.kurento.jsonrpc.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import com.google.gson.JsonObject;
import com.dist.interview.kurentobackend.model.UserSession;
import com.dist.interview.kurentobackend.repository.UserRegistryRepository;

@Service
public class CallService {
    private static final Logger log = LoggerFactory.getLogger(CallService.class);

    private final UserRegistryRepository registry;
    private final KurentoClient kurento;
    private final ConcurrentHashMap<String, MediaPipelineService> pipelines = new ConcurrentHashMap<>();

    public CallService(UserRegistryRepository registry, KurentoClient kurento) {
        this.registry = registry;
        this.kurento = kurento;
    }

    public void register(WebSocketSession session, String name) throws IOException {
        UserSession caller = new UserSession(session, name);
        String responseMsg = "accepted";

        if (name.isEmpty()) {
            responseMsg = "rejected: empty user name";
        } else if (registry.exists(name)) {
            responseMsg = "rejected: user '" + name + "' already registered";
        } else {
            registry.register(caller);
        }

        JsonObject response = new JsonObject();
        response.addProperty("id", "registerResponse");
        response.addProperty("response", responseMsg);
        caller.sendMessage(response);
    }

    public void call(UserSession caller, String to, String from) throws IOException {
        if (registry.exists(to)) {
            JsonObject response = new JsonObject();
            response.addProperty("id", "incomingCall");
            response.addProperty("from", from);

            UserSession callee = registry.getByName(to);
            callee.sendMessage(response);
            callee.setCallingFrom(from);
        } else {
            JsonObject response = new JsonObject();
            response.addProperty("id", "callResponse");
            response.addProperty("response", "rejected: user '" + to + "' is not registered");
            caller.sendMessage(response);
        }
    }

    public void handleIncomingCallResponse(UserSession callee, String callResponse,
                                           String from, String sdpOffer) throws IOException {
        UserSession calleer = registry.getByName(from);

        if ("accept".equals(callResponse)) {
            MediaPipelineService pipeline = new MediaPipelineService(kurento);
            pipelines.put(calleer.getSessionId(), pipeline);
            pipelines.put(callee.getSessionId(), pipeline);

            callee.setWebRtcEndpoint(pipeline.getCalleeWebRtcEp());
            configureWebRtcEndpoint(callee, pipeline.getCalleeWebRtcEp());

            calleer.setWebRtcEndpoint(pipeline.getCallerWebRtcEp());
            configureWebRtcEndpoint(calleer, pipeline.getCallerWebRtcEp());

            String calleeSdpAnswer = pipeline.generateSdpAnswerForCallee(sdpOffer);
            JsonObject startCommunication = new JsonObject();
            startCommunication.addProperty("id", "startCommunication");
            startCommunication.addProperty("sdpAnswer", calleeSdpAnswer);
            callee.sendMessage(startCommunication);

            pipeline.getCalleeWebRtcEp().gatherCandidates();

            String callerSdpOffer = calleer.getSdpOffer();
            String callerSdpAnswer = pipeline.generateSdpAnswerForCaller(callerSdpOffer);
            JsonObject response = new JsonObject();
            response.addProperty("id", "callResponse");
            response.addProperty("response", "accepted");
            response.addProperty("sdpAnswer", callerSdpAnswer);
            calleer.sendMessage(response);

            pipeline.getCallerWebRtcEp().gatherCandidates();
        } else {
            JsonObject response = new JsonObject();
            response.addProperty("id", "callResponse");
            response.addProperty("response", "rejected");
            calleer.sendMessage(response);
        }
    }

    private void configureWebRtcEndpoint(final UserSession user, WebRtcEndpoint endpoint) {
        endpoint.addIceCandidateFoundListener(
                new EventListener<IceCandidateFoundEvent>() {
                    @Override
                    public void onEvent(IceCandidateFoundEvent event) {
                        JsonObject response = new JsonObject();
                        response.addProperty("id", "iceCandidate");
                        response.add("candidate", JsonUtils.toJsonObject(event.getCandidate()));
                        try {
                            synchronized (user.getSession()) {
                                user.getSession().sendMessage(
                                        new TextMessage(response.toString()));
                            }
                        } catch (IOException e) {
                            log.debug(e.getMessage());
                        }
                    }
                });
    }

    public void stop(WebSocketSession session) throws IOException {
        String sessionId = session.getId();
        if (pipelines.containsKey(sessionId)) {
            MediaPipelineService pipeline = pipelines.remove(sessionId);
            pipeline.release();

            UserSession stopperUser = registry.getBySession(session);
            if (stopperUser != null) {
                UserSession stoppedUser = registry.getByName(
                        stopperUser.getCallingFrom() != null ?
                                stopperUser.getCallingFrom() :
                                stopperUser.getCallingTo());

                if (stoppedUser != null) {
                    JsonObject message = new JsonObject();
                    message.addProperty("id", "stopCommunication");
                    stoppedUser.sendMessage(message);
                    stoppedUser.clear();
                }
                stopperUser.clear();
            }
        }
    }
}