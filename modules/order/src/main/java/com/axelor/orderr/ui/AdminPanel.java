package com.axelor.orderr.ui;

import com.axelor.auth.db.User;
import com.axelor.order.db.*;
import com.axelor.orderr.service.complaints.ComplaintsService;
import com.axelor.orderr.service.dish.DishService;
import com.axelor.orderr.service.menu.MenuService;
import com.axelor.orderr.service.order.OrderService;
import com.axelor.orderr.service.rating.DishRatingService;
import com.axelor.orderr.service.user.UserService;
import com.google.inject.Inject;
import com.pengrad.telegrambot.model.CallbackQuery;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import com.pengrad.telegrambot.request.SendMessage;

import java.util.*;

public class AdminPanel {
    private final TgBotService botService;
    private final DishService dishService;
    private final MenuService menuService;
    private final UserService userService;
    private final OrderService orderService;
    private final ComplaintsService complaintsService;

    public final Map<Long, Integer> awaitingPassword = new HashMap<>();
    private final Map<Long, Dish> pendingDish = new HashMap<>();


    @Inject
    public AdminPanel(TgBotService botService, DishService dishService, MenuService menuService, UserService userService, OrderService orderService, ComplaintsService complaintsService) {
        this.botService = botService;
        this.dishService = dishService;
        this.menuService = menuService;
        this.userService = userService;
        this.orderService = orderService;
        this.complaintsService = complaintsService;
    }

    // Авторизация админа
    public void handleCallback(Update update) {
        if (update.callbackQuery() == null) return;
        CallbackQuery callback = update.callbackQuery();
        String data = callback.data();
        long chatId = callback.maybeInaccessibleMessage().chat().id();

        if ("admin".equals(data)) {
            botService.safeDelete(String.valueOf(chatId), callback.maybeInaccessibleMessage().messageId());
            InlineKeyboardButton btnCancel = new InlineKeyboardButton("⬅️ Отмена").callbackData("back_start");
            InlineKeyboardMarkup markup = new InlineKeyboardMarkup(btnCancel);

            SendMessage sendMessage = new SendMessage(String.valueOf(chatId), "Введите пароль:").replyMarkup(markup);
            Message message = botService.getBot().execute(sendMessage).message();
            awaitingPassword.put(chatId, message.messageId());
        }
    }


    public void checkPassword(Message message) {
        long chatId = message.chat().id();
        botService.safeDelete(String.valueOf(chatId), message.messageId());

        if (awaitingPassword.containsKey(chatId)) {
            botService.safeDelete(String.valueOf(chatId), awaitingPassword.get(chatId));
        }

        String inputPassword = message.text();
        if (inputPassword.equals(Config.ADMIN_PASSWORD)) {
            awaitingPassword.remove(chatId);
            adminMenu(chatId);
        } else {
            botService.sendMessage(String.valueOf(chatId), "Неверный пароль, попробуйте снова:");
        }
    }

    public boolean isAwaitingPassword(long chatId) {
        return awaitingPassword.containsKey(chatId);
    }

