package com.casualsmp.tournament;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

public final class CasualTournament extends JavaPlugin {

    private static CasualTournament instance;

    private StorageManager storageManager;
    private ArenaManager arenaManager;
    private TeamManager teamManager;
    private InventoryManager inventoryManager;
    private SpectatorManager spectatorManager;
    private StatisticsManager statisticsManager;
    private BracketManager bracketManager;
    private TournamentManager tournamentManager;
    private TournamentGui tournamentGui;
    private Economy vaultEconomy;

    public static CasualTournament getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        setupVault();

        this.storageManager = new StorageManager(this);
        if (!this.storageManager.initialize()) {
            getLogger().log(Level.SEVERE, "Failed to initialize SQLite storage. Disabling CasualTournament.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.arenaManager = new ArenaManager(this, storageManager);
        this.teamManager = new TeamManager(this);
        this.inventoryManager = new InventoryManager(this, storageManager);
        this.spectatorManager = new SpectatorManager(this);
        this.statisticsManager = new StatisticsManager(this, storageManager);
        this.bracketManager = new BracketManager();
        this.tournamentGui = new TournamentGui(this);
        this.tournamentManager = new TournamentManager(this);

        // Load persistent data safely on startup
        arenaManager.loadArenas();
        tournamentManager.loadLobbyLocation();
        statisticsManager.loadAllStats();
        inventoryManager.loadPendingBackups();
        tournamentManager.recoverIncompleteTournamentState();

        PluginCommand cmd = getCommand("tournament");
        if (cmd != null) {
            TournamentCommand executor = new TournamentCommand(this);
            cmd.setExecutor(executor);
            cmd.setTabCompleter(executor);
        } else {
            getLogger().warning("Command 'tournament' was not found in plugin.yml!");
        }

        getServer().getPluginManager().registerEvents(new MatchListener(this), this);
        tournamentManager.startPeriodicTasks();

        getLogger().info("CasualTournament v" + getPluginMeta().getVersion() + " enabled successfully.");
    }

    @Override
    public void onDisable() {
        if (tournamentManager != null) {
            tournamentManager.shutdownTasksAndSave();
        }
        if (inventoryManager != null) {
            inventoryManager.restoreAllOnlineAndPersistPending();
        }
        if (statisticsManager != null) {
            statisticsManager.flushAllSync();
        }
        if (arenaManager != null) {
            arenaManager.saveAllArenasSync();
        }
        if (storageManager != null) {
            storageManager.close();
        }
        getLogger().info("CasualTournament disabled cleanly.");
    }

    private void setupVault() {
        if (!getConfig().getBoolean("rewards.use-vault", false)) {
            return;
        }
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            getLogger().info("Vault not found; Vault money rewards are disabled.");
            return;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp != null) {
            vaultEconomy = rsp.getProvider();
            getLogger().info("Hooked into Vault economy: " + vaultEconomy.getName());
        } else {
            getLogger().warning("Vault plugin detected, but no Economy provider was registered.");
        }
    }

    public StorageManager getStorageManager() {
        return storageManager;
    }

    public ArenaManager getArenaManager() {
        return arenaManager;
    }

    public TeamManager getTeamManager() {
        return teamManager;
    }

    public InventoryManager getInventoryManager() {
        return inventoryManager;
    }

    public SpectatorManager getSpectatorManager() {
        return spectatorManager;
    }

    public StatisticsManager getStatisticsManager() {
        return statisticsManager;
    }

    public BracketManager getBracketManager() {
        return bracketManager;
    }

    public TournamentManager getTournamentManager() {
        return tournamentManager;
    }

    public TournamentGui getTournamentGui() {
        return tournamentGui;
    }

    public Economy getVaultEconomy() {
        return vaultEconomy;
    }
}
