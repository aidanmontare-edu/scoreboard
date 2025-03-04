package com.carolinarollergirls.scoreboard.json;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

public class JSONStateManagerTests {
    private JSONStateManager jsm;
    private TestListener listener;

    @Before
    public void setup() {
        jsm = new JSONStateManager();
        listener = new TestListener();
    }

    public class TestListener implements JSONStateListener {
        public StateTrie state;
        public StateTrie changed;
        public int num_updates;

        @SuppressWarnings("hiding")
        @Override
        public void sendUpdates(StateTrie state, StateTrie changed) {
            this.state = state;
            this.changed = changed;
            num_updates++;
        }
    }

    @Test
    public void listener_gets_update_on_register() {
        jsm.updateState("foo", "bar");
        jsm.register(listener);
        HashMap<String, Object> hm = new HashMap<>();
        hm.put("foo", "bar");

        jsm.waitForSent();
        assertEquals(1, listener.num_updates);
        assertEquals(hm, listener.state.getAll(false));
    }

    @Test
    public void no_update_on_noop_change() {
        jsm.register(listener);
        jsm.waitForSent();
        assertEquals(1, listener.num_updates);
        jsm.updateState("foo", "bar");
        jsm.waitForSent();
        assertEquals(2, listener.num_updates);
        jsm.updateState("foo", "bar");
        jsm.waitForSent();
        assertEquals(2, listener.num_updates);
    }

    @Test
    public void delete_subtree() {
        jsm.updateState("foo.12.34", "bar");
        jsm.updateState("foo.12.34.56", "bar");
        jsm.updateState("foo.78.90", "bar");
        jsm.register(listener);
        jsm.waitForSent();
        assertEquals(3, listener.state.size());

        jsm.updateState("foo.12", null);
        jsm.waitForSent();
        assertEquals(1, listener.state.size());
        assertEquals(2, listener.changed.size());
    }

    @Test
    public void replace_subtree() {
        jsm.updateState("foo.12.34", "bar");
        jsm.updateState("foo.12.34.56", "bar");
        jsm.updateState("foo.78.90", "bar");
        jsm.register(listener);
        jsm.waitForSent();
        assertEquals(3, listener.state.size());

        List<WSUpdate> updates = new ArrayList<>();
        updates.add(new WSUpdate("foo.12", null));
        updates.add(new WSUpdate("foo.12.78", "bar"));
        jsm.updateState(updates);
        jsm.waitForSent();
        assertEquals(2, listener.state.size());
        assertEquals(3, listener.changed.size());
    }

    @Test
    public void do_not_delete_prefix() {
        jsm.updateState("foo.12.34", "bar");
        jsm.updateState("foo.12.34.56", "bar");
        jsm.updateState("foo.78.90", "bar");
        jsm.register(listener);
        jsm.waitForSent();
        assertEquals(3, listener.state.size());
        assertEquals(1, listener.num_updates);

        jsm.updateState("foo.1", null);
        jsm.waitForSent();
        assertEquals(3, listener.state.size());
        assertEquals(1, listener.num_updates);
    }

    @Test
    public void no_update_when_nothing_deleted() {
        jsm.register(listener);
        jsm.waitForSent();
        assertEquals(1, listener.num_updates);
        jsm.updateState("foo", null);
        jsm.waitForSent();
        assertEquals(1, listener.num_updates);
    }

    @Test
    public void delete_and_recreate_in_one_update() {
        jsm.register(listener);
        jsm.updateState("foo.1", "bar");
        List<WSUpdate> updates = new ArrayList<>();
        updates.add(new WSUpdate("foo", null));
        updates.add(new WSUpdate("foo.1", "baz"));
        jsm.updateState(updates);

        HashMap<String, Object> hm = new HashMap<>();
        hm.put("foo.1", "baz");
        jsm.waitForSent();
        assertEquals(hm, listener.state.getAll(false));
        assertEquals(1, listener.changed.size());
    }
}
