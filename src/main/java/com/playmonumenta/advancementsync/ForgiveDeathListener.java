package com.playmonumenta.advancementsync;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.PigZombie;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.plugin.Plugin;

public class ForgiveDeathListener implements Listener {
	private static final int INT_BIT_SHIFT = 32;
	private static final long INT_MASK = 0xffffffffL;

	private static ForgiveDeathListener INSTANCE = null;
	private static Plugin mPlugin = null;

	private static Map<UUID, UUID> mLastPlayerWorld = new HashMap<>();
	private static Map<UUID, Long> mLastPlayerChunkKey = new HashMap<>();
	// Map<Player UUID, Map<World UID, Set<Chunk Key>>>
	private static Map<UUID, Map<UUID, Set<Long>>> mPlayerNearChunks = new HashMap<>();
	// Map<World UID, Map<Chunk Key, Set<Player UUID>>>
	private static Map<UUID, Map<Long, Set<UUID>>> mPlayerForgiveChunks = new HashMap<>();

	private ForgiveDeathListener(Plugin plugin) {
		INSTANCE = this;
		mPlugin = plugin;
	}

	public static ForgiveDeathListener getInstance() {
		return INSTANCE;
	}

	public static ForgiveDeathListener getInstance(Plugin plugin) {
		if (INSTANCE == null) {
			INSTANCE = new ForgiveDeathListener(plugin);
		}
		return INSTANCE;
	}

	@EventHandler(priority = EventPriority.LOW)
	public void playerJoinEvent(PlayerJoinEvent event) {
		trackPlayerLocation(event.getPlayer());
	}

	@EventHandler(priority = EventPriority.LOW)
	public void playerMoveEvent(PlayerJoinEvent event) {
		trackPlayerLocation(event.getPlayer());
	}

	@EventHandler(priority = EventPriority.LOW)
	public void playerDeathEvent(PlayerDeathEvent event) {
		Player player = event.getEntity();
		UUID playerUuid = player.getUniqueId();

		// Track which players to forgive by world/chunk
		Map<UUID, Set<Long>> visitedChunks = mPlayerNearChunks.get(playerUuid);
		if (visitedChunks == null) {
			return;
		}
		for (Map.Entry<UUID, Set<Long>> worldChunkEntry : visitedChunks.entrySet()) {
			UUID worldUid = worldChunkEntry.getKey();
			Set<Long> chunkKeys = worldChunkEntry.getValue();

			if (!mPlayerForgiveChunks.containsKey(worldUid)) {
				mPlayerForgiveChunks.put(worldUid, new HashMap<Long, Set<UUID>>());
			}
			Map<Long, Set<UUID>> worldChunks = mPlayerForgiveChunks.get(worldUid);
			for (Long chunkKey : chunkKeys) {
				if (!worldChunks.containsKey(chunkKey)) {
					worldChunks.put(chunkKey, new HashSet<UUID>());
				}
				worldChunks.get(chunkKey).add(playerUuid);
			}
		}
		visitedChunks.clear();

		// Handle loaded chunks
		for (World world : Bukkit.getWorlds()) {
			Boolean forgiveDeadPlayers = world.getGameRuleValue(GameRule.FORGIVE_DEAD_PLAYERS);
			if (forgiveDeadPlayers == null || forgiveDeadPlayers == false) {
				continue;
			}
			for (Chunk chunk : world.getLoadedChunks()) {
				forgiveChunk(chunk);
			}
		}
	}

	@EventHandler(priority = EventPriority.LOW)
	public void chunkLoadEvent(ChunkLoadEvent event) {
		forgiveChunk(event.getChunk());
	}

