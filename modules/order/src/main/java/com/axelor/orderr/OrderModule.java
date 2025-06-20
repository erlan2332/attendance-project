package com.axelor.orderr;

import com.axelor.app.AxelorModule;
import com.axelor.orderr.service.complaints.ComplaintsService;
import com.axelor.orderr.service.complaints.ComplaintsServiceImpl;
import com.axelor.orderr.service.dish.DishService;
import com.axelor.orderr.service.dish.DishServiceImpl;
import com.axelor.orderr.service.menu.MenuService;
import com.axelor.orderr.service.menu.MenuServiceImpl;
import com.axelor.orderr.service.order.OrderService;
import com.axelor.orderr.service.order.OrderServiceImpl;
import com.axelor.orderr.service.user.UserService;
import com.axelor.orderr.service.user.UserServiceImpl;
import com.axelor.orderr.ui.BotLauncher;
import com.axelor.orderr.ui.Config;
import com.axelor.orderr.ui.TgBotService;

public class OrderModule extends AxelorModule {
    @Override
    protected void configure() {
        bind(DishService.class).to(DishServiceImpl.class);
        bind(MenuService.class).to(MenuServiceImpl.class);
        bind(UserService.class).to(UserServiceImpl.class);
        bind(OrderService.class).to(OrderServiceImpl.class);
        bind(ComplaintsService.class).to(ComplaintsServiceImpl.class);
        bind(BotLauncher.class);
        bind(TgBotService.class).toProvider(() -> new TgBotService(Config.getToken()));
    }
}
