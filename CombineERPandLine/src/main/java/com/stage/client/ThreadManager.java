package com.stage.client;

import configuration.AssetThing;
import configuration.ConfigurationAgent;
import gui.GUIAgent;
import gui.VirtualProductLine;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.LoggerFactory;

public class ThreadManager {

    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(ThreadManager.class);

    private final List<Thread> threads;
    private final ConfigurationAgent agent;
    private final ThingworxClient client;
    private final Thread GUIThread;
    private boolean pauseThread;

    public ThreadManager(ConfigurationAgent agent) {
        this.agent = agent;
        this.client = agent.getClient();
        threads = new ArrayList<>();
        this.GUIThread = new Thread(new GUIThreadRunnable());
        pauseThread = false;
    }

    //Start threads
    public void start() {
        try {
            //Start GUI
            GUIThread.start();

            //Start thingThreads (simulate data)
            if (client.waitForConnection(30000)) {
                for (AssetThing thing : agent.getThings()) {
                    if (!thing.getName().contains("Line")) {
                        Thread thingThread = new Thread(new AgentThreadRunnable(thing, 5));
                        thingThread.start();
                    }
                }
                LOG.info("NOTIFICATIE [INFO] - {} - Threads succesfully started. Refresh interval: {} seconds.", ThreadManager.class, 5);
            }
        } catch (Exception e) {
            LOG.error("NOTIFICATIE [ERROR] - {} - An exception occurred while starting the threads.", ThreadManager.class);
            e.printStackTrace();
        }
    }

    void togglePause() {
        this.pauseThread = !this.pauseThread;
    }

    private class AgentThreadRunnable implements Runnable {

        private final AssetThing thing;
        private final int speed;

        private AgentThreadRunnable(AssetThing thing, int speed) {
            this.speed = speed;
            this.thing = thing;
        }

        @Override
        public void run() {
            while (!client.isShutdown()) {
                if (pauseThread) {
                    LOG.info("NOTIFICATIE [INFO] - pausing...");
                    try {
                        TimeUnit.SECONDS.sleep(speed);
                    } catch (InterruptedException ex) {
                        LOG.info("NOTIFICATIE [ERROR] - InterruptedException while sleeping...");
                    }
                } else {
                    try {
                        client.bindThing(this.thing);
                        if (client.isConnected()) {
                            try {
                                this.thing.simulateData();
                                LOG.info("NOTIFICATIE [INFO] - {} has been updated.", thing.getName());
                                TimeUnit.SECONDS.sleep(speed);
                            } catch (Exception e) {
                                LOG.error("NOTIFICATIE [ERROR] - {} - Exception occurred while simulating new data.", AgentThreadRunnable.class);
                            }
                        }
                    } catch (Exception e) {
                        LOG.error("NOTIFICATIE [ERROR] - {} - Thing {} could not be bound to the client.", AgentThreadRunnable.class, this.thing.getName());
                    }
                }
            }
        }

    }

    private class GUIThreadRunnable implements Runnable {

        @Override
        public void run() {
            java.awt.EventQueue.invokeLater(() -> {
                new VirtualProductLine(new GUIAgent(agent)).setVisible(true);
            });
        }
    }
}
