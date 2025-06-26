package com.axelor.orderr.ui;

import com.axelor.app.AppSettings;

public class Config {

    public static String getToken(){
        return AppSettings.get().get("telegram.bot.token");
    }

    public static String getAdminPassword() {
        return AppSettings.get().get("telegram.admin.password").toString();
    }
}
