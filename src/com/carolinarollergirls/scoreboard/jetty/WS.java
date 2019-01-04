package com.carolinarollergirls.scoreboard.jetty;
/**
 * Copyright (C) 2008-2012 Mr Temper <MrTemper@CarolinaRollergirls.com>
 *
 * This file is part of the Carolina Rollergirls (CRG) ScoreBoard.
 * The CRG ScoreBoard is licensed under either the GNU General Public
 * License version 3 (or later), or the Apache License 2.0, at your option.
 * See the file COPYING for details.
 */

import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.prometheus.client.Histogram;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import java.util.UUID;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.websocket.WebSocket;
import org.eclipse.jetty.websocket.WebSocket.OnTextMessage;
import org.eclipse.jetty.websocket.WebSocketServlet;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.carolinarollergirls.scoreboard.ScoreBoardManager;
import com.carolinarollergirls.scoreboard.core.ScoreBoard;
import com.carolinarollergirls.scoreboard.event.ScoreBoardEvent.ValueWithId;
import com.carolinarollergirls.scoreboard.json.JSONStateManager;
import com.carolinarollergirls.scoreboard.utils.ValWithId;
import com.carolinarollergirls.scoreboard.json.JSONStateListener;

public class WS extends WebSocketServlet {

    public WS(ScoreBoard s, JSONStateManager j) {
        sb = s;
        jsm = j;
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        getServletContext().getNamedDispatcher("default").forward(request, response);
    }

    @Override
    public WebSocket doWebSocketConnect(HttpServletRequest request, String arg1) {
        return new Conn(jsm);
    }


    private ScoreBoard sb;
    private JSONStateManager jsm;


    private boolean hasPermission(String action) {
        return true;
    }

    private static final Gauge connectionsActive = Gauge.build()
            .name("crg_websocket_active_connections").help("Current WebSocket connections").register();
    private static final Counter messagesReceived = Counter.build()
            .name("crg_websocket_messages_received").help("Number of WebSocket messages received").register();
    private static final Histogram messagesSentDuration = Histogram.build()
            .name("crg_websocket_messages_sent_duration_seconds").help("Time spent sending WebSocket messages").register();
    private static final Counter messagesSentFailures = Counter.build()
            .name("crg_websocket_messages_sent_failed").help("Number of WebSocket messages we failed to send").register();

    public class Conn implements OnTextMessage, JSONStateListener {
        private Connection connection;
        private JSONStateManager jsm;

        public Conn(JSONStateManager jsm) {
            this.jsm = jsm;
        }

