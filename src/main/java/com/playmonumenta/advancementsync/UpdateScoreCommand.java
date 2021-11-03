package com.playmonumenta.advancementsync;

import java.util.HashMap;
import java.util.Map;

import com.playmonumenta.advancementsync.utils.DataPackUtils;

import org.bukkit.Bukkit;
import org.bukkit.scoreboard.Team;

import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.CommandPermission;
import dev.jorel.commandapi.arguments.ObjectiveArgument;

public class UpdateScoreCommand {
	static final String COMMAND = "updatescore";

	public static void register() {
		CommandPermission perms = CommandPermission.fromString("monumenta.command.updatescore");

		new CommandAPICommand(COMMAND)
			.withPermission(perms)
			.withArguments(new ObjectiveArgument("objective"))
			.executes((sender, args) -> {
				run((String)args[0]);
			})
			.register();
	}

	private static void run(String objective) {
		AdvancementManager.getInstance().updateObjective(objective);
	}
}
