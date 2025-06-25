package com.axelor.orderr.ui;

import com.axelor.auth.db.User;
import com.axelor.order.db.Complaints;
import com.axelor.order.db.Dish;
import com.axelor.order.db.Menu;
import com.axelor.order.db.Orderr;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class EmployeePanel {
    private final TgBotService botService;
    private final UserService userService;
    private final DishService dishService;
    private final MenuService menuService;
    private final OrderService orderService;
    private final ComplaintsService complaintsService;
    private final DishRatingService dishRatingService;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    private final Map<Long, Long> pendingRegistration = new HashMap<>();
    private final Map<Long, Integer> registrationMessage = new HashMap<>();
    private final Map<Long, Orderr> pendingOrders = new HashMap<>();
    private final Map<Long, Integer> dishSelectionMessages = new HashMap<>(); // Сообщения с выбором блюда
    private final Map<Long, Integer> portionSelectionMessages = new HashMap<>();
    private final Map<Long, Complaints> pendingCompl = new HashMap<>();

    @Inject
    public EmployeePanel(TgBotService botService, UserService userService, DishService dishService, MenuService menuService, OrderService orderService, ComplaintsService complaintsService, DishRatingService dishRatingService) {
        this.botService = botService;
        this.userService = userService;
        this.dishService = dishService;
        this.menuService = menuService;
        this.orderService = orderService;
        this.complaintsService = complaintsService;
        this.dishRatingService = dishRatingService;
    }


    // Регистрация сотрудника
    public void usernameInput(Update update) {
        if (update.callbackQuery() == null) return;
        CallbackQuery callback = update.callbackQuery();
        long chatId = callback.maybeInaccessibleMessage().chat().id();
        long telegramId = callback.from().id();
        botService.safeDelete(String.valueOf(chatId), callback.maybeInaccessibleMessage().messageId());

        User user = userService.getUserByTelegramId(telegramId);
        if (user != null) {
            botService.sendMessage(String.valueOf(chatId), "Добро пожаловать ");
            Orderr order = new Orderr();
            order.setUser(user);
            pendingOrders.put(chatId, order);

            employeePanel(chatId, user);
            return;
        }
        InlineKeyboardButton btn_back = new InlineKeyboardButton("Отмена").callbackData("back_role_choose");
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup(btn_back);

        SendMessage sendMessage = new SendMessage(String.valueOf(chatId), "Введите свое имя").replyMarkup(markup);
        Message message = botService.getBot().execute(sendMessage).message();
        pendingRegistration.put(chatId, telegramId);
        registrationMessage.put(chatId, message.messageId());

    }

    public void userRegistration(Update update) {
        if (update.message() == null) return;
        long chatId = update.message().chat().id();
        String name = update.message().text();

        if (pendingRegistration.containsKey(chatId)) {
            long telegramId = pendingRegistration.get(chatId);
            userService.registerUser(telegramId, name);
            pendingRegistration.remove(chatId);

            if (registrationMessage.containsKey(chatId)) {
                botService.safeDelete(String.valueOf(chatId), registrationMessage.get(chatId));
                registrationMessage.remove(chatId);
            }

            botService.sendMessage(String.valueOf(chatId), "✅ Вы зарегистрировались");
            botService.safeDelete(String.valueOf(chatId), update.message().messageId());

            User user = userService.getUserByTelegramId(telegramId);
            Orderr order = new Orderr();
            order.setUser(user);
            pendingOrders.put(chatId, order);

            employeePanel(chatId, user);
        }
    }

    // панель сотрудника
    public void employeePanel(long chatId, User user) {
        Orderr order = new Orderr();
        order.setUser(user);
        pendingOrders.put(chatId, order);

        InlineKeyboardButton btn_my_orders = new InlineKeyboardButton("\uD83D\uDCDC Мои заказы").callbackData("my_orders");
        InlineKeyboardButton btn_make_order = new InlineKeyboardButton("\uD83E\uDD61 Сделать заказ").callbackData("make_order");
        InlineKeyboardButton btn_write_compl = new InlineKeyboardButton("\uD83D\uDCDD Жалобы/предложения").callbackData("write_compl");
        InlineKeyboardButton btn_back_role_choose = new InlineKeyboardButton("⬅️ Назад").callbackData("back_role_choose");
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.addRow(btn_my_orders, btn_make_order);
        markup.addRow(btn_write_compl, btn_back_role_choose);
        botService.sendMessage(String.valueOf(chatId), "Добро пожаловать", markup);
    }

    public void employeePanelNav(Update update) {
        if (update.callbackQuery() == null) return;
        CallbackQuery callback = update.callbackQuery();
        String data = callback.data();
        long chatId = callback.maybeInaccessibleMessage().chat().id();
        int messageId = callback.maybeInaccessibleMessage().messageId();
        botService.safeDelete(String.valueOf(chatId), messageId);

        if ("my_orders".equals(data)) {
            myOrdersList(chatId);
        } else if ("make_order".equals(data)) {
            showDishList(chatId);
        } else if ("write_compl".equals(data)) {
            botService.sendMessage(String.valueOf(chatId), "Что бы вы хотели изменить/внести в меню или сервис?");
            pendingCompl.put(chatId, new Complaints());
        } else if ("back_role_choose".equals(data)) {
            CommandHandler.roleChoose(String.valueOf(chatId), botService);
        }
    }

    // Мои заказы
    public void myOrdersList(long chatId) {
        User user = userService.getUserByTelegramId(chatId);
        List<Orderr> ordersById = orderService.getOrderById(user);
        StringBuilder text = new StringBuilder();

        if (ordersById.isEmpty()) {
            text.append("\uD83D\uDE45 У вас еще нет заказов");
        }

        text.append("\uD83D\uDDD3 Ваши заказы:\n");
        for (Orderr orderr : ordersById) {
            text.append("\n")
                    .append(orderr.getDish().getName())
                    .append(" - ")
                    .append(orderr.getPortion_size())
                    .append(" порция");
        }

        InlineKeyboardButton btn_back_employee_panel = new InlineKeyboardButton("⬅️ Назад").callbackData("back_employee_panel");
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup(btn_back_employee_panel);

        botService.sendMessage(String.valueOf(chatId), text.toString(), markup);
    }

    // возвращение в панель меню сотрудника
    public void backToEmployeePanel(Update update) {
        if (update.callbackQuery() == null) return;
        CallbackQuery callback = update.callbackQuery();
        String data = callback.data();
        long chatId = callback.maybeInaccessibleMessage().chat().id();
        botService.safeDelete(String.valueOf(chatId), callback.maybeInaccessibleMessage().messageId());
        User user = userService.getUserByTelegramId(chatId);
        if ("back_employee_panel".equals(data)) {
            employeePanel(chatId, user);
        }
    }

    // Сделать заказ
    public void showDishList(long chatId) {
        Menu menu = menuService.getTomorrowMenu();
        List<Dish> dishes = menu.getDishes();
        StringBuilder text = new StringBuilder();

        if (menu == null || menu.getDishes() == null || menu.getDishes().isEmpty()) {
            text.append("\uD83D\uDE45 Меню на завтра пустое \n");
        } else {
            text.append("\uD83C\uDF7D Выберите блюдо: ");
        }

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        for (Dish dish : dishes) {
            InlineKeyboardButton button = new InlineKeyboardButton(dish.getName())
                    .callbackData("select_dish:" + dish.getId());
            markup.addRow(button);
        }
        InlineKeyboardButton bnt_back_employee_panel = new InlineKeyboardButton("⬅️Назад").callbackData("back_employee_panel");
        markup.addRow(bnt_back_employee_panel);

        SendMessage sendMessage = new SendMessage(String.valueOf(chatId), text.toString()).replyMarkup(markup);
        Message message = botService.getBot().execute(sendMessage).message();
        dishSelectionMessages.put(chatId, message.messageId());
    }

    public void dishSelection(Update update) {
        if (update.callbackQuery() == null) return;
        CallbackQuery callback = update.callbackQuery();
        long chatId = callback.maybeInaccessibleMessage().chat().id();
        long dishId = Long.parseLong(callback.data().split(":")[1]);

        Dish dish = dishService.getDishById(dishId);
        if (dish == null) {
            botService.sendMessage(String.valueOf(chatId), "\uD83D\uDE45Блюдо не найдено.");
            return;
        }
        if (!pendingOrders.containsKey(chatId)) {
            botService.sendMessage(String.valueOf(chatId), "\uD83D\uDE45 Заказ не сделан. Попробуйте начать заказ снова. \uD83D\uDC49\uD83C\uDFFC /start");
            return;
        }

        pendingOrders.get(chatId).setDish(dish);
        if (dishSelectionMessages.containsKey(chatId)) {
            botService.safeDelete(String.valueOf(chatId), dishSelectionMessages.get(chatId));
            dishSelectionMessages.remove(chatId);
        }
        showPortionSize(chatId);
    }

    public void showPortionSize(long chatId) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup(
                new InlineKeyboardButton("Маленькая").callbackData("select_portion:маленькая"),
                new InlineKeyboardButton("Большая").callbackData("select_portion:большая")
        );
        SendMessage sendMessage = new SendMessage(String.valueOf(chatId), "Выберите размер порции:").replyMarkup(markup);
        Message message = botService.getBot().execute(sendMessage).message();
        portionSelectionMessages.put(chatId, message.messageId());
    }

    public void portionSelection(Update update) {
        if (update.callbackQuery() == null) return;
        CallbackQuery callback = update.callbackQuery();
        long chatId = callback.maybeInaccessibleMessage().chat().id();
        String portionSize = callback.data().split(":")[1];
        User user = userService.getUserByTelegramId(chatId);

        // Проверяем, что заказ есть
        if (!pendingOrders.containsKey(chatId)) {
            botService.sendMessage(String.valueOf(chatId), "\uD83D\uDE45 Заказ не сделан. Попробуйте начать заказ заново. \uD83D\uDC49\uD83C\uDFFC /start");
            return;
        }
        Orderr order = pendingOrders.get(chatId);
        order.setPortion_size(portionSize);

        long dishId = order.getDish().getId();
        Orderr persistedOrder = orderService.makeOrder(order.getUser(), order.getDish(), order.getPortion_size());

        pendingOrders.remove(chatId);
        if (portionSelectionMessages.containsKey(chatId)) {
            botService.safeDelete(String.valueOf(chatId), portionSelectionMessages.get(chatId));
            portionSelectionMessages.remove(chatId);
        }

        employeePanel(chatId, user);

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup(
                new InlineKeyboardButton("⭐️").callbackData("rateDish:" + dishId + ":1"),
                new InlineKeyboardButton("⭐️").callbackData("rateDish:" + dishId + ":2"),
                new InlineKeyboardButton("⭐️").callbackData("rateDish:" + dishId + ":3"),
                new InlineKeyboardButton("⭐️").callbackData("rateDish:" + dishId + ":4"),
                new InlineKeyboardButton("⭐️").callbackData("rateDish:" + dishId + ":5")
        );

        botService.sendMessage(String.valueOf(chatId), "✅ Ваш заказ сохранён!\n" +
                persistedOrder.getUser().getName() + "\n" +
                persistedOrder.getDish().getName() + " порция " +
                persistedOrder.getPortion_size() +
                "\n\nНе забудьте оценить блюдо, когда опробуете", markup);
    }

        // оценка блюд
    public void dishRating(Update update) {
        if (update.callbackQuery() == null) return;
        CallbackQuery callback = update.callbackQuery();
        long chatId = callback.maybeInaccessibleMessage().chat().id();
        int messageId = callback.maybeInaccessibleMessage().messageId();
        botService.safeDelete(String.valueOf(chatId), messageId);

        String[] parts = callback.data().split(":");
        long dishId = Long.parseLong(parts[1]);
        Integer rating = Integer.valueOf(parts[2]);

        dishRatingService.setDishRating(dishId, rating);
    }

    // Жалобы/предложения
    public boolean isComplWriting(long chatId) {
        return pendingCompl.containsKey(chatId);
    }

    public void complaintSaving(long chatId, Message message) {
        Complaints complaints = pendingCompl.get(chatId);
        User user = userService.getUserByTelegramId(chatId);
        if (pendingCompl.containsKey(chatId)) {
            complaints.setText(message.text());
            complaintsService.saveComplaint(complaints);
            botService.sendMessage(String.valueOf(chatId), "Отправлено. Спасибо за отзыв!");
            pendingCompl.remove(chatId);
            employeePanel(chatId, user);
        }
    }
}