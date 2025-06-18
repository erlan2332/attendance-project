package com.axelor.orderr.ui;

import com.pengrad.telegrambot.*;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import com.pengrad.telegrambot.request.*;
import com.pengrad.telegrambot.response.BaseResponse;
import com.pengrad.telegrambot.response.GetUpdatesResponse;
import com.pengrad.telegrambot.response.SendResponse;
import okhttp3.OkHttpClient;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class TgBotService {
    private final TelegramBot bot;

    public TgBotService(String token) {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
        this.bot = new TelegramBot.Builder(token)
                .okHttpClient(client)
                .build();
    }

    public TelegramBot getBot() {
        return this.bot;
    }


    public void sendMessage(String chatId, String messageText) {
        SendMessage request = new SendMessage(chatId, messageText);
        SendResponse response = bot.execute(request);
        if (response.isOk()) {
            System.out.println("Сообщение отправлено успешно!");
        } else {
            System.err.println("Ошибка отправки сообщения, код ошибки: " + response.errorCode() + ". сообщение: " + messageText);
        }
    }

    public void sendMessage(String chatId, String messageText, InlineKeyboardMarkup replyMarkup) {
        SendMessage request = new SendMessage(chatId, messageText).replyMarkup(replyMarkup);
        SendResponse response = bot.execute(request);
        if (response.isOk()) {
            System.out.println("Сообщение отправлено успешно!");
        } else {
            System.err.println("Ошибка отправки сообщения: " + response.errorCode());
        }
    }

    public void safeDelete(String chatId, int messageId) {
        DeleteMessage request = new DeleteMessage(chatId, messageId);
        BaseResponse response = bot.execute(request);
        if (!response.isOk()) {
            System.err.println("Ошибка при удалении сообщения: " + response.errorCode());
        }
    }

    public List<Update> getUpdates(int limit, int offset, int timeout) {
        GetUpdates getUpdates = new GetUpdates().limit(limit).offset(offset).timeout(timeout);
        GetUpdatesResponse response = bot.execute(getUpdates);
        return response.updates();
    }

    public void setUpdatesListener(CommandHandler commandHandler) {
        bot.setUpdatesListener(new UpdatesListener() {
            @Override
            public int process(List<Update> updates) {
                System.out.println("Listener: получено " + updates.size() + " обновлений");
                for (Update update : updates) {
                    commandHandler.processUpdate(update);
                }
                return UpdatesListener.CONFIRMED_UPDATES_ALL;
            }
        }, new ExceptionHandler() {
            @Override
            public void onException(TelegramException e) {
                if (e.response() != null) {
                    System.err.println("Listener: ошибка " +
                            e.response().errorCode() + " - " +
                            e.response().description());
                } else {
                    e.printStackTrace();
                }
            }
        });
    }
}