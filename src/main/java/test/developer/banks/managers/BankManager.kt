package test.developer.banks.managers

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.scheduler.BukkitRunnable
import test.developer.banks.Banks
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.*

class BankManager(private val plugin: Banks) {
    private val databaseManager = plugin.databaseManager

    private val guiName = plugin
        .config
        .getString("messages.gui_name")
        .toString()
    private val depositName = plugin
        .config
        .getString("items.deposit.name")
        .toString()
    private val profileName = plugin
        .config
        .getString("items.profile.name")
        .toString()
    private val withdrawName = plugin
        .config
        .getString("items.withdraw.name")
        .toString()


    init {
        startInterestTask()
    }

    fun createBankAccount(player: Player) {
        val playerUUID = player.uniqueId
        if (databaseManager.getBalance(playerUUID) == null) {
            databaseManager.createAccount(playerUUID)
            player.sendMessage(
                plugin
                    .config
                    .getString("messages.welcome")!!
            )
        } else {
            openBankGui(player)
        }
    }

    fun openBankGui(player: Player) {
        val inventory = Bukkit.createInventory(
            null,
            27,
            Component.text(
                guiName
            )
        )

        val depositItem = ItemStack(Material.GOLD_INGOT)
        val depositMeta: ItemMeta = depositItem.itemMeta!!
        depositMeta.displayName(
            Component.text(
                depositName
            )
        )
        depositItem.itemMeta = depositMeta

        val withdrawItem = ItemStack(Material.IRON_INGOT)
        val withdrawMeta: ItemMeta = withdrawItem.itemMeta!!
        withdrawMeta.displayName(
            Component.text(
                withdrawName
            )
        )
        withdrawItem.itemMeta = withdrawMeta

        val profileItem = ItemStack(Material.BOOK)
        val profileMeta: ItemMeta = profileItem.itemMeta!!
        profileMeta.displayName(
            Component.text(
                profileName
            )
        )
        profileItem.itemMeta = profileMeta

        inventory.setItem(
            plugin.config.getInt("items.deposit.slot"),
            depositItem
        )
        inventory.setItem(
            plugin.config.getInt("items.withdraw.slot"),
            profileItem
        )
        inventory.setItem(
            plugin.config.getInt("items.profile.slot"),
            withdrawItem
        )

        player.openInventory(inventory)
    }

    fun handleInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        if (event.view.title() != Component.text(
                guiName
            )
        ) return

        event.isCancelled = true