	public void forgiveChunk(Chunk chunk) {
		World world = chunk.getWorld();
		Boolean forgiveDeadPlayers = world.getGameRuleValue(GameRule.FORGIVE_DEAD_PLAYERS);
		if (forgiveDeadPlayers == null || forgiveDeadPlayers == false) {
			return;
		}

		UUID worldUid = world.getUID();
		Map<Long, Set<UUID>> chunkForgivenessEntry = mPlayerForgiveChunks.get(worldUid);
		if (chunkForgivenessEntry == null) {
			return;
		}
		long chunkKey = getChunkKey(chunk);
		Set<UUID> players = chunkForgivenessEntry.get(chunkKey);
		if (players == null) {
			return;
		}

		for (Entity entity : chunk.getEntities()) {
			if (!(entity instanceof Mob)) {
				continue;
			}
			Mob mob = (Mob) entity;
			Entity target = mob.getTarget();
			if (target == null || !(target instanceof OfflinePlayer)) {
				continue;
			}
			OfflinePlayer targetPlayer = (OfflinePlayer) target;
			UUID targetPlayerUuid = targetPlayer.getUniqueId();
			if (!players.contains(targetPlayerUuid)) {
				continue;
			}
			mob.setTarget(null);
			if (mob instanceof PigZombie) {
				((PigZombie) mob).setAngry(false);
			}
		}

		chunkForgivenessEntry.remove(chunkKey);
		if (chunkForgivenessEntry.isEmpty()) {
			mPlayerForgiveChunks.remove(worldUid);
		}
	}

	public void trackPlayerLocation(Player player) {
		UUID playerUuid = player.getUniqueId();
		Location loc = player.getLocation();
		World world = loc.getWorld();
		int viewDistance = world.getViewDistance();
		UUID worldUid = world.getUID();
		Chunk chunk = loc.getChunk();
		Long chunkKey = getChunkKey(chunk);
		// Includes an extra chunk on each axis as a precaution
		int minX = chunk.getX() - viewDistance - 1;
		int maxX = chunk.getX() + viewDistance + 1;
		int minZ = chunk.getZ() - viewDistance - 1;
		int maxZ = chunk.getZ() + viewDistance + 1;

		UUID oldWorldUid = mLastPlayerWorld.get(playerUuid);
		Long oldChunkKey = mLastPlayerChunkKey.get(playerUuid);

		if (worldUid.equals(oldWorldUid)) {
			if (chunkKey.equals(oldChunkKey)) {
				return;
			}
			// Player changed chunks
			mLastPlayerChunkKey.put(playerUuid, chunkKey);

			Set<Long> nearbyChunks = getNearbyPlayerChunksSet(playerUuid, worldUid);
			for (int z = minX; z <= maxZ; z++) {
				for (int x = minX; x <= maxX; x++) {
					nearbyChunks.add(getChunkKey(x, z));
				}
			}
		} else {
			// Player changed worlds (or logged in for the first time this restart)
			mLastPlayerWorld.put(playerUuid, worldUid);
			mLastPlayerChunkKey.put(playerUuid, chunkKey);

			Set<Long> nearbyChunks = getNearbyPlayerChunksSet(playerUuid, worldUid);
			for (int z = minX; z <= maxZ; z++) {
				for (int x = minX; x <= maxX; x++) {
					nearbyChunks.add(getChunkKey(x, z));
				}
			}
		}
	}

	public Set<Long> getNearbyPlayerChunksSet(UUID playerUuid, UUID worldUid) {
		if (!mPlayerNearChunks.containsKey(playerUuid)) {
			mPlayerNearChunks.put(playerUuid, new HashMap<UUID, Set<Long>>());
		}
		Map<UUID, Set<Long>> nearbyChunks = mPlayerNearChunks.get(playerUuid);
		if (!nearbyChunks.containsKey(worldUid)) {
			nearbyChunks.put(worldUid, new HashSet<Long>());
		}
		return nearbyChunks.get(worldUid);
	}

	public static long getChunkKey(Chunk chunk) {
		return getChunkKey(chunk.getX(), chunk.getZ());
	}

	public static long getChunkKey(int cx, int cz) {
		return ((long) cx) & INT_MASK | (((long) cz) & INT_MASK) << INT_BIT_SHIFT;
	}

	public static int getChunkKeyX(long chunkKey) {
		return (int)(chunkKey & INT_MASK);
	}

	public static int getChunkKeyZ(long chunkKey) {
		return (int)(chunkKey >>> INT_MASK & INT_MASK);
	}
}
