/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2015, AlgorithmX2, All rights reserved.
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Applied Energistics 2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Applied Energistics 2.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.core.worlddata;


import appeng.core.AELog;
import appeng.core.AppEng;
import com.google.common.base.Preconditions;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.mojang.authlib.GameProfile;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;


/**
 * Handles the matching between UUIDs and internal IDs for security systems.
 * This whole system could be replaced by storing directly the UUID,
 * using a lot more traffic though
 *
 * @author thatsIch
 * @version rv3 - 30.05.2015
 * @since rv3 30.05.2015
 */
final class PlayerData extends WorldSavedData implements IWorldPlayerData {

    public static final String NAME = AppEng.MOD_ID + "_players";
    public static final String TAG_PLAYER_IDS = "playerIds";
    private static final String PROFILE_PREFIX = "profile_";

    private final BiMap<UUID, Integer> mapping = HashBiMap.create();

    // Caches the highest assigned player id + 1
    private int nextPlayerId = 0;

    public PlayerData() {
        super(NAME);
    }

    @Nullable
    @Override
    public UUID getProfileId(final int playerId) {
        return this.mapping.inverse().get(playerId);
    }

    @Override
    public int getMePlayerId(@Nonnull final GameProfile profile) {
        Preconditions.checkNotNull(profile);

        final UUID uuid = profile.getId();
        Integer playerId = mapping.get(uuid);

        if (playerId == null) {
            playerId = this.nextPlayerId++;
            this.mapping.put(profile.getId(), playerId);
            markDirty();

            AELog.info("Assigning ME player id {} to Minecraft profile {} ({})", playerId, profile.getId(), profile.getName());
        }

        return playerId;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        int[] playerIds = nbt.getIntArray(TAG_PLAYER_IDS);
        this.mapping.clear();

        for (int i = 0; i < playerIds.length; i++) {
            long most = nbt.getLong(PROFILE_PREFIX + i + "_most");
            long least = nbt.getLong(PROFILE_PREFIX + i + "_least");
            UUID profileId = new UUID(most, least);

            mapping.put(profileId, playerIds[i]);
            this.nextPlayerId = Math.max(playerIds[i] + 1, this.nextPlayerId);
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        int[] playerIds = new int[mapping.size()];
        int i = 0;

        for (Map.Entry<UUID, Integer> entry : mapping.entrySet()) {
            UUID uuid = entry.getKey();
            compound.setLong(PROFILE_PREFIX + i + "_most", uuid.getMostSignificantBits());
            compound.setLong(PROFILE_PREFIX + i + "_least", uuid.getLeastSignificantBits());
            playerIds[i] = entry.getValue();
            i++;
        }

        compound.setIntArray(TAG_PLAYER_IDS, playerIds);
        return compound;
    }

}
