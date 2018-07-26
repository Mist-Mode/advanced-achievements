package com.hm.achievement.listener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.FireworkEffect.Builder;
import org.bukkit.FireworkEffect.Type;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.advancement.Advancement;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;

import com.hm.achievement.AdvancedAchievements;
import com.hm.achievement.advancement.AchievementAdvancement;
import com.hm.achievement.advancement.AdvancementManager;
import com.hm.achievement.command.executable.ToggleCommand;
import com.hm.achievement.db.AbstractDatabaseManager;
import com.hm.achievement.db.CacheManager;
import com.hm.achievement.lang.LangHelper;
import com.hm.achievement.lang.ListenerLang;
import com.hm.achievement.lifecycle.Reloadable;
import com.hm.achievement.utils.PlayerAdvancedAchievementEvent;
import com.hm.achievement.utils.RewardParser;
import com.hm.mcshared.file.CommentedYamlConfiguration;
import com.hm.mcshared.particle.FancyMessageSender;
import com.hm.mcshared.particle.ParticleEffect;

import net.milkbowl.vault.economy.Economy;

/**
 * Listener class to deal with achievement receptions: rewards, display and database operations.
 *
 * @author Pyves
 */
@Singleton
public class PlayerAdvancedAchievementListener implements Listener, Reloadable {

	private static final Random RANDOM = new Random();

	private final CommentedYamlConfiguration mainConfig;
	private final CommentedYamlConfiguration langConfig;
	private final int serverVersion;
	private final Logger logger;
	private final StringBuilder pluginHeader;
	private final CacheManager cacheManager;
	private final AdvancedAchievements advancedAchievements;
	private final RewardParser rewardParser;
	private final Map<String, String> achievementsAndDisplayNames;
	private final AbstractDatabaseManager sqlDatabaseManager;
	private final ToggleCommand toggleCommand;
	private final FireworkListener fireworkListener;

	private boolean configRewardCommandNotif;
	private String configFireworkStyle;
	private boolean configFirework;
	private boolean configSimplifiedReception;
	private boolean configTitleScreen;
	private boolean configNotifyOtherPlayers;
	private boolean configActionBarNotify;
	private boolean configHoverableReceiverChatText;

	private String langCommandReward;
	private String langAchievementReceived;
	private String langItemRewardReceived;
	private String langMoneyRewardReceived;
	private String langExperienceRewardReceived;
	private String langIncreaseMaxHealthRewardReceived;
	private String langIncreaseMaxOxygenRewardReceived;
	private String langAchievementNew;
	private String langCustomMessageCommandReward;
	private String langAllAchievementsReceived;

	@Inject
	public PlayerAdvancedAchievementListener(@Named("main") CommentedYamlConfiguration mainConfig,
			@Named("lang") CommentedYamlConfiguration langConfig, int serverVersion, Logger logger,
			StringBuilder pluginHeader, CacheManager cacheManager, AdvancedAchievements advancedAchievements,
			RewardParser rewardParser, Map<String, String> achievementsAndDisplayNames,
			AbstractDatabaseManager sqlDatabaseManager, ToggleCommand toggleCommand, FireworkListener fireworkListener) {
		this.mainConfig = mainConfig;
		this.langConfig = langConfig;
		this.serverVersion = serverVersion;
		this.logger = logger;
		this.pluginHeader = pluginHeader;
		this.cacheManager = cacheManager;
		this.advancedAchievements = advancedAchievements;
		this.rewardParser = rewardParser;
		this.achievementsAndDisplayNames = achievementsAndDisplayNames;
		this.sqlDatabaseManager = sqlDatabaseManager;
		this.toggleCommand = toggleCommand;
		this.fireworkListener = fireworkListener;
	}

