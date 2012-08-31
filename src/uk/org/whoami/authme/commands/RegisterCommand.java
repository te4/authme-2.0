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

package uk.org.whoami.authme.commands;

import java.security.NoSuchAlgorithmException;
import java.util.Date;

import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import org.bukkit.plugin.java.JavaPlugin;
import uk.org.whoami.authme.ConsoleLogger;
import uk.org.whoami.authme.cache.auth.PlayerAuth;
import uk.org.whoami.authme.cache.auth.PlayerCache;
import uk.org.whoami.authme.cache.limbo.LimboCache;
import uk.org.whoami.authme.cache.limbo.LimboPlayer;
import uk.org.whoami.authme.datasource.DataSource;
import uk.org.whoami.authme.security.PasswordSecurity;
import uk.org.whoami.authme.settings.Messages;

public class RegisterCommand implements CommandExecutor {

    private Messages m = Messages.getInstance();
    private DataSource database;
    private JavaPlugin plugin;

    public RegisterCommand(JavaPlugin plugin, DataSource database) {
        this.plugin = plugin;
        this.database = database;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmnd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            return true;
        }

        if (!sender.hasPermission("authme." + label.toLowerCase())) {
            sender.sendMessage(m._("no_perm"));
            return true;
        }

        Player player = (Player) sender;
        String name = player.getName().toLowerCase();
        String ip = player.getAddress().getAddress().getHostAddress();

        if (PlayerCache.getInstance().isAuthenticated(name)) {
            player.sendMessage(m._("logged_in"));
            return true;
        }

        if (!plugin.getConfig().getBoolean("settings.registration.enabled")) {
            player.sendMessage(m._("reg_disabled"));
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(m._("usage_reg"));
            return true;
        }

        if (database.isAuthAvailable(player.getName().toLowerCase())) {
            player.sendMessage(m._("user_regged"));
            return true;
        }

        try {
            String hash = PasswordSecurity.getHash(PasswordSecurity.getPasswordHash(plugin.getConfig().getString("settings.security.passwordHash")), args[0]);

            PlayerAuth auth = new PlayerAuth(name, hash, ip, new Date().getTime());
            if (!database.saveAuth(auth)) {
                player.sendMessage(m._("error"));
                return true;
            }
            PlayerCache.getInstance().addPlayer(auth);

            LimboPlayer limbo = LimboCache.getInstance().getLimboPlayer(name);
            if (limbo != null) {
                player.getInventory().setContents(limbo.getInventory());
                player.getInventory().setArmorContents(limbo.getArmour());
                player.setGameMode(GameMode.getByValue(limbo.getGameMode()));
                if (plugin.getConfig().getBoolean("settings.restrictions.teleportUnAuthedToSpawn")) {
                    player.teleport(limbo.getLoc());
                }

                sender.getServer().getScheduler().cancelTask(limbo.getTimeoutTaskId());
                LimboCache.getInstance().deleteLimboPlayer(name);
            }

            player.sendMessage(m._("registered"));
            ConsoleLogger.info(player.getDisplayName() + " registered");
        } catch (NoSuchAlgorithmException ex) {
            ConsoleLogger.showError(ex.getMessage());
            sender.sendMessage(m._("error"));
        }
        return true;
    }
}
