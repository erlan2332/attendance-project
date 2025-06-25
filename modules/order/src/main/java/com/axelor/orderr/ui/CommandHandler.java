package com.axelor.orderr.ui;

import com.google.inject.Inject;
import com.pengrad.telegrambot.model.CallbackQuery;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import com.pengrad.telegrambot.request.SendMessage;

public class CommandHandler {
    private final TgBotService botService;
    private final AdminPanel adminPanel;
    private final EmployeePanel employeePanel;

    @Inject
    public CommandHandler(TgBotService botService, AdminPanel adminPanel, EmployeePanel employeePanel) {
        this.botService = botService;
        this.adminPanel = adminPanel;
        this.employeePanel = employeePanel;
    }

    public void processUpdate(Update update) {
        if (update.callbackQuery() != null) {
            CallbackQuery callback = update.callbackQuery();
            String data = callback.data();
            long chatId = callback.maybeInaccessibleMessage().chat().id();

            // админ панель
            if ("admin".equals(data)) {
                adminPanel.handleCallback(update);
                return;
            } 
            else if ("back_start".equals(data)) {
                roleChoose(String.valueOf(chatId), botService);
                adminPanel.awaitingPassword.remove(chatId);
                return;
            }
            else if ("dishes".equals(data)
                    || "create_menu".equals(data)
                    || "tomorrow_orders".equals(data)
                    || "show_complaints".equals(data)
                    || "month_report".equals(data)
                    || "back_role_choose".equals(data)) {
                adminPanel.adminMenuNav(update);
                return;
            }
            else if ("create_dish".equals(data) || "delete_dish".equals(data) || "back_admin_menu".equals(data)) {
                adminPanel.showDishNav(update);
            }
            else if (data.startsWith("remove_dish:") || "back_show_dishes".equals(data)) {
                adminPanel.deletionMenuNav(update);
            }
            else if ("add_dish".equals(data)
                    || "remove_dish".equals(data)
                    || "clear".equals(data)
                    || "menu_ready".equals(data)
                    || "back_admin_menu".equals(data)) {
                adminPanel.tomorrowMenuNav(update);
            }
            else if (data.startsWith("add_tm_dish:") || "back_tomorrow_menu".equals(data)) {
                adminPanel.addingMenuNav(update);
            }
            else if (data.startsWith("remove_tm_dish:") || "back_tomorrow_menu".equals(data)) {
                adminPanel.removingMenuNav(update);
            } else if ("back_admin_menu".equals(data)) {
                adminPanel.adminMenu(chatId);
            }

            // сотрудник
            if ("employee".equals(data)) {
                employeePanel.usernameInput(update);
                return;
            }
            else if ("back_role_choose".equals(data)) {
                roleChoose(String.valueOf(chatId), botService);
            } else if (data.startsWith("select_dish:")) {
                employeePanel.dishSelection(update);
                return;
            } else if (data.startsWith("select_portion:")) {
                employeePanel.portionSelection(update);
                return;
            } else if ("my_orders".equals(data)
                    || "make_order".equals(data)
                    || "write_compl".equals(data)
                    || "back_role_choose".equals(data)) {
                employeePanel.employeePanelNav(update);
            } else if ("back_employee_panel".equals(data)) {
                employeePanel.backToEmployeePanel(update);
            } else if (data.startsWith("rateDish:")) {
                employeePanel.dishRating(update);
            }
        }

        Message message = update.message();
        if (message == null || message.text() == null) {
            return;
        }

        String text = message.text().trim();
        long chatId = message.chat().id();


        if (adminPanel.isAwaitingPassword(chatId)) {
            adminPanel.checkPassword(message);
            return;
        }

        if (message.text() != null && adminPanel.isDishCreating(chatId)) {
            adminPanel.dishInfoInsert(chatId, message);
            return;
        }

        if (message.text() != null && employeePanel.isComplWriting(chatId)) {
            if (message.text().length() > 100 || message.text().length() < 10) {
                botService.sendMessage(String.valueOf(chatId), "Сообщение должно быть в диапозоне 10 - 100 символов");
            } else {
                employeePanel.complaintSaving(chatId, message);
                return;
            }
        }

        if ("/start".equalsIgnoreCase(text)) {
            roleChoose(String.valueOf(chatId), botService);
            return;
        } else if ("/help".equalsIgnoreCase(text)) {
            botService.sendMessage(String.valueOf(chatId), "Список доступных команд: /start, /help");
        }

        if (update.message() != null) {
            employeePanel.userRegistration(update);
            return;
        }
    }


    public static void roleChoose(String chatId, TgBotService botService) {
        InlineKeyboardButton buttonAdmin = new InlineKeyboardButton("Админ")
                .callbackData("admin");
        InlineKeyboardButton buttonEmployee = new InlineKeyboardButton("Сотрудник")
                .callbackData("employee");
        InlineKeyboardMarkup inlineKeyboard = new InlineKeyboardMarkup(
                new InlineKeyboardButton[]{ buttonAdmin, buttonEmployee }
        );
        SendMessage sendMessage = new SendMessage(chatId, "Кто вы:").replyMarkup(inlineKeyboard);
        botService.getBot().execute(sendMessage);
    }
}