        when (event.currentItem?.itemMeta?.displayName()) {
            Component.text(
                depositName
            ) -> {
                player.closeInventory()
                player.sendMessage(
                    plugin
                        .config
                        .getString("messages.deposit_prompt")
                        .toString()
                )
                plugin.chatListeners[player.uniqueId] = { input ->
                    input as TextComponent
                    val amount = input.content().toDoubleOrNull()
                    if (amount != null && amount > 0) {
                        depositMoney(player, amount)
                    } else {
                        player.sendMessage(
                            plugin
                                .config
                                .getString("messages.invalid_amount")
                                .toString()
                        )
                    }
                    plugin.chatListeners.remove(player.uniqueId)
                }
            }

            Component.text(
                withdrawName
            ) -> {
                player.closeInventory()
                player.sendMessage(
                    plugin
                        .config
                        .getString("messages.withdrawal_prompt")
                        .toString()
                )
                plugin.chatListeners[player.uniqueId] = { input ->
                    input as TextComponent
                    val amount = input.content().toDoubleOrNull()
                    if (amount != null && amount > 0) {
                        withdrawMoney(player, amount)
                    } else {
                        player.sendMessage(
                            plugin
                                .config
                                .getString("messages.invalid_amount")
                                .toString()
                        )
                    }
                    plugin.chatListeners.remove(player.uniqueId)
                }
            }

            Component.text(
                profileName
            ) -> {
                val balance = databaseManager.getBalance(player.uniqueId)

                val statement = databaseManager.connection?.prepareStatement(
                    "SELECT balance, created_at FROM bank_accounts WHERE uuid = ?"
                )
                statement?.setString(1, player.uniqueId.toString())

                val resultSet = statement?.executeQuery()
                val isOld = isAccountOlderThenAWeek(resultSet?.getTimestamp("created_at")!!)
                val bankBalance = resultSet.getDouble("balance")

                resultSet.close()
                statement.close()

                val nextInterest = if (isOld) {
                    bankBalance * 0.025
                } else {
                    bankBalance * 0.032
                }

                player.sendMessage(
                    plugin
                        .config
                        .getString("messages.current_balance")
                        .toString()
                        .format(balance)
                            + "\n /" +
                            plugin
                                .config
                                .getString("messages.interest_info")
                                .toString()
                                .format(nextInterest)
                )
            }
        }
    }

    fun handleInventoryClose(event: InventoryCloseEvent) {
        val player = event.player as? Player ?: return

        if (plugin.chatListeners.containsKey(player.uniqueId)) {
            plugin.chatListeners.remove(player.uniqueId)
        }
    }

    fun checkIfPlayerHasBank(player: Player): Boolean {
        val createdAt = databaseManager.getCreatedAt(player.uniqueId)
        if (createdAt == null) {
            player.sendMessage(
                plugin
                    .config
                    .getString("messages.no_bank_message")!!
            )
            return false
        }
        return true
    }

    private fun depositMoney(player: Player, amount: Double) {
        val economy = Banks.economy ?: return
        if (!checkIfPlayerHasBank(player)) {
            return
        }

        if (economy.withdrawPlayer(player, amount).transactionSuccess()) {
            val playerUUID = player.uniqueId
            val newBalance = databaseManager.getBalance(playerUUID)!! + amount
            databaseManager.setBalance(playerUUID, newBalance)
            player.sendMessage(
                plugin
                    .config
                    .getString("messages.deposit_success")!!
                    .format(amount)
            )
        } else {
            player.sendMessage(
                plugin
                    .config
                    .getString("messages.insufficient_funds")!!
            )
        }
    }

    private fun withdrawMoney(player: Player, amount: Double) {
        if (!checkIfPlayerHasBank(player)) {
            return
        }

        val playerUUID = player.uniqueId
        val currentBalance = databaseManager.getBalance(playerUUID)!!
        if (currentBalance >= amount) {
            val newBalance = currentBalance - amount
            databaseManager.setBalance(playerUUID, newBalance)
            Banks.economy?.depositPlayer(player, amount)
            player.sendMessage(
                plugin
                    .config
                    .getString("messages.withdrawal_success")
                !!.format(amount)
            )
        } else {
            player.sendMessage(plugin.config.getString("messages.insufficient_funds")!!)
        }
    }

    private fun startInterestTask() {
        object : BukkitRunnable() {
            override fun run() {
                val statement = databaseManager.connection?.createStatement()
                val resultSet =
                    statement
                        ?.executeQuery(
                            "SELECT uuid, balance, created_at FROM bank_accounts"
                        )

                while (resultSet?.next() == true) {
                    startInterestForPLayer(resultSet)
                }
                resultSet?.close()
                statement?.close()
            }
        }.runTaskTimer(plugin, 0, 1728000L)
    }

    private fun startInterestForPLayer(resultSet: ResultSet) {
        val uuid = UUID.fromString(resultSet.getString("uuid"))
        val balance = resultSet.getDouble("balance")
        val createdAt = resultSet.getTimestamp("created_at")

        val accountOlderThenAWeek = isAccountOlderThenAWeek(createdAt)
        val interest = if (accountOlderThenAWeek) {
            balance * 0.025
        } else {
            balance * 0.032
        }
        val newBalance = balance + interest

        databaseManager.setBalance(uuid, newBalance)
        val player = plugin.server.getPlayer(uuid)
        player?.sendMessage(
            plugin
                .config
                .getString("messages.daily_interest")
            !!.format(interest),
        )
    }

    private fun isAccountOlderThenAWeek(createdAt: Timestamp): Boolean {
        return createdAt.before(
            Timestamp.from(
                Instant.ofEpochSecond(
                    LocalDateTime.now().minusWeeks(1).toEpochSecond(ZoneOffset.UTC)
                )
            )
        )
    }

}