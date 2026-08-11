package com.example;

import java.awt.Color;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("shinigamieyes")
public interface ShinigamiEyesConfig extends Config
{
    @ConfigItem(
            keyName = "textColor",
            name = "Text color",
            description = "Color of the overlay text"
    )
    default Color textColor()
    {
        return Color.RED;
    }
}