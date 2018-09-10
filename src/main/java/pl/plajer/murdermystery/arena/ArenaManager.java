/*
 * Village Defense 3 - Protect villagers from hordes of zombies
 * Copyright (C) 2018  Plajer's Lair - maintained by Plajer
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package pl.plajer.murdermystery.arena;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import org.apache.commons.lang.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import pl.plajer.murdermystery.Main;
import pl.plajer.murdermystery.handlers.ChatManager;
import pl.plajer.murdermystery.handlers.PermissionsManager;
import pl.plajer.murdermystery.handlers.items.SpecialItemManager;
import pl.plajer.murdermystery.handlers.language.LanguageManager;
import pl.plajer.murdermystery.handlers.language.Locale;
import pl.plajer.murdermystery.murdermysteryapi.MMGameJoinAttemptEvent;
import pl.plajer.murdermystery.murdermysteryapi.MMGameLeaveAttemptEvent;
import pl.plajer.murdermystery.murdermysteryapi.MMGameStopEvent;
import pl.plajer.murdermystery.user.User;
import pl.plajer.murdermystery.user.UserManager;
import pl.plajer.murdermystery.utils.MessageUtils;
import pl.plajerlair.core.services.ReportedException;
import pl.plajerlair.core.utils.MinigameUtils;

/**
 * @author Plajer
 * <p>
 * Created at 13.05.2018
 */
public class ArenaManager {

  private static Main plugin = JavaPlugin.getPlugin(Main.class);

