package com.axelor.attendance.web;

import com.axelor.attendance.db.EventImportWizard;
import com.axelor.attendance.service.AttendanceService;
import com.axelor.inject.Beans;
import com.axelor.meta.db.MetaFile;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class AttendanceController {

    private static final Logger log = LoggerFactory.getLogger(AttendanceController.class);

    public void importCsv(ActionRequest req, ActionResponse res) {
        log.debug("Получение контекста и извлечение transient-модели EventImportWizard.");

        EventImportWizard wizard = req.getContext().asType(EventImportWizard.class);
        if (wizard == null) {
            log.warn("Transient-модель EventImportWizard не найдена в контексте.");
            res.setError("Транзиент-модель не найдена.");
            return;
        }

        MetaFile csvFile = wizard.getCsvData();
        if (csvFile == null) {
            log.warn("Поле csvData пусто. Файл не загружен.");
            res.setError("Файл не выбран или не загружен!");
            return;
        }

        log.info("CSV-файл получен ({} байт)", csvFile.getFileSize());

        try {
            log.debug("Передача transient-модели в AttendanceService для обработки CSV.");
            Beans.get(AttendanceService.class).importCsvAndProcess(wizard);
            log.info("Импорт завершён");
            res.setNotify("Данные успешно импортированы");
        } catch (Exception e) {
            log.error("Ошибка импорта CSV: ", e);
            res.setError("Ошибка импорта: " + e.getMessage());
        }
    }
}
