package com.example;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

public class ShinigamiEyesOverlay extends Overlay
{
    private static final Font SHINIGAMI_FONT = new Font("Serif", Font.ITALIC | Font.BOLD, 16);
    private static final int TEXT_HEIGHT_OFFSET = 20;

    private final Client client;
    private final ShinigamiEyesPlugin plugin;
    private final ShinigamiEyesConfig config;

    @Inject
    private ShinigamiEyesOverlay(Client client, ShinigamiEyesPlugin plugin, ShinigamiEyesConfig config)
    {
        this.client = client;
        this.plugin = plugin;
        this.config = config;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (!plugin.isInTargetArea())
        {
            return null;
        }

        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setFont(SHINIGAMI_FONT);

        Player localPlayer = client.getLocalPlayer();
        long time = System.currentTimeMillis();

        for (Player p : client.getPlayers())
        {
            if (p == null || p == localPlayer)
            {
                continue;
            }

            String text = plugin.getDisplayText(p.getName());
            if (text == null)
            {
                continue;
            }

            double phase = (p.getName().hashCode() % 1000) / 1000.0 * Math.PI * 2;
            float floatOffset = (float) (Math.sin(time / 400.0 + phase) * 4);

            net.runelite.api.Point base = p.getCanvasTextLocation(graphics, text, p.getLogicalHeight() + TEXT_HEIGHT_OFFSET);
            if (base == null)
            {
                continue;
            }

            int x = base.getX();
            int y = base.getY() + Math.round(floatOffset);

            drawSmokeyText(graphics, x, y, text, config.textColor());
        }

        return null;
    }

    private void drawSmokeyText(Graphics2D graphics, int x, int y, String text, Color baseColor)
    {
        int[][] offsets = {
                {-2, -2}, {2, -2}, {-2, 2}, {2, 2},
                {-1, 0}, {1, 0}, {0, -1}, {0, 1},
                {-3, 0}, {3, 0}, {0, -3}, {0, 3}
        };

        Color glow = new Color(baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue(), 35);
        graphics.setColor(glow);
        for (int[] off : offsets)
        {
            graphics.drawString(text, x + off[0], y + off[1]);
        }

        Color outline = new Color(0, 0, 0, 120);
        graphics.setColor(outline);
        graphics.drawString(text, x + 1, y + 1);

        Color core = new Color(baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue(), 230);
        graphics.setColor(core);
        graphics.drawString(text, x, y);
    }
}