	@Override
	public void extractConfigurationParameters() {
		configFireworkStyle = mainConfig.getString("FireworkStyle", "BALL_LARGE");
		configFirework = mainConfig.getBoolean("Firework", true);
		configSimplifiedReception = mainConfig.getBoolean("SimplifiedReception", false);
		configTitleScreen = mainConfig.getBoolean("TitleScreen", true);
		// Title screens introduced in Minecraft 1.8. Automatically relevant parameter for older versions.
		if (configTitleScreen && serverVersion < 8) {
			configTitleScreen = false;
		}
		configNotifyOtherPlayers = mainConfig.getBoolean("NotifyOtherPlayers", false);
		configActionBarNotify = mainConfig.getBoolean("ActionBarNotify", false);
		// Action bars introduced in Minecraft 1.8. Automatically relevant parameter for older versions.
		if (configActionBarNotify && serverVersion < 8) {
			configActionBarNotify = false;
		}
		// No longer available in default config, kept for compatibility with versions prior to 2.1; defines whether
		// a player is notified in case of a command reward.
		configRewardCommandNotif = mainConfig.getBoolean("RewardCommandNotif", true);
		configHoverableReceiverChatText = mainConfig.getBoolean("HoverableReceiverChatText", false);
		// Hoverable chat messages introduced in Minecraft 1.8. Automatically relevant parameter for older versions.
		if (configHoverableReceiverChatText && serverVersion < 8) {
			configHoverableReceiverChatText = false;
		}

		langCommandReward = LangHelper.get(ListenerLang.COMMAND_REWARD, langConfig);
		langAchievementReceived = LangHelper.get(ListenerLang.ACHIEVEMENT_RECEIVED, langConfig) + " " + ChatColor.WHITE;
		langItemRewardReceived = LangHelper.get(ListenerLang.ITEM_REWARD_RECEIVED, langConfig) + " ";
		langMoneyRewardReceived = LangHelper.get(ListenerLang.MONEY_REWARD_RECEIVED, langConfig);
		langExperienceRewardReceived = LangHelper.get(ListenerLang.EXPERIENCE_REWARD_RECEIVED, langConfig);
		langIncreaseMaxHealthRewardReceived = LangHelper.get(ListenerLang.INCREASE_MAX_HEALTH_REWARD_RECEIVED, langConfig);
		langIncreaseMaxOxygenRewardReceived = LangHelper.get(ListenerLang.INCREASE_MAX_OXYGEN_REWARD_RECEIVED, langConfig);
		langAchievementNew = pluginHeader + LangHelper.get(ListenerLang.ACHIEVEMENT_NEW, langConfig) + " " + ChatColor.WHITE;
		langCustomMessageCommandReward = LangHelper.get(ListenerLang.CUSTOM_COMMAND_REWARD, langConfig);
		langAllAchievementsReceived = pluginHeader + LangHelper.get(ListenerLang.ALL_ACHIEVEMENTS_RECEIVED, langConfig);
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onPlayerAdvancedAchievementReception(PlayerAdvancedAchievementEvent event) {
		Player player = event.getPlayer();
		// Achievement could have already been received if MultiCommand is set to true in the configuration.
		if (!cacheManager.hasPlayerAchievement(player.getUniqueId(), event.getName())) {
			cacheManager.registerNewlyReceivedAchievement(player.getUniqueId(), event.getName());

			if (serverVersion >= 12) {
				Advancement advancement = Bukkit.getServer()
						.getAdvancement(new NamespacedKey(advancedAchievements, AdvancementManager.getKey(event.getName())));
				// Matching advancement might not exist if user has not called /aach generate.
				if (advancement != null) {
					player.getAdvancementProgress(advancement).awardCriteria(AchievementAdvancement.CRITERIA_NAME);
				}
			}
		}
		sqlDatabaseManager.registerAchievement(player.getUniqueId(), event.getName(), event.getMessage());

		List<String> rewardTexts = giveRewardsAndPrepareTexts(player, event.getCommandRewards(), event.getCommandMessages(),
				event.getItemReward(), event.getMoneyReward(), event.getExperienceReward(), event.getMaxHealthReward(),
				event.getMaxOxygenReward());
		displayAchievement(player, event.getName(), event.getDisplayName(), event.getMessage(), rewardTexts);

		if (cacheManager.getPlayerTotalAchievements(player.getUniqueId()) == achievementsAndDisplayNames.size()) {
			handleAllAchievementsReceived(player);
		}
	}

	/**
	 * Gives relevant rewards and prepares the texts to be displayed to the receiver.
	 * 
	 * @param player
	 * @param commands
	 * @param commandMessage
	 * @param item
	 * @param money
	 * @param experience
	 * @param health
	 * @param oxygen
	 * @return all the reward texts to be displayed to the user
	 */
	private List<String> giveRewardsAndPrepareTexts(Player player, String[] commands, List<String> commandMessage,
			ItemStack item, int money, int experience, int health, int oxygen) {
		List<String> rewardTexts = new ArrayList<>();
		if (commands != null && commands.length > 0) {
			rewardTexts.addAll(rewardCommands(commands, commandMessage));
		}
		if (item != null) {
			rewardTexts.add(rewardItem(player, item));
		}
		if (money > 0) {
			rewardTexts.add(rewardMoney(player, money));
		}
		if (experience > 0) {
			rewardTexts.add(rewardExperience(player, experience));
		}
		if (health > 0) {
			rewardTexts.add(rewardMaxHealth(player, health));
		}
		if (oxygen > 0) {
			rewardTexts.add(rewardMaxOxygen(player, oxygen));
		}
		return rewardTexts;
	}

	/**
	 * Executes player command rewards.
	 *
	 * @param commands
	 * @param messages
	 * @return the reward text to display to the player
	 */
	private List<String> rewardCommands(String[] commands, List<String> messages) {
		for (String command : commands) {
			advancedAchievements.getServer().dispatchCommand(advancedAchievements.getServer().getConsoleSender(), command);
		}
		if (!configRewardCommandNotif || langCommandReward.length() == 0) {
			return new ArrayList<>();
		}

		if (messages != null) {
			return messages.stream().map(message -> StringUtils.replace(langCustomMessageCommandReward, "MESSAGE", message))
					.collect(Collectors.toList());
		}

		return Collections.singletonList(langCommandReward);
	}

	/**
	 * Gives an item reward to a player.
	 *
	 * @param player
	 * @param item
	 * @return the reward text to display to the player
	 */
	private String rewardItem(Player player, ItemStack item) {
		if (player.getInventory().firstEmpty() != -1) {
			player.getInventory().addItem(item);
		} else {
			player.getWorld().dropItem(player.getLocation(), item);
		}

		String name = item.getItemMeta().getDisplayName();
		if (name == null || name.isEmpty()) {
			name = rewardParser.getItemName(item);
		}

		return langItemRewardReceived + name;
	}

	/**
	 * Gives a money reward to a player.
	 *
	 * @param player
	 * @param amount
	 * @return the reward text to display to the player
	 */
	private String rewardMoney(Player player, int amount) {
		Economy economy = rewardParser.getEconomy();
		if (economy != null) {
			economy.depositPlayer(player, amount);

			String currencyName = rewardParser.getCurrencyName(amount);
			return ChatColor.translateAlternateColorCodes('&',
					StringUtils.replaceOnce(langMoneyRewardReceived, "AMOUNT", amount + " " + currencyName));
		}
		logger.warning("You have specified a money reward but Vault was not linked successfully.");
		return "";
	}

	/**
	 * Gives an experience reward to a player.
	 *
	 * @param player
	 * @param amount
	 * @return the reward text to display to the player
	 */
	private String rewardExperience(Player player, int amount) {
		player.giveExp(amount);
		return ChatColor.translateAlternateColorCodes('&',
				StringUtils.replaceOnce(langExperienceRewardReceived, "AMOUNT", Integer.toString(amount)));
	}

	/**
	 * Gives an increased max health reward to a player.
	 *
	 * @param player
	 * @param amount
	 * @return the reward text to display to the player
	 */
	@SuppressWarnings("deprecation")
	private String rewardMaxHealth(Player player, int amount) {
		if (serverVersion >= 9) {
			AttributeInstance playerAttribute = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
			playerAttribute.setBaseValue(playerAttribute.getBaseValue() + amount);
		} else {
			player.setMaxHealth(player.getMaxHealth() + amount);
		}
		return ChatColor.translateAlternateColorCodes('&',
				StringUtils.replaceOnce(langIncreaseMaxHealthRewardReceived, "AMOUNT", Integer.toString(amount)));
	}

	/**
	 * Gives an increased max oxygen reward to a player.
	 *
	 * @param player
	 * @param amount
	 * @return the reward text to display to the player
	 */
	private String rewardMaxOxygen(Player player, int amount) {
		player.setMaximumAir(player.getMaximumAir() + amount);
		return ChatColor.translateAlternateColorCodes('&',
				StringUtils.replaceOnce(langIncreaseMaxOxygenRewardReceived, "AMOUNT", Integer.toString(amount)));
	}

	/**
	 * Displays chat messages, screen title and launches a firework when a player receives an achievement.
	 *
	 * @param player
	 * @param name
	 * @param displayName
	 * @param message
	 * @param rewardTexts
	 */
	private void displayAchievement(Player player, String name, String displayName, String message,
			List<String> rewardTexts) {
		String nameToShowUser;
		if (StringUtils.isNotBlank(displayName)) {
			// Display name is defined; use it.
			nameToShowUser = ChatColor.translateAlternateColorCodes('&', displayName);
			logger.info("Player " + player.getName() + " received the achievement: " + name + " (" + displayName + ")");
		} else {
			// Use the achievement key name (this name is used in the achievements table in the database).
			nameToShowUser = ChatColor.translateAlternateColorCodes('&', name);
			logger.info("Player " + player.getName() + " received the achievement: " + name);

		}
		String messageToShowUser = ChatColor.translateAlternateColorCodes('&', message);

		displayReceiverMessages(player, nameToShowUser, messageToShowUser, rewardTexts);

		// Notify other online players that the player has received an achievement.
		for (Player p : advancedAchievements.getServer().getOnlinePlayers()) {
			// Notify other players only if NotifyOtherPlayers is enabled and player has not used /aach toggle, or if
			// NotifyOtherPlayers is disabled and player has used /aach toggle.
			if (!p.getName().equals(player.getName()) && (configNotifyOtherPlayers ^ toggleCommand.isPlayerToggled(p))) {
				displayNotification(player, nameToShowUser, p);
			}
		}

		if (configFirework) {
			displayFirework(player);
		} else if (configSimplifiedReception) {
			displaySimplifiedReception(player);
		}

		if (configTitleScreen) {
			displayTitle(player, nameToShowUser, messageToShowUser);
		}
	}

	/**
	 * Displays texts related to the achievement in the receiver's chat. This method can display a single hoverable
	 * message or several messages one after the other.
	 *
	 * @param player
	 * @param nameToShowUser
	 * @param messageToShowUser
	 * @param rewardTexts
	 */
	private void displayReceiverMessages(Player player, String nameToShowUser, String messageToShowUser,
			List<String> rewardTexts) {
		if (configHoverableReceiverChatText) {
			StringBuilder hover = new StringBuilder(messageToShowUser + "\n");
			rewardTexts.stream().filter(StringUtils::isNotBlank)
					.forEach(t -> hover.append(ChatColor.translateAlternateColorCodes('&', t)).append("\n"));
			try {
				FancyMessageSender.sendHoverableMessage(player, langAchievementNew + nameToShowUser,
						hover.substring(0, hover.length() - 1), "white");
				return;
			} catch (Exception e) {
				logger.warning(
						"Failed to display hoverable message for achievement reception. Displaying standard messages instead.");
			}
		}
		player.sendMessage(langAchievementNew + nameToShowUser);
		player.sendMessage(pluginHeader.toString() + ChatColor.WHITE + messageToShowUser);
		rewardTexts.stream().filter(StringUtils::isNotBlank)
				.forEach(t -> player.sendMessage(pluginHeader + ChatColor.translateAlternateColorCodes('&', t)));
	}

	/**
	 * Displays an action bar message or chat notification to another player.
	 *
	 * @param achievementReceiver
	 * @param nameToShowUser
	 * @param otherPlayer
	 */
	private void displayNotification(Player achievementReceiver, String nameToShowUser, Player otherPlayer) {
		if (configActionBarNotify) {
			try {
				FancyMessageSender.sendActionBarMessage(otherPlayer,
						"&o" + StringUtils.replaceOnce(langAchievementReceived, "PLAYER", achievementReceiver.getName())
								+ nameToShowUser);
			} catch (Exception e) {
				logger.warning("Failed to display action bar message for achievement reception notification.");
			}
		} else {
			otherPlayer.sendMessage(
					pluginHeader + StringUtils.replaceOnce(langAchievementReceived, "PLAYER", achievementReceiver.getName())
							+ nameToShowUser);
		}
	}

	/**
	 * Displays title when receiving an achievement.
	 *
	 * @param player
	 * @param nameToShowUser
	 * @param messageToShowUser
	 */
	private void displayTitle(Player player, String nameToShowUser, String messageToShowUser) {
		try {
			FancyMessageSender.sendTitle(player, nameToShowUser, messageToShowUser);
		} catch (Exception e) {
			logger.warning("Failed to display achievement screen title.");
		}
	}

	/**
	 * Launches firework when receiving an achievement.
	 *
	 * @param player
	 */
	private void displayFirework(Player player) {
		Location location = player.getLocation();
		try {
			// Set firework to launch beneath user.
			location.setY(location.getY() - 1);

			Firework firework = player.getWorld().spawn(location, Firework.class);
			FireworkMeta fireworkMeta = firework.getFireworkMeta();
			Builder effectBuilder = FireworkEffect.builder().flicker(false).trail(false)
					.withColor(Color.WHITE.mixColors(Color.BLUE.mixColors(Color.NAVY))).withFade(Color.PURPLE);
			setFireworkType(effectBuilder);
			fireworkMeta.addEffects(effectBuilder.build());
			firework.setVelocity(player.getLocation().getDirection().multiply(0));
			firework.setFireworkMeta(fireworkMeta);

			// Firework launched by plugin: damage will later be cancelled out.
			fireworkListener.addFirework(firework);
		} catch (Exception e) {
			// Particle effect workaround to handle various bugs in early Spigot 1.9 and 1.11 releases. We try to
			// simulate a firework.
			Sound launchSound = serverVersion < 9 ? Sound.valueOf("ENTITY_FIREWORK_LAUNCH")
					: Sound.ENTITY_FIREWORK_ROCKET_LAUNCH;
			player.getWorld().playSound(location, launchSound, 1, 0.6f);
			if (serverVersion >= 13) {
				player.spawnParticle(Particle.FIREWORKS_SPARK, player.getLocation(), 500, 0, 3, 0, 0.1f);
			} else {
				ParticleEffect.FIREWORKS_SPARK.display(0, 3, 0, 0.1f, 500, player.getLocation(), 1);
			}
			Sound blastSound = serverVersion < 9 ? Sound.valueOf("ENTITY_FIREWORK_BLAST")
					: Sound.ENTITY_FIREWORK_ROCKET_BLAST;
			player.getWorld().playSound(location, blastSound, 1, 0.6f);
			Sound twinkleSound = serverVersion < 9 ? Sound.valueOf("ENTITY_FIREWORK_TWINKLE")
					: Sound.ENTITY_FIREWORK_ROCKET_BLAST;
			player.getWorld().playSound(location, twinkleSound, 1, 0.6f);
		}
	}

	/**
	 * Sets the type of the firwrok, which can either be predefined or random.
	 *
	 * @param effectBuilder
	 */
	private void setFireworkType(Builder effectBuilder) {
		if ("RANDOM".equalsIgnoreCase(configFireworkStyle)) {
			Type[] fireworkTypes = Type.values();
			effectBuilder.with(fireworkTypes[RANDOM.nextInt(fireworkTypes.length)]);
		} else {
			try {
				effectBuilder.with(Type.valueOf(configFireworkStyle.toUpperCase()));
			} catch (Exception e) {
				effectBuilder.with(Type.BALL_LARGE);
				logger.warning(
						"Failed to load FireworkStyle. Please use one of the following: BALL_LARGE, BALL, BURST, CREEPER or STAR.");
			}
		}
	}

	/**
	 * Displays a simplified particle effect and calm sound when receiving an achievement. Is used instead of
	 * displayFirework.
	 *
	 * @param player
	 */
	private void displaySimplifiedReception(Player player) {
		Location location = player.getLocation();
		// If old version, retrieving sound by name as it no longer exists in newer versions.
		Sound sound = serverVersion < 9 ? Sound.valueOf("LEVEL_UP") : Sound.ENTITY_PLAYER_LEVELUP;
		player.getWorld().playSound(location, sound, 1, 0.9f);
		if (serverVersion >= 13) {
			player.spawnParticle(Particle.FIREWORKS_SPARK, player.getLocation(), 500, 0, 3, 0, 0.1f);
		} else {
			ParticleEffect.FIREWORKS_SPARK.display(0, 3, 0, 0.1f, 500, player.getLocation(), 1);
		}
	}

	/**
	 * Handles rewards and displaying messages when a player has received all achievements.
	 * 
	 * @param player
	 */
	private void handleAllAchievementsReceived(Player player) {
		List<String> rewardTexts = giveRewardsAndPrepareTexts(player,
				rewardParser.getCommandRewards("AllAchievementsReceivedRewards", player),
				rewardParser.getCustomCommandMessage("AllAchievementsReceivedRewards"),
				rewardParser.getItemReward("AllAchievementsReceivedRewards"),
				rewardParser.getRewardAmount("AllAchievementsReceivedRewards", "Money"),
				rewardParser.getRewardAmount("AllAchievementsReceivedRewards", "Experience"),
				rewardParser.getRewardAmount("AllAchievementsReceivedRewards", "IncreaseMaxHealth"),
				rewardParser.getRewardAmount("AllAchievementsReceivedRewards", "IncreaseMaxOxygen"));
		player.sendMessage(langAllAchievementsReceived);
		rewardTexts.stream().filter(StringUtils::isNotBlank)
				.forEach(t -> player.sendMessage(pluginHeader + ChatColor.translateAlternateColorCodes('&', t)));
	}
}