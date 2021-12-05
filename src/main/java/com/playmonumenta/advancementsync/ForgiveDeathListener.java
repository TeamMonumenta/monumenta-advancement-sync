package com.playmonumenta.advancementsync;

import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.PigZombie;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.plugin.Plugin;

public class ForgiveDeathListener implements Listener {
	private static ForgiveDeathListener INSTANCE = null;
	private static Plugin mPlugin = null;

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
	public void playerDeathEvent(PlayerDeathEvent event) {
		Player player = event.getEntity();
		for (World world : Bukkit.getWorlds()) {
			Boolean forgiveDeadPlayers = world.getGameRuleValue(GameRule.FORGIVE_DEAD_PLAYERS);
			if (forgiveDeadPlayers == null || forgiveDeadPlayers == false) {
				continue;
			}
			for (Entity entity : world.getEntities()) {
				if (!(entity instanceof Mob)) {
					continue;
				}
				Mob mob = (Mob) entity;
				if (!player.equals(mob.getTarget())) {
					continue;
				}
				mob.setTarget(null);
				if (mob instanceof PigZombie) {
					((PigZombie) mob).setAngry(false);
				}
			}
		}
	}
}