  /**
   * Attempts player to join arena.
   * Calls MMGameJoinAttemptEvent.
   * Can be cancelled only via above-mentioned event
   *
   * @param p player to join
   * @see MMGameJoinAttemptEvent
   */
  public static void joinAttempt(Player p, Arena arena) {
    try {
      Main.debug("Initial join attempt, " + p.getName(), System.currentTimeMillis());
      MMGameJoinAttemptEvent gameJoinAttemptEvent = new MMGameJoinAttemptEvent(p, arena);
      Bukkit.getPluginManager().callEvent(gameJoinAttemptEvent);
      if (!arena.isReady()) {
        p.sendMessage(ChatManager.PLUGIN_PREFIX + ChatManager.colorMessage("In-Game.Arena-Not-Configured"));
        return;
      }
      if (gameJoinAttemptEvent.isCancelled()) {
        p.sendMessage(ChatManager.PLUGIN_PREFIX + ChatManager.colorMessage("In-Game.Join-Cancelled-Via-API"));
        return;
      }
      if (!plugin.isBungeeActivated()) {
        if (!(p.hasPermission(PermissionsManager.getJoinPerm().replace("<arena>", "*")) || p.hasPermission(PermissionsManager.getJoinPerm().replace("<arena>", arena.getID())))) {
          p.sendMessage(ChatManager.PLUGIN_PREFIX + ChatManager.colorMessage("In-Game.Join-No-Permission"));
          return;
        }
      }
      Main.debug("Final join attempt, " + p.getName(), System.currentTimeMillis());
      if ((arena.getArenaState() == ArenaState.IN_GAME || (arena.getArenaState() == ArenaState.STARTING && arena.getTimer() <= 3) || arena.getArenaState() == ArenaState.ENDING)) {
        if (plugin.isInventoryManagerEnabled()) {
          p.setLevel(0);
          plugin.getInventoryManager().saveInventoryToFile(p);
        }
        arena.teleportToStartLocation(p);
        p.sendMessage(ChatManager.colorMessage("In-Game.You-Are-Spectator"));
        p.getInventory().clear();

        ItemStack spectatorItem = new ItemStack(Material.COMPASS, 1);
        ItemMeta spectatorMeta = spectatorItem.getItemMeta();
        spectatorMeta.setDisplayName(ChatManager.colorMessage("In-Game.Spectator.Spectator-Item-Name"));
        spectatorItem.setItemMeta(spectatorMeta);
        p.getInventory().setItem(0, spectatorItem);

        p.getInventory().setItem(8, SpecialItemManager.getSpecialItem("Leave").getItemStack());

        for (PotionEffect potionEffect : p.getActivePotionEffects()) {
          p.removePotionEffect(potionEffect.getType());
        }

        arena.addPlayer(p);
        p.setFoodLevel(20);
        p.setGameMode(GameMode.SURVIVAL);
        p.setAllowFlight(true);
        p.setFlying(true);
        User user = UserManager.getUser(p.getUniqueId());
        user.setSpectator(true);
        user.setFakeDead(true);
        user.setInt("gold", 0);
        p.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, Integer.MAX_VALUE, 1));
        ArenaUtils.hidePlayer(p, arena);

        for (Player spectator : arena.getPlayers()) {
          if (UserManager.getUser(spectator.getUniqueId()).isSpectator()) {
            p.hidePlayer(spectator);
          } else {
            p.showPlayer(spectator);
          }
        }
        ArenaUtils.hidePlayersOutsideTheGame(p, arena);
        return;
      }
      if (plugin.isInventoryManagerEnabled()) {
        p.setLevel(0);
        plugin.getInventoryManager().saveInventoryToFile(p);
      }
      arena.teleportToLobby(p);
      arena.addPlayer(p);
      p.setFoodLevel(20);
      p.getInventory().setArmorContents(new ItemStack[]{new ItemStack(Material.AIR), new ItemStack(Material.AIR), new ItemStack(Material.AIR), new ItemStack(Material.AIR)});
      p.setFlying(false);
      p.setAllowFlight(false);
      p.getInventory().clear();
      arena.showPlayers();
      if (plugin.isBossbarEnabled()) {
        arena.getGameBar().addPlayer(p);
      }
      if (!UserManager.getUser(p.getUniqueId()).isSpectator()) {
        ChatManager.broadcastAction(arena, p, ChatManager.ActionType.JOIN);
      }
      if (arena.getArenaState() == ArenaState.STARTING || arena.getArenaState() == ArenaState.WAITING_FOR_PLAYERS) {
        p.getInventory().setItem(SpecialItemManager.getSpecialItem("Leave").getSlot(), SpecialItemManager.getSpecialItem("Leave").getItemStack());
      }
      p.updateInventory();
      for (Player player : arena.getPlayers()) {
        ArenaUtils.showPlayer(player, arena);
      }
      arena.showPlayers();
    } catch (Exception ex) {
      new ReportedException(plugin, ex);
    }
  }

  /**
   * Attempts player to leave arena.
   * Calls MMGameLeaveAttemptEvent event.
   *
   * @param p player to join
   * @see MMGameLeaveAttemptEvent
   */
  public static void leaveAttempt(Player p, Arena arena) {
    try {
      Main.debug("Initial leave attempt, " + p.getName(), System.currentTimeMillis());
      MMGameLeaveAttemptEvent gameLeaveAttemptEvent = new MMGameLeaveAttemptEvent(p, arena);
      Bukkit.getPluginManager().callEvent(gameLeaveAttemptEvent);
      User user = UserManager.getUser(p.getUniqueId());
      if (user.getInt("local_score") > user.getInt("highestscore")) {
        user.setInt("highestscore", user.getInt("local_score"));
      }
      if (arena.getArenaState() == ArenaState.IN_GAME) {
        //-1 cause we didn't remove player yet
        if (arena.getPlayersLeft().size() - 1 == 1) {
          return;
        }
        if (ArenaUtils.isRole(ArenaUtils.Role.MURDERER, p)) {
          List<UUID> players = new ArrayList<>();
          for (Player player : arena.getPlayersLeft()) {
            if (ArenaUtils.isRole(ArenaUtils.Role.ANY_DETECTIVE, player)) {
              continue;
            }
            players.add(player.getUniqueId());
          }
          UUID newMurderer = players.get(new Random().nextInt(players.size() - 1));
          arena.setMurderer(newMurderer);
          for (Player player : arena.getPlayers()) {
            MessageUtils.sendTitle(player, ChatManager.colorMessage("In-Game.Messages.Previous-Role-Left-Title").replace("%role%",
                    ChatManager.colorMessage("Scoreboard.Roles.Murderer")), 5, 40, 5);
            MessageUtils.sendSubTitle(player, ChatManager.colorMessage("In-Game.Messages.Previous-Role-Left-Subtitle").replace("%role%",
                    ChatManager.colorMessage("Scoreboard.Roles.Murderer")), 5, 40, 5);
          }
          MessageUtils.sendTitle(Bukkit.getPlayer(newMurderer), ChatManager.colorMessage("In-Game.Messages.Role-Set.Murderer-Title"), 5, 40, 5);
          MessageUtils.sendSubTitle(Bukkit.getPlayer(newMurderer), ChatManager.colorMessage("In-Game.Messages.Role-Set.Murderer-Subtitle"), 5, 40, 5);
          Bukkit.getPlayer(newMurderer).getInventory().setItem(1, new ItemStack(Material.IRON_SWORD, 1));
          user.setInt("contribution_murderer", 1);
        } else if (ArenaUtils.isRole(ArenaUtils.Role.ANY_DETECTIVE, p)) {
          arena.setDetectiveDead(true);
          if (ArenaUtils.isRole(ArenaUtils.Role.FAKE_DETECTIVE, p)) {
            arena.setFakeDetective(null);
          } else {
            user.setInt("contribution_detective", 1);
          }
          ArenaUtils.dropBowAndAnnounce(arena, p);
        }
        ArenaUtils.spawnCorpse(p, arena);
      }
      p.getInventory().clear();
      p.getInventory().setArmorContents(null);
      arena.removePlayer(p);
      if (!user.isSpectator()) {
        ChatManager.broadcastAction(arena, p, ChatManager.ActionType.LEAVE);
      }
      p.setGlowing(false);
      user.setFakeDead(false);
      user.setSpectator(false);
      user.removeScoreboard();
      if (plugin.isBossbarEnabled()) {
        arena.getGameBar().removePlayer(p);
      }
      p.setFoodLevel(20);
      p.setFlying(false);
      p.setAllowFlight(false);
      for (PotionEffect effect : p.getActivePotionEffects()) {
        p.removePotionEffect(effect.getType());
      }
      p.setFireTicks(0);
      if (arena.getPlayers().size() == 0) {
        arena.setArenaState(ArenaState.ENDING);
        arena.setTimer(0);
      }

      p.setGameMode(GameMode.SURVIVAL);
      for (Player players : plugin.getServer().getOnlinePlayers()) {
        if (ArenaRegistry.getArena(players) != null) {
          players.showPlayer(p);
        }
        p.showPlayer(players);
      }
      arena.teleportToEndLocation(p);
      if (!plugin.isBungeeActivated() && plugin.isInventoryManagerEnabled()) {
        plugin.getInventoryManager().loadInventory(p);
      }
      user.setInt("gold", 0);
      user.setInt("local_score", 0);
      user.setInt("local_kills", 0);
    } catch (Exception ex) {
      new ReportedException(plugin, ex);
    }
  }

  /**
   * Stops current arena. Calls MMGameStopEvent event
   *
   * @param quickStop should arena be stopped immediately? (use only in important cases)
   * @see MMGameStopEvent
   */
  public static void stopGame(boolean quickStop, Arena arena) {
    try {
      Main.debug("Game stop event initiate, arena " + arena.getID(), System.currentTimeMillis());
      if (arena.getArenaState() != ArenaState.IN_GAME) {
        Main.debug("Game stop event finish, arena " + arena.getID(), System.currentTimeMillis());
        return;
      }
      MMGameStopEvent gameStopEvent = new MMGameStopEvent(arena);
      Bukkit.getPluginManager().callEvent(gameStopEvent);
      List<String> summaryMessages;
      if (LanguageManager.getPluginLocale() == Locale.ENGLISH) {
        summaryMessages = LanguageManager.getLanguageFile().getStringList("In-Game.Messages.Game-End-Messages.Summary-Message");
      } else {
        summaryMessages = Arrays.asList(ChatManager.colorMessage("In-Game.Messages.Game-End-Messages.Summary-Message").split(";"));
      }
      Random rand = new Random();
      for (final Player p : arena.getPlayers()) {
        User user = UserManager.getUser(p.getUniqueId());
        if (ArenaUtils.isRole(ArenaUtils.Role.FAKE_DETECTIVE, p) || ArenaUtils.isRole(ArenaUtils.Role.INNOCENT, p)) {
          user.setInt("contribution_murderer", rand.nextInt(4) + 1);
          user.setInt("contribution_detective", rand.nextInt(4) + 1);
        }
        p.getInventory().clear();
        p.getInventory().setItem(SpecialItemManager.getSpecialItem("Leave").getSlot(), SpecialItemManager.getSpecialItem("Leave").getItemStack());
        for (String msg : summaryMessages) {
          MessageUtils.sendCenteredMessage(p, formatSummaryPlaceholders(msg, arena));
        }
        user.removeScoreboard();
        if (!quickStop) {
          if (plugin.getConfig().getBoolean("Firework-When-Game-Ends", true)) {
            new BukkitRunnable() {
              int i = 0;

              public void run() {
                if (i == 4 || !arena.getPlayers().contains(p)) {
                  this.cancel();
                }
                MinigameUtils.spawnRandomFirework(p.getLocation());
                i++;
              }
            }.runTaskTimer(plugin, 30, 30);
          }
        }
      }
      Main.debug("Game stop event finish, arena " + arena.getID(), System.currentTimeMillis());
    } catch (Exception ex) {
      new ReportedException(plugin, ex);
    }
  }

  private static String formatSummaryPlaceholders(String msg, Arena a) {
    String formatted = msg;
    if (a.getPlayersLeft().size() == 1 && a.getPlayersLeft().get(0).getUniqueId() == a.getMurderer()) {
      formatted = StringUtils.replace(formatted, "%winner%", ChatManager.colorMessage("In-Game.Messages.Game-End-Messages.Winners.Murderer"));
    } else {
      formatted = StringUtils.replace(formatted, "%winner%", ChatManager.colorMessage("In-Game.Messages.Game-End-Messages.Winners.Players"));
    }
    if (a.isDetectiveDead()) {
      formatted = StringUtils.replace(formatted, "%detective%", ChatColor.STRIKETHROUGH + Bukkit.getOfflinePlayer(a.getDetective()).getName());
    } else {
      formatted = StringUtils.replace(formatted, "%detective%", Bukkit.getOfflinePlayer(a.getDetective()).getName());
    }
    if (a.isMurdererDead()) {
      formatted = StringUtils.replace(formatted, "%murderer%", ChatColor.STRIKETHROUGH + Bukkit.getOfflinePlayer(a.getMurderer()).getName());
    } else {
      formatted = StringUtils.replace(formatted, "%murderer%", Bukkit.getOfflinePlayer(a.getMurderer()).getName());
    }
    formatted = StringUtils.replace(formatted, "%murderer_kills%", String.valueOf(UserManager.getUser(a.getMurderer()).getInt("local_kills")));
    formatted = StringUtils.replace(formatted, "%hero%", a.getHero() == null ? ChatManager.colorMessage("In-Game.Messages.Game-End-Messages.Winners.Nobody") : Bukkit.getOfflinePlayer(a.getHero()).getName());
    return formatted;
  }

}