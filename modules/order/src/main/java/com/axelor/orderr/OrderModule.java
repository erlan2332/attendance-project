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
import com.axelor.orderr.service.rating.DishRatingService;
import com.axelor.orderr.service.rating.DishRatingServiceImpl;
import com.axelor.orderr.service.user.UserService;
import com.axelor.orderr.service.user.UserServiceImpl;
import com.axelor.orderr.ui.*;

public class OrderModule extends AxelorModule {
    @Override
    protected void configure() {
        bind(CommandHandler.class).asEagerSingleton();
        bind(DishService.class).to(DishServiceImpl.class);
        bind(MenuService.class).to(MenuServiceImpl.class);
        bind(UserService.class).to(UserServiceImpl.class);
        bind(OrderService.class).to(OrderServiceImpl.class);
        bind(ComplaintsService.class).to(ComplaintsServiceImpl.class);
        bind(DishRatingService.class).to(DishRatingServiceImpl.class);
        bind(BotLauncher.class);
        bind(TgBotService.class).toProvider(() -> new TgBotService(Config.getToken()));
        bind(ReportScheduler.class).asEagerSingleton();
    }
}
