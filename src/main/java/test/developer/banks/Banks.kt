package test.developer.banks

import io.papermc.paper.event.player.AsyncChatEvent
import net.kyori.adventure.text.Component
import net.milkbowl.vault.economy.Economy
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.plugin.RegisteredServiceProvider
import org.bukkit.plugin.java.JavaPlugin
import test.developer.banks.commands.BankCommand
import test.developer.banks.managers.BankManager
import test.developer.banks.managers.DatabaseManager
import java.util.*
import kotlin.collections.HashMap

class Banks : JavaPlugin(), Listener {

    companion object {
        var economy: Economy? = null
    }


    lateinit var databaseManager: DatabaseManager
    lateinit var bankManager: BankManager
    val chatListeners: MutableMap<UUID, (Component) -> Unit> = HashMap()

    override fun onEnable() {
        if (!setupEconomy()) {
            logger.severe("Vault plugin not found, disabling plugin.")
            server.pluginManager.disablePlugin(this)
            return
        }

        saveDefaultConfig()

        getCommand("bank")?.setExecutor(BankCommand(this))

        setupDatabase()

        setupBank()

        server.pluginManager.registerEvents(this, this)
    }

    private fun setupBank() {
        bankManager = BankManager(this)
    }

    private fun setupEconomy(): Boolean {
        if (server.pluginManager.getPlugin("Vault") == null) {
            return false
        }
        val rsp: RegisteredServiceProvider<Economy> =
            server.servicesManager.getRegistration(Economy::class.java)
                ?: return false
        economy = rsp.provider
        return true
    }

    private fun setupDatabase() {
        databaseManager = DatabaseManager(this)
    }

    override fun onDisable() {

    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        bankManager.handleInventoryClick(event)
    }

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        bankManager.handleInventoryClose(event)
    }

    @EventHandler
    fun onPlayerChat(event: AsyncChatEvent) {
        val player = event.player
        val listener = chatListeners[player.uniqueId] ?: return
        event.isCancelled = true
        listener(event.message())
    }
}
