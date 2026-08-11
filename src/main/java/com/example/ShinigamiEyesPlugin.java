package com.example;

import com.google.inject.Provides;
import java.io.IOException;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.events.GameTick;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.hiscore.HiscoreClient;
import net.runelite.client.hiscore.HiscoreEndpoint;
import net.runelite.client.hiscore.HiscoreResult;
import net.runelite.client.hiscore.HiscoreSkill;
import net.runelite.client.hiscore.Skill;

@PluginDescriptor(
        name = "Shinigami Eyes",
        description = "Shows Corrupted Gauntlet KC above nearby players in the Gauntlet lobby",
        tags = {"gauntlet", "overlay", "kc"}
)
public class ShinigamiEyesPlugin extends Plugin
{
    private static final int TARGET_REGION_ID = 12127;
    private static final int NOT_FOUND = -1;

    @Inject
    private Client client;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private ShinigamiEyesOverlay overlay;

    @Inject
    private ShinigamiEyesConfig config;

    @Inject
    private HiscoreClient hiscoreClient;

    @Inject
    private ScheduledExecutorService executor;

    private ScheduledFuture<?> drainTask;

    // Names currently queued or already resolved this session — ensures
    // each player is only ever looked up once per client session.
    private final Queue<String> lookupQueue = new ConcurrentLinkedQueue<>();
    private final Set<String> queuedOrDone = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<String, Integer> kcCache = new ConcurrentHashMap<>();

    @Provides
    ShinigamiEyesConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(ShinigamiEyesConfig.class);
    }

    @Override
    protected void startUp()
    {
        overlayManager.add(overlay);
        // Drains at most one name from the queue every 300ms, so lookups
        // trickle out one-at-a-time instead of bursting.
        drainTask = executor.scheduleWithFixedDelay(this::drainOne, 0, 300, TimeUnit.MILLISECONDS);
    }

    @Override
    protected void shutDown()
    {
        overlayManager.remove(overlay);
        if (drainTask != null)
        {
            drainTask.cancel(true);
        }
        lookupQueue.clear();
        queuedOrDone.clear();
        kcCache.clear();
    }

    @Subscribe
    public void onGameTick(GameTick tick)
    {
        if (!isInTargetArea())
        {
            return;
        }

        for (Player p : client.getPlayers())
        {
            if (p == null || p == client.getLocalPlayer())
            {
                continue;
            }

            String name = p.getName();
            // queuedOrDone.add() returns false if the name is already present,
            // so each player name is only ever added to the queue once.
            if (name != null && queuedOrDone.add(name))
            {
                lookupQueue.add(name);
            }
        }
    }

    private void drainOne()
    {
        String name = lookupQueue.poll();
        if (name == null)
        {
            return;
        }

        try
        {
            HiscoreResult result = hiscoreClient.lookup(name, HiscoreEndpoint.NORMAL);
            Skill skill = result.getSkill(HiscoreSkill.THE_CORRUPTED_GAUNTLET);
            int kc = skill != null ? skill.getLevel() : NOT_FOUND;
            kcCache.put(name, kc);
        }
        catch (IOException e)
        {
            // Transient failure (timeout, brief rate limit). Remove from
            // queuedOrDone so this specific player can be retried later —
            // this is the only case where a "poll" doesn't count as final.
            queuedOrDone.remove(name);
        }
    }

    public boolean isInTargetArea()
    {
        if (client.getGameState() != GameState.LOGGED_IN || client.getLocalPlayer() == null)
        {
            return false;
        }

        return client.getLocalPlayer().getWorldLocation().getRegionID() == TARGET_REGION_ID;
    }

    /**
     * Returns display text for a player, or null if no data yet / not found.
     */
    public String getDisplayText(String playerName)
    {
        if (playerName == null)
        {
            return null;
        }

        Integer kc = kcCache.get(playerName);
        if (kc == null)
        {
            return null; // still queued/not looked up yet
        }
        if (kc == NOT_FOUND)
        {
            return "0"; // not on hiscores — treat as 0
        }
        return String.valueOf(kc);
    }
}