        public synchronized void onMessage(String message_data) {
            messagesReceived.inc();
            try {
                JSONObject json = new JSONObject(message_data);
                String action = json.getString("action");
                if (!hasPermission(action)) {
                    json = new JSONObject();
                    json.put("authorization", "Not authorized for " + action);
                    send(json);
                    return;
                }
                if (action.equals("Register")) {
                    JSONArray paths = json.optJSONArray("paths");
                    if (paths != null) {
                        Set<String> newPaths = new HashSet<String>();
                        for (int i = 0; i < paths.length(); i++) {
                            newPaths.add(paths.getString(i));
                        }
                        // Send on updates for the newly registered paths.
                        sendWSUpdatesForPaths(newPaths, state.keySet());
                        this.paths.addAll(newPaths);
                    }
                } else if (action.equals("Penalty")) {
                    JSONObject data = json.getJSONObject("data");
                    String teamId = data.optString("teamId");
                    String skaterId = data.optString("skaterId");
                    String penaltyId = data.optString("penaltyId", null);
                    boolean fo_exp = data.optBoolean("fo_exp", false);
                    String num = fo_exp ? "FO_EXP" : "0";
                    int period = data.optInt("period", -1);
                    int jam = data.optInt("jam", -1);
                    String code = data.optString("code", null);
                    if (period == -1 || jam == -1) {
                        return;
                    }
                    sb.getTeam(teamId).getSkater(skaterId).penalty(penaltyId, num, period, jam, code);
                } else if (action.equals("Set")) {
                    String key = json.getString("key");
                    Object value = json.get("value");
                    String v;
                    if (value == JSONObject.NULL) {
                        // Null deletes the setting.
                        v = null;
                    } else {
                        v = value.toString();
                    }
                    ScoreBoardManager.printMessage("Setting " + key + " to " + v);

                    if (key.startsWith("ScoreBoard.Settings.")) {
                        sb.getSettings().set(key.substring(20), v);
                    }
                } else if (action.equals("AddRuleset")) {
                    JSONObject data = json.getJSONObject("data");
                    String n = data.getString("name");
                    String p = data.getString("parent");
                    sb.getRulesets().addRuleset(n, p);
                } else if (action.equals("RemoveRuleset")) {
                    JSONObject data = json.getJSONObject("data");
                    String i = data.getString("id");
                    sb.getRulesets().removeRuleset(i);
                } else if (action.equals("UpdateRuleset")) {
                    JSONObject data = json.getJSONObject("data");
                    String i = data.getString("id");
                    String n = data.getString("name");
                    JSONObject rules = data.getJSONObject("rules");
                    Collection<ValueWithId> s = new HashSet<ValueWithId>();
                    for (String k : rules.keySet()) {
                	s.add(new ValWithId(k, rules.getString(k)));
                    }
                    sb.getRulesets().getRuleset(i).setAll(s);
                    sb.getRulesets().getRuleset(i).setName(n);
                } else if (action.equals("Ping")) {
                    send(new JSONObject().put("Pong", ""));
                } else {
                    sendError("Unknown Action '" + action + "'");
                }
            } catch (JSONException je) {
                ScoreBoardManager.printMessage("Error parsing JSON message: " + je);
                je.printStackTrace();
            }
        }

        public void send(JSONObject json) {
            Histogram.Timer timer = messagesSentDuration.startTimer();
            try {
                connection.sendMessage(json.toString());
            } catch (Exception e) {
                ScoreBoardManager.printMessage("Error sending JSON update: " + e);
                e.printStackTrace();
                messagesSentFailures.inc();
            } finally {
                timer.observeDuration();
            }
        }

        @Override
        public void onOpen(Connection connection) {
            connectionsActive.inc();
            this.connection = connection;
            id = UUID.randomUUID();
            jsm.register(this);

            try {
                JSONObject json = new JSONObject();
                json.put("id", id);
                send(json);
            } catch (JSONException je) {
                ScoreBoardManager.printMessage("Error sending ID to client: " + je);
                je.printStackTrace();
            }
        }

        @Override
        public void onClose(int closeCode, String message) {
            connectionsActive.dec();
            jsm.unregister(this);
        }

        public void sendError(String message) {
            try {
                JSONObject json = new JSONObject();
                json.put("error", message);
                send(json);
            } catch (JSONException je) {
                ScoreBoardManager.printMessage("Error sending error to client: " + je);
                je.printStackTrace();
            }
        }

        // State changes from JSONStateManager.
        public synchronized void sendUpdates(Map<String, Object> state, Set<String> changed) {
            this.state = state;
            sendWSUpdatesForPaths(paths, changed);
        }

        private void sendWSUpdatesForPaths(Set<String>paths, Set<String> changed) {
            Map<String, Object> updates = new HashMap<String, Object>();
            for (String k: changed) {
                for (String p : paths) {
                    if (k.startsWith(p)) {
                        if (state.get(k) == null) {
                            updates.put(k, JSONObject.NULL);
                        } else {
                            updates.put(k, state.get(k));
                        }
                    }
                }
            }
            if (updates.size() == 0) {
                return;
            }
            try {
                JSONObject json = new JSONObject();
                json.put("state", new JSONObject(updates));
                send(json);
                updates.clear();
            } catch (JSONException e) {
                ScoreBoardManager.printMessage("Error sending updates to client: " + e);
                e.printStackTrace();
            }
        }

        protected UUID id;
        protected Set<String> paths = new HashSet<String>();
        private Map<String, Object> state;
    }
}
