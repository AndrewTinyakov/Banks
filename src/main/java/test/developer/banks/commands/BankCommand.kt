package test.developer.banks.commands

import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import test.developer.banks.Banks

class BankCommand(
    private val plugin: Banks
) : CommandExecutor {

    override fun onCommand(sender: CommandSender, command: Command, p2: String, args: Array<out String>?): Boolean {
        if (sender is Player) {
            when (args?.size) {
                0 -> {
                    val hasAccount = plugin.bankManager.checkIfPlayerHasBank(sender)
                    if (!hasAccount) {
                        return true
                    }

                    plugin.bankManager.openBankGui(sender)
                }

                1 -> when (args[0].lowercase()) {
                    "create" -> {
                        plugin.bankManager.createBankAccount(sender)
                    }

                    "help" -> sender.sendMessage(
                        plugin
                            .config
                            .getString(
                                "messages.bank_help_command"
                            )!!
                    )

                    "give" -> {
                        Banks.economy!!.depositPlayer(sender, 1000.0)
                    }

                    else -> sender.sendMessage(
                        plugin
                            .config
                            .getString(
                                "messages.invalid_command_message"
                            )!!
                    )
                }

                else -> sender.sendMessage(
                    plugin
                        .config
                        .getString(
                            "messages.invalid_command_message"
                        )!!
                )
            }
            return true
        }
        return false
    }

}