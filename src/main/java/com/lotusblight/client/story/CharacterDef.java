package com.lotusblight.client.story;

import net.minecraft.resources.ResourceLocation;

/** One story character as the data file describes it. {@code floor} is the floor of the 5-floor house (null until the author says). */
public record CharacterDef(String id, String name, ResourceLocation skin, int color, Integer floor, boolean placeholderSkin) {}
