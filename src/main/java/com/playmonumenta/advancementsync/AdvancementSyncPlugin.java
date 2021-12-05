package com.playmonumenta.advancementsync;

import org.bukkit.plugin.java.JavaPlugin;

public class AdvancementSyncPlugin extends JavaPlugin {
	public AdvancementManager mAdvancementManager = null;
	public ForgiveDeathListener mForgiveDeathListener = null;

	@Override
	public void onLoad() {
		SetupTeamCommand.register();
		UpdateScoreCommand.register();
	}

	@Override
	public void onEnable() {
		mAdvancementManager = AdvancementManager.getInstance(this);
		mForgiveDeathListener = ForgiveDeathListener.getInstance(this);

		getServer().getPluginManager().registerEvents(mAdvancementManager, this);
		getServer().getPluginManager().registerEvents(mForgiveDeathListener, this);

		mAdvancementManager.reload();
	}

	@Override
	public void onDisable() {
		if (mAdvancementManager != null) {
			mAdvancementManager.saveState();
		}
	}
}