    public void adminMenu(long chatId) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        InlineKeyboardButton dishes = new InlineKeyboardButton("\uD83E\uDD59 Блюда").callbackData("dishes");
        InlineKeyboardButton createMenu = new InlineKeyboardButton("\uD83D\uDCC4 Сделать меню").callbackData("create_menu");
        InlineKeyboardButton tomorrow_orders = new InlineKeyboardButton("\uD83D\uDCDC Заказы на завтра").callbackData("tomorrow_orders");
        InlineKeyboardButton complaints = new InlineKeyboardButton("\uD83D\uDCDC Жалобы/предложения").callbackData("show_complaints");
        InlineKeyboardButton back = new InlineKeyboardButton("⬅️ Назад").callbackData("back_role_choose");
        markup.addRow(dishes, createMenu);
        markup.addRow(tomorrow_orders, complaints);
        markup.addRow(back);
        botService.sendMessage(String.valueOf(chatId), "Добро пожаловать", markup);
    }

    public void adminMenuNav(Update update) {
        if (update.callbackQuery() == null) return;
        CallbackQuery callback = update.callbackQuery();
        String data = callback.data();
        long chatId = callback.maybeInaccessibleMessage().chat().id();
        int messageId = callback.maybeInaccessibleMessage().messageId();
        botService.safeDelete(String.valueOf(chatId), messageId);

        if ("dishes".equals(data)) {
            showDishes(chatId);
        } else if ("create_menu".equals(data)) {
            createTomorrowMenu(chatId, messageId);
        } else if ("tomorrow_orders".equals(data)) {
            showTomorrowOrders(chatId);
        } else if ("show_complaints".equals(data)) {
            showComplaintsList(chatId);
        } else if ("back_role_choose".equals(data)) {
            CommandHandler.roleChoose(String.valueOf(chatId), botService);
        }
    }

    // раздел "Блюда"
    public void showDishes(long chatId) {
        List<Dish> dishList = dishService.getAllDishes();
        StringBuilder dishDesc = new StringBuilder();
        if (dishList.isEmpty()) {
            dishDesc.append("❎ Список блюд пуст");
        } else {
            dishDesc.append("\uD83D\uDCCB Общее меню: \n");
            for (Dish dish : dishList) {
                dishDesc.append(dish.getName())
                        .append("\n");
            }
        }

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        InlineKeyboardButton btnCreateDish = new InlineKeyboardButton("➕ Добавить").callbackData("create_dish");
        InlineKeyboardButton btnDeleteDish = new InlineKeyboardButton("➖ Удалить").callbackData("delete_dish");
        InlineKeyboardButton btnBack = new InlineKeyboardButton("⬅️ Назад").callbackData("back_admin_menu");
        markup.addRow(btnCreateDish, btnDeleteDish);
        markup.addRow(btnBack);

        botService.sendMessage(String.valueOf(chatId), String.valueOf(dishDesc), markup);
    }

    public void showDishNav(Update update) {
        if (update.callbackQuery() == null) return;
        CallbackQuery callback = update.callbackQuery();
        String data = callback.data();
        long chatId = callback.maybeInaccessibleMessage().chat().id();
        botService.safeDelete(String.valueOf(chatId), callback.maybeInaccessibleMessage().messageId());

        if ("create_dish".equals(data)) {
            botService.sendMessage(String.valueOf(chatId), "Введите название блюда");
            pendingDish.put(chatId, new Dish());
        } else if ("delete_dish".equals(data)) {
            showDeletionMenu(chatId);
        } else if ("back_admin_menu".equals(data)) {
            adminMenu(chatId);
        }
    }

    // Создание блюда
    public boolean isDishCreating(long chatId) {
        return pendingDish.containsKey(chatId);
    }

    public void dishInfoInsert(long chatId, Message message) {
        Dish dish = pendingDish.get(chatId);
        if (pendingDish.containsKey(chatId)) {
            if (dish.getName() == null) {
                dish.setName(message.text());
                dishService.createDish(dish);
                botService.sendMessage(String.valueOf(chatId), "Добавлено " + dish.getName());
                pendingDish.remove(chatId);
                showDishes(chatId);
            }
        }
    }

    // удаление блюда
    public void showDeletionMenu(long chatId) {
        List<Dish> dishesData = dishService.getAllDishes();
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        if (dishesData != null && !dishesData.isEmpty()) {
            for (Dish dish : dishesData) {
                InlineKeyboardButton btn_remove = new
                        InlineKeyboardButton(dish.getName())
                        .callbackData("remove_dish:" + dish.getId());
                markup.addRow(btn_remove);
            }

            InlineKeyboardButton btn_back = new InlineKeyboardButton("⬅️ Готово/Отмена").callbackData("back_show_dishes");
            markup.addRow(btn_back);

            botService.sendMessage(String.valueOf(chatId), "Выберите блюда для удаления:", markup);
        } else {
            botService.sendMessage(String.valueOf(chatId), "Блюдо не удалено");
        }
    }

    public void deletionMenuNav(Update update) {
        if (update.callbackQuery() == null) return;
        CallbackQuery callback = update.callbackQuery();
        String data = callback.data();
        long chatId = callback.maybeInaccessibleMessage().chat().id();

        if (data.startsWith("remove_dish:")) {
            deleteDish(chatId, callback);
        } else if ("back_show_dishes".equals(data)) {
            botService.safeDelete(String.valueOf(chatId), callback.maybeInaccessibleMessage().messageId());
            showDishes(chatId);
        }
    }

    public void deleteDish(long chatId, CallbackQuery callback) {
        long dishId = Long.parseLong(callback.data().split(":")[1]);
        dishService.deleteDish(dishId);
        botService.sendMessage(String.valueOf(chatId), "Блюдо удалено");
    }


    // Раздел "Меню на завтра"
    public void createTomorrowMenu(long chatId, Integer messageId) {
        Menu menuData = menuService.getTomorrowMenu();
        String text;

        if (menuData != null && !menuData.getDishes().isEmpty()) {
            StringBuilder dishList = new StringBuilder("📅 Меню на завтра:\n\n");
            for (Dish dish : menuData.getDishes()) {
                dishList.append(dish.getName())
                        .append("\n");
            }
            text = dishList.toString();
        } else {
            text = "❎ Меню на завтра пусто";
        }

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        InlineKeyboardButton btnAddDish = new InlineKeyboardButton("➕ Добавить").callbackData("add_dish");
        InlineKeyboardButton btnRemoveDish = new InlineKeyboardButton("➖ Удалить").callbackData("remove_dish");
        InlineKeyboardButton btnClearMenu = new InlineKeyboardButton("\uD83E\uDDF9 Очистить меню").callbackData("clear");
        InlineKeyboardButton menu_ready = new InlineKeyboardButton("\uD83D\uDD0A Меню готово").callbackData("menu_ready");
        InlineKeyboardButton btnBack = new InlineKeyboardButton("⬅️ Назад").callbackData("back_admin_menu");
        markup.addRow(btnAddDish, btnRemoveDish);
        markup.addRow(btnClearMenu, menu_ready);
        markup.addRow(btnBack);

        botService.sendMessage(String.valueOf(chatId), text, markup);
    }

    public void tomorrowMenuNav(Update update) {
        if (update.callbackQuery() == null) return;
        CallbackQuery callback = update.callbackQuery();
        String data = callback.data();
        long chatId = callback.maybeInaccessibleMessage().chat().id();
        botService.safeDelete(String.valueOf(chatId), callback.maybeInaccessibleMessage().messageId());

        if ("add_dish".equals(data)) {
            addDishToTomorrowMenu(chatId, update);
        } else if ("remove_dish".equals(data)) {
            removeDishFromTomorrowMenu(chatId, update);
        } else if ("clear".equals(data)) {
            clearTomorrowMenu(chatId, callback.maybeInaccessibleMessage().messageId());
        } else if ("menu_ready".equals(data)) {
            menuIsReady(chatId, callback.maybeInaccessibleMessage().messageId());
        } else if ("back_admin_menu".equals(data)) {
            adminMenu(chatId);
        }
    }

    // Добавление блюда в завтрашнее меню
    public void addDishToTomorrowMenu(long chatId, Update update) {
        List<Dish> dishesData = dishService.getAllDishes();
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        if (dishesData != null && !dishesData.isEmpty()) {
            for (Dish dish : dishesData) {
                InlineKeyboardButton btn_remove = new
                        InlineKeyboardButton(dish.getName())
                        .callbackData("add_tm_dish:" + dish.getId());
                markup.addRow(btn_remove);
            }

            InlineKeyboardButton btn_back = new InlineKeyboardButton("⬅️ Готово/Отмена").callbackData("back_tomorrow_menu");
            markup.addRow(btn_back);

            botService.sendMessage(String.valueOf(chatId), "Выберите блюда завтрашнего меню:", markup);
        } else {
            botService.sendMessage(String.valueOf(chatId), "Сначала добавьте блюда в разделе \n \"\uD83E\uDD59 Блюда\"");
            createTomorrowMenu(chatId, update.callbackQuery().maybeInaccessibleMessage().messageId());
        }
    }

    public void addingMenuNav(Update update) {
        if (update.callbackQuery() == null) return;
        CallbackQuery callback = update.callbackQuery();
        String data = callback.data();
        long chatId = callback.maybeInaccessibleMessage().chat().id();

        if (data.startsWith("add_tm_dish:")) {
            addingDishToTomorrowMenu(chatId, callback);
        } else if ("back_tomorrow_menu".equals(data)) {
            botService.safeDelete(String.valueOf(chatId), callback.maybeInaccessibleMessage().messageId());
            createTomorrowMenu(chatId, callback.maybeInaccessibleMessage().messageId());
        }
    }

    public void addingDishToTomorrowMenu(long chatId, CallbackQuery callback) {
        long dishId = Long.parseLong(callback.data().split(":")[1]);
        menuService.addDishToTomorrowMenu(dishId);
        botService.sendMessage(String.valueOf(chatId), "Блюдо добавлено");
    }

    // Удаление блюда из завтрашнего меню
    public void removeDishFromTomorrowMenu(long chatId, Update update) {
        Menu tomorrowMenu = menuService.getTomorrowMenu();
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        if (tomorrowMenu != null && !tomorrowMenu.getDishes().isEmpty()) {
            for (Dish dish : tomorrowMenu.getDishes()) {
                InlineKeyboardButton btn_remove = new
                        InlineKeyboardButton(dish.getName())
                        .callbackData("remove_tm_dish:" + dish.getId());
                markup.addRow(btn_remove);
            }

            InlineKeyboardButton btn_back = new InlineKeyboardButton("⬅️ Готово/Отмена").callbackData("back_tomorrow_menu");
            markup.addRow(btn_back);

            botService.sendMessage(String.valueOf(chatId), "\uD83D\uDCC4 Удалите блюда из завтрашнего меню:", markup);
        } else {
            botService.sendMessage(String.valueOf(chatId), "Меню на завтра ещё пусто");
            createTomorrowMenu(chatId, update.callbackQuery().maybeInaccessibleMessage().messageId());
        }
    }

    public void removingMenuNav(Update update) {
        if (update.callbackQuery() == null) return;
        CallbackQuery callback = update.callbackQuery();
        String data = callback.data();
        long chatId = callback.maybeInaccessibleMessage().chat().id();

        if (data.startsWith("remove_tm_dish:")) {
            removeDishFromTomorrowMenu(chatId, callback);
        } else if ("back_tomorrow_menu".equals(data)) {
            botService.safeDelete(String.valueOf(chatId), callback.maybeInaccessibleMessage().messageId());
            createTomorrowMenu(chatId, callback.maybeInaccessibleMessage().messageId());
        }
    }

    public void removeDishFromTomorrowMenu(long chatId, CallbackQuery callback) {
        long dishId = Long.parseLong(callback.data().split(":")[1]);
        menuService.removeDishFromTomorrowMenu(dishId);
        botService.sendMessage(String.valueOf(chatId), "Блюдо удалено");
    }

    // Меню готово
    public void menuIsReady(long chatId, int messageId) {
        List<User> users = userService.getUsersList();
        for (User user : users) {
            if (user.getTg_id() == null) {
                System.out.println("user " + user.getName() + " don't have an id \n");
                continue;
            }
            botService.sendMessage(user.getTg_id(), "✅ Меню готово!");
        }
        orderService.clearAllOrders();
        createTomorrowMenu(chatId, messageId);
    }

    // Очистка меню
    public void clearTomorrowMenu(long chatId, Integer messageId) {
        menuService.clearTomorrowMenu();
        botService.sendMessage(String.valueOf(chatId), "♻️ Меню очищено");
        createTomorrowMenu(chatId, messageId);
    }

    // Заказы на завтра
    public void showTomorrowOrders(long chatId) {
        List<Orderr> ordersList = orderService.getOrderList();
        StringBuilder order = new StringBuilder();
        if (ordersList.isEmpty()) {
            order.append("\uD83D\uDE45 Заказов еще нет");
        } else {
            order.append("\uD83D\uDCDC Заказы на завтра: \n");
            for (Orderr orderr : ordersList) {
                order.append("\n")
                        .append(orderr.getDish().getName())
                        .append(" ")
                        .append(orderr.getPortion_size())
                        .append(" - ")
                        .append(orderr.getUser().getName());
            }
        }
        InlineKeyboardButton bnt_back = new InlineKeyboardButton("⬅️ Назад").callbackData("back_admin_menu");
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup(bnt_back);
        botService.sendMessage(String.valueOf(chatId), order.toString(), markup);
    }

    public void showComplaintsList(long chatId) {
        List<Complaints> compList = complaintsService.getCompList();
        StringBuilder text = new StringBuilder();

        if (!compList.isEmpty()) {
            text.append("Список жалоб/предложений \n");
            for (Complaints compl : compList) {
                text.append("\n")
                        .append(compl.getText());
            }
        } else {
            text.append("Жалоб/предложений еще нет");
        }

        InlineKeyboardButton back = new InlineKeyboardButton("Назад").callbackData("back_admin_menu");
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup().addRow(back);

        botService.sendMessage(String.valueOf(chatId), text.toString(), markup);
    }
}
