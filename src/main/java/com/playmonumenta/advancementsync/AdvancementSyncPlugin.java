package com.playmonumenta.advancementsync;

import org.bukkit.plugin.java.JavaPlugin;

public class AdvancementSyncPlugin extends JavaPlugin {
	public AdvancementManager mAdvancementManager = null;

	@Override
	public void onLoad() {
		SetupTeamCommand.register();
	}

	@Override
	public void onEnable() {
		mAdvancementManager = AdvancementManager.getInstance(this);

		getServer().getPluginManager().registerEvents(mAdvancementManager, this);

		mAdvancementManager.reload();
	}

	@Override
	public void onDisable() {
		if (mAdvancementManager != null) {
			mAdvancementManager.saveState();
		}
	}
}
