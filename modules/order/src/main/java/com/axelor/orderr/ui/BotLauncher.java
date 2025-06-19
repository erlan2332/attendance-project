package com.axelor.orderr.ui;

import com.axelor.event.Observes;
import com.axelor.events.StartupEvent;
import com.axelor.orderr.service.user.UserService;
import com.google.inject.Inject;

import java.util.List;

public class BotLauncher {
    private final TgBotService botService;
    private final AdminPanel adminPanel;
    private final EmployeePanel employeePanel;
    private final UserService userService;

    @Inject
    public BotLauncher(TgBotService botService, AdminPanel adminPanel, EmployeePanel employeePanel, UserService userService) {
        this.adminPanel = adminPanel;
        this.botService = botService;
        this.employeePanel = employeePanel;
        this.userService = userService;
    }

    public void Launcher(@Observes StartupEvent startupEvent) {
        String token = Config.getToken();
        List<String> allChatIds = userService.getUsersTelegramId();

        for (String chatId : allChatIds) {
            botService.sendMessage(chatId, "cheers\n    /start");
        }

        CommandHandler commandHandler = new CommandHandler(botService, adminPanel, employeePanel);

        botService.setUpdatesListener(commandHandler);

        try {
            Thread.sleep(30000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
