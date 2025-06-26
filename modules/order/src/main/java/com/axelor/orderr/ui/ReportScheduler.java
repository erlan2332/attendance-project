package com.axelor.orderr.ui;

import com.axelor.auth.db.User;
import com.axelor.orderr.service.order.OrderService;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Singleton
public class ReportScheduler {

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final OrderService orderService;
    private final TgBotService botService;

    @Inject
    public ReportScheduler(OrderService orderService, TgBotService botService) {
        this.orderService = orderService;
        this.botService = botService;
    }

    public void scheduleMonthlyReport() {
        long initialDelay = computeInitialDelay();
        scheduler.schedule(() -> {
            sendLastMonthReport();
            scheduleMonthlyReport();
        }, initialDelay, TimeUnit.MILLISECONDS);
    }

    private long computeInitialDelay() {
        LocalDateTime now = LocalDateTime.now();
        LocalDate nextMonthDate = now.getDayOfMonth() == 1 && now.getHour() < 9 ?
                now.toLocalDate() :
                now.toLocalDate().plusMonths(1).withDayOfMonth(1);
        LocalDateTime nextRun = LocalDateTime.of(nextMonthDate, LocalTime.of(9, 0));
        return Duration.between(now, nextRun).toMillis();
//        return 10_000L; // для теста
    }

    private void sendLastMonthReport() {
        Map<User, BigDecimal> report = orderService.getLastMonthReport();
        report.forEach((user, total) -> {
            String text = "Добрый день, "
                    + user.getName() +
                    "\nОбщая сумма за заказы за прошлый месяц: "
                    + total
                    + " сом";
            botService.sendMessage(user.getTg_id(), text);
            System.out.println("Отчет отправлен пользвателю " + user.getName() + " - " + user.getTg_id());
        });
        System.out.println("Сообщение с отчетом отправлено в " + LocalDateTime.now());
    }

    public void shutdown() {
        scheduler.shutdown();
    }
}
