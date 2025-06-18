package com.axelor.orderr.ui;

import com.axelor.event.Observes;
import com.axelor.events.StartupEvent;
import com.google.inject.Inject;

public class BotLauncher {
    private final TgBotService botService;
    private final AdminPanel adminPanel;
    private final EmployeePanel employeePanel;

    @Inject
    public BotLauncher(TgBotService botService, AdminPanel adminPanel, EmployeePanel employeePanel) {
        this.adminPanel = adminPanel;
        this.botService = botService;
        this.employeePanel = employeePanel;
    }

    public void Launcher(@Observes StartupEvent startupEvent) {
        String token = Config.getToken();

        botService.sendMessage(Config.CHAT_ID, "cheers\n    /start");

        CommandHandler commandHandler = new CommandHandler(botService, adminPanel, employeePanel);

        botService.setUpdatesListener(commandHandler);

        try {
            Thread.sleep(30000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
