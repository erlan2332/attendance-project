package com.axelor.orderr.ui;

import com.axelor.event.Observes;
import com.axelor.events.StartupEvent;
import com.axelor.orderr.service.order.OrderService;
import com.axelor.orderr.service.user.UserService;
import com.google.inject.Inject;

import java.util.List;


public class BotLauncher {
    private final TgBotService botService;
    private final ReportScheduler scheduler;
    private final UserService userService;
    private final CommandHandler commandHandler;

    @Inject
    public BotLauncher(TgBotService botService, ReportScheduler scheduler, UserService userService, CommandHandler commandHandler) {
        this.botService = botService;
        this.scheduler = scheduler;
        this.userService = userService;
        this.commandHandler = commandHandler;
    }

    public void Launcher(@Observes StartupEvent startupEvent) {
        List<String> allChatIds = userService.getUsersTelegramId();

        for (String chatId : allChatIds) {
            botService.sendMessage(chatId, "cheers\n    /start");
        }

        scheduler.scheduleMonthlyReport();

        botService.setUpdatesListener(commandHandler);

        try {
            Thread.sleep(30000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
