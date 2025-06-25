package com.axelor.orderr.ui;

import com.axelor.event.Observes;
import com.axelor.events.StartupEvent;
import com.axelor.orderr.service.order.OrderService;
import com.google.inject.Inject;


public class BotLauncher {
    private final TgBotService botService;
    private final AdminPanel adminPanel;
    private final EmployeePanel employeePanel;
    private final OrderService orderService;

    @Inject
    public BotLauncher(TgBotService botService, AdminPanel adminPanel, EmployeePanel employeePanel, OrderService orderService) {
        this.adminPanel = adminPanel;
        this.botService = botService;
        this.employeePanel = employeePanel;
        this.orderService = orderService;
    }

    public void Launcher(@Observes StartupEvent startupEvent) {
        String token = Config.getToken();
//        List<String> allChatIds = userService.getUsersTelegramId();
//
//        for (String chatId : allChatIds) {
//            botService.sendMessage(chatId, "cheers\n    /start");
//        }

        botService.sendMessage("1607228323", "cheers\n    /start");

        CommandHandler commandHandler = new CommandHandler(botService, adminPanel, employeePanel);

        ReportScheduler scheduler = new ReportScheduler(orderService, botService);
        scheduler.scheduleMonthlyReport();

        botService.setUpdatesListener(commandHandler);

        try {
            Thread.sleep(30000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
