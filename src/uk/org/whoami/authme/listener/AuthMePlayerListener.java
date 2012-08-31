/*
 * Copyright 2011 Sebastian Köhler <sebkoehler@whoami.org.uk>.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.org.whoami.authme.listener;

import java.util.Date;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerLoginEvent.Result;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;

import uk.org.whoami.authme.cache.auth.PlayerAuth;
import uk.org.whoami.authme.cache.auth.PlayerCache;
import uk.org.whoami.authme.cache.limbo.LimboPlayer;
import uk.org.whoami.authme.cache.limbo.LimboCache;
import uk.org.whoami.authme.citizens.CitizensCommunicator;
import uk.org.whoami.authme.datasource.DataSource;
import uk.org.whoami.authme.settings.Messages;
import uk.org.whoami.authme.task.MessageTask;
import uk.org.whoami.authme.task.TimeoutTask;


public class AuthMePlayerListener implements Listener {

    private Messages m = Messages.getInstance();
    private JavaPlugin plugin;
    private DataSource data;

    public AuthMePlayerListener(JavaPlugin plugin, DataSource data) {
        this.plugin = plugin;
        this.data = data;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
        if (event.isCancelled() || event.getPlayer() == null) {
            return;
        }

        Player player = event.getPlayer();
        String name = player.getName().toLowerCase();
        
        if (CitizensCommunicator.isNPC(player)) {
            return;
        }

        if (PlayerCache.getInstance().isAuthenticated(name)) {
            return;
        }

        if (!data.isAuthAvailable(name)) {
            if (!plugin.getConfig().getBoolean("settings.restrictions.ForceSingleSession")) {
                return;
            }
        }

        String msg = event.getMessage();
        //WorldEdit GUI Shit
        if (msg.equalsIgnoreCase("/worldedit cui")) {
            return;
        }

        String cmd = msg.split(" ")[0];
        if (cmd.equalsIgnoreCase("/login") || cmd.equalsIgnoreCase("/register")) {
            return;
        }

        event.setMessage("/notloggedin");
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChat(PlayerChatEvent event) {
        if (event.isCancelled() || event.getPlayer() == null) {
            return;
        }

        Player player = event.getPlayer();
        String name = player.getName().toLowerCase();

        if (CitizensCommunicator.isNPC(player)) {
            return;
        }

        if (PlayerCache.getInstance().isAuthenticated(name)) {
            return;
        }

        if (data.isAuthAvailable(name)) {
            player.sendMessage(m._("login_msg"));
        } else {
            if (!plugin.getConfig().getBoolean("settings.restrictions.ForceSingleSession")) {
                return;
            }
            if (plugin.getConfig().getBoolean("settings.restrictions.allowChat")) {
                return;
            }
            player.sendMessage(m._("reg_msg"));
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.isCancelled() || event.getPlayer() == null) {
            return;
        }

        Player player = event.getPlayer();
        String name = player.getName().toLowerCase();

        if (CitizensCommunicator.isNPC(player)) {
            return;
        }

        if (PlayerCache.getInstance().isAuthenticated(name)) {
            return;
        }

        if (data.isAuthAvailable(name)) {
            event.setTo(event.getFrom());
            return;
        }

        if (!plugin.getConfig().getBoolean("settings.restrictions.ForceSingleSession")) {
            return;
        }

        if (!plugin.getConfig().getBoolean("settings.restrictions.allowMovement")) {
            event.setTo(event.getFrom());
            return;
        }

        if (plugin.getConfig().getInt("settings.restrictions.allowedMovementRadius") == 0) {
            return;
        }

        int radius = plugin.getConfig().getInt("settings.restrictions.allowedMovementRadius");
        Location spawn = player.getWorld().getSpawnLocation();
        Location to = event.getTo();

        if (to.getX() > spawn.getX() + radius || to.getX() < spawn.getX() - radius ||
            to.getY() > spawn.getY() + radius || to.getY() < spawn.getY() - radius ||
            to.getZ() > spawn.getZ() + radius || to.getZ() < spawn.getZ() - radius) {
            event.setTo(event.getFrom());
        }
    }
    
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerLogin(PlayerLoginEvent event) {
        if (event.getResult() != Result.ALLOWED || event.getPlayer() == null) {
            return;
        }

        Player player = event.getPlayer();
        String name = player.getName().toLowerCase();

        if (CitizensCommunicator.isNPC(player)) {
            return;
        }
        
       

        int min = plugin.getConfig().getInt("settings.restrictions.minNicknameLength");
        int max = plugin.getConfig().getInt("settings.restrictions.maxNicknameLength");
        String regex = plugin.getConfig().getString("settings.restrictions.maxNicknameLength");

        if (name.length() > max || name.length() < min) {
            event.disallow(Result.KICK_OTHER, "Your nickname has the wrong length. MaxLen: " + max + ", MinLen: " + min);
            return;
        }
        if (!player.getName().matches(regex) || name.equals("Player")) {
            event.disallow(Result.KICK_OTHER, "Your nickname contains illegal characters. Allowed chars: " + regex);
            return;
        }

        //Check if forceSingleSession is set to true, so kick player that has joined with same nick of online player
        if(player.isOnline() && plugin.getConfig().getBoolean("settings.restrictions.ForceSingleSession")) {
               //System.out.println("[Debug name] "+player.getName());
               player.kickPlayer(m._("same_nick"));
               event.disallow(PlayerLoginEvent.Result.KICK_OTHER, m._("same_nick"));  
               return;
        } 
       /* // OLD METHOD 
        for (Player onlinePlayer : plugin.getServer().getOnlinePlayers()) {
            System.out.println("[Debug name 3] "+onlinePlayer.getName());
            if (onlinePlayer.getName().equalsIgnoreCase(player.getName())) {
                System.out.println("[Debug name 2] "+onlinePlayer.getName());
                event.disallow(Result.KICK_OTHER, m._("same_nick"));
                return;
            }
        } */
       
        if (plugin.getConfig().getBoolean("settings.restrictions.kickNonRegistered")) {
            if (!data.isAuthAvailable(name)) {
                event.disallow(Result.KICK_OTHER, m._("reg_only"));
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (event.getPlayer() == null) {
            return;
        }

        Player player = event.getPlayer();
        String name = player.getName().toLowerCase();
        String ip = player.getAddress().getAddress().getHostAddress();

        if (CitizensCommunicator.isNPC(player)) {
            return;
        }
        
        if (PlayerCache.getInstance().isAuthenticated(name)) {
            return;
        }

        if (data.isAuthAvailable(name)) {
            if (plugin.getConfig().getBoolean("settings.sessions.enabled")) {
                PlayerAuth auth = data.getAuth(name);
                long timeout = plugin.getConfig().getInt("settings.sessions.timeout") * 60000;
                long lastLogin = auth.getLastLogin();
                long cur = new Date().getTime();

                if (auth.getNickname().equals(name) && auth.getIp().equals(ip) && (cur - lastLogin < timeout || timeout == 0)) {
                    PlayerCache.getInstance().addPlayer(auth);
                    player.sendMessage(m._("valid_session"));
                    return;
                }
            }
        } else {
            if (!plugin.getConfig().getBoolean("settings.restrictions.ForceSingleSession")) {
                return;
            }
        }

        LimboCache.getInstance().addLimboPlayer(player);
        player.getInventory().setArmorContents(new ItemStack[0]);
        player.getInventory().setContents(new ItemStack[36]);
        player.setGameMode(GameMode.SURVIVAL);
        if (plugin.getConfig().getBoolean("settings.restrictions.teleportUnAuthedToSpawn")) {
            player.teleport(player.getWorld().getSpawnLocation());
        }

        String msg = data.isAuthAvailable(name) ? m._("login_msg") : m._("reg_msg");
        int time = plugin.getConfig().getInt("settings.restrictions.timeout") * 20;
        int msgInterval = plugin.getConfig().getInt("settings.registration.messageInterval");
        BukkitScheduler sched = plugin.getServer().getScheduler();
        if (time != 0) {
            int id = sched.scheduleSyncDelayedTask(plugin, new TimeoutTask(plugin, name), time);
            LimboCache.getInstance().getLimboPlayer(name).setTimeoutTaskId(id);
        }
        sched.scheduleSyncDelayedTask(plugin, new MessageTask(plugin, name, msg, msgInterval));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (event.getPlayer() == null) {
            return;
        }

        Player player = event.getPlayer();

        if (CitizensCommunicator.isNPC(player)) {
            return;
        }

        String name = player.getName().toLowerCase();
        if (LimboCache.getInstance().hasLimboPlayer(name)) {
            LimboPlayer limbo = LimboCache.getInstance().getLimboPlayer(name);
            player.getInventory().setArmorContents(limbo.getArmour());
            player.getInventory().setContents(limbo.getInventory());
            plugin.getServer().getScheduler().cancelTask(limbo.getTimeoutTaskId());
            LimboCache.getInstance().deleteLimboPlayer(name);
        }
        PlayerCache.getInstance().removePlayer(name);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerKick(PlayerKickEvent event) {
        if (event.getPlayer() == null) {
            return;
        }

        Player player = event.getPlayer();

        if (CitizensCommunicator.isNPC(player)) {
            return;
        }
        
        // Check for Minecraft message kick request on same nickname
	// Work only for off-line server
		if (plugin.getConfig().getBoolean("settings.registration.force")) {
			if (event.getReason().equals("You logged in from another location")) {
                            //System.out.println("[Debug same nick] "+event.getReason());	
                            event.setCancelled(true); }
                }
             
        String name = player.getName().toLowerCase();
        if (LimboCache.getInstance().hasLimboPlayer(name)) {
            LimboPlayer limbo = LimboCache.getInstance().getLimboPlayer(name);
            player.getInventory().setArmorContents(limbo.getArmour());
            player.getInventory().setContents(limbo.getInventory());
            player.teleport(limbo.getLoc());
            plugin.getServer().getScheduler().cancelTask(limbo.getTimeoutTaskId());
            LimboCache.getInstance().deleteLimboPlayer(name);
        }
        PlayerCache.getInstance().removePlayer(name);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerPickupItem(PlayerPickupItemEvent event) {
        if (event.isCancelled() || event.getPlayer() == null) {
            return;
        }

        Player player = event.getPlayer();
        String name = player.getName().toLowerCase();

        if (CitizensCommunicator.isNPC(player)) {
            return;
        }

        if (PlayerCache.getInstance().isAuthenticated(player.getName().toLowerCase())) {
            return;
        }

        if (!data.isAuthAvailable(name)) {
            if (!plugin.getConfig().getBoolean("settings.restrictions.ForceSingleSession")) {
                return;
            }
        }

        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.isCancelled() || event.getPlayer() == null) {
            return;
        }

        Player player = event.getPlayer();
        String name = player.getName().toLowerCase();

        if (CitizensCommunicator.isNPC(player)) {
            return;
        }

        if (PlayerCache.getInstance().isAuthenticated(player.getName().toLowerCase())) {
            return;
        }

        if (!data.isAuthAvailable(name)) {
            if (!plugin.getConfig().getBoolean("settings.restrictions.ForceSingleSession")) {
                return;
            }
        }

        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (event.isCancelled() || event.getPlayer() == null) {
            return;
        }

        Player player = event.getPlayer();
        String name = player.getName().toLowerCase();

        if (CitizensCommunicator.isNPC(player)) {
            return;
        }

        if (PlayerCache.getInstance().isAuthenticated(player.getName().toLowerCase())) {
            return;
        }

        if (!data.isAuthAvailable(name)) {
            if (!plugin.getConfig().getBoolean("settings.restrictions.ForceSingleSession")) {
                return;
            }
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        if (event.isCancelled() || event.getPlayer() == null) {
            return;
        }
        Player player = event.getPlayer();
        String name = player.getName().toLowerCase();

        if (CitizensCommunicator.isNPC(player)) {
            return;
        }

        if (PlayerCache.getInstance().isAuthenticated(player.getName().toLowerCase())) {
            return;
        }

        if (!data.isAuthAvailable(name)) {
            if (!plugin.getConfig().getBoolean("settings.restrictions.ForceSingleSession")) {
                return;
            }
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerBedEnter(PlayerBedEnterEvent event) {
        if (event.isCancelled() || event.getPlayer() == null) {
            return;
        }
        Player player = event.getPlayer();
        String name = player.getName().toLowerCase();

        if (CitizensCommunicator.isNPC(player)) {
            return;
        }

        if (PlayerCache.getInstance().isAuthenticated(player.getName().toLowerCase())) {
            return;
        }

        if (!data.isAuthAvailable(name)) {
            if (!plugin.getConfig().getBoolean("settings.restrictions.ForceSingleSession")) {
                return;
            }
        }
        event.setCancelled(true);
    }
    
}
