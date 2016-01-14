package com.hm.achievement.command;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

import com.hm.achievement.AdvancedAchievements;
import com.hm.achievement.language.Lang;

public class GiveCommand implements Listener {

	private AdvancedAchievements plugin;
	private Boolean multiCommand;

	public GiveCommand(AdvancedAchievements plugin) {

		this.plugin = plugin;
		multiCommand = plugin.getConfig().getBoolean("MultiCommand", true);
	}

	/**
	 * Give an achievement with an in game or console command.
	 */
	public void achievementGive(CommandSender sender, String args[]) {

		String configAchievement = "Commands." + args[1];

		// Retrieve player instance with his name.
		Player player = null;
		for (Player currentPlayer : Bukkit.getOnlinePlayers()) {
			if (currentPlayer.getName().equalsIgnoreCase(args[2])) {
				player = currentPlayer;
				break;
			}
		}

		// If player not found or is offline.
		if (player == null) {

			sender.sendMessage(plugin.getChatHeader() + Lang.PLAYER_OFFLINE.toString().replaceAll("PLAYER", args[2]));

			return;
		}
		if (plugin.getReward().checkAchievement(configAchievement)) {

			// Check whether player has already received achievement and cannot
			// receive it again.
			if (!multiCommand && plugin.getDb().hasPlayerAchievement(player,
					plugin.getConfig().getString(configAchievement + ".Name"))) {

				sender.sendMessage(plugin.getChatHeader()
						+ Lang.ACHIEVEMENT_ALREADY_RECEIVED.toString().replace("PLAYER", args[2]));
				return;
			}

			plugin.getAchievementDisplay().displayAchievement(player, configAchievement);
			plugin.getDb().registerAchievement(player, plugin.getConfig().getString(configAchievement + ".Name"),
					plugin.getConfig().getString(configAchievement + ".Message"));
			plugin.getReward().checkConfig(player, configAchievement);

			sender.sendMessage(plugin.getChatHeader() + Lang.ACHIEVEMENT_GIVEN);
		} else {
			sender.sendMessage(
					plugin.getChatHeader() + Lang.ACHIEVEMENT_NOT_FOUND.toString().replace("PLAYER", args[2]));
		}
	}
}
