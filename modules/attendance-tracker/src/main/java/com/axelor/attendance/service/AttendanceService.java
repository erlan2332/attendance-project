package com.axelor.attendance.service;

import com.axelor.attendance.db.AttendanceSession;
import com.axelor.attendance.db.Event;
import com.axelor.attendance.db.EventImportWizard;
import com.axelor.attendance.db.repo.AttendanceSessionRepository;
import com.axelor.attendance.db.repo.EventRepository;
import com.axelor.meta.MetaFiles;
import com.axelor.meta.db.MetaFile;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import javax.inject.Provider;
import javax.persistence.EntityManager;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Singleton
public class AttendanceService {

    private final AttendanceSessionRepository sessionRepo;
    private final EventRepository eventRepo;
    private final Provider<EntityManager> entityManagerProvider;

    @Inject
    public AttendanceService(
            AttendanceSessionRepository sessionRepo,
            EventRepository eventRepo,
            Provider<EntityManager> entityManagerProvider
    ) {
        this.sessionRepo = sessionRepo;
        this.eventRepo = eventRepo;
        this.entityManagerProvider = entityManagerProvider;
    }

    public void importCsvAndProcess(EventImportWizard wizard) {
        MetaFile metaFile = wizard.getCsvData();
        if (metaFile == null) {
            throw new IllegalStateException("Файл не выбран.");
        }

        File file = MetaFiles.getPath(metaFile).toFile();
        if (file == null || !file.exists() || !file.canRead()) {
            throw new IllegalStateException("Файл не найден или недоступен для чтения: " + file);
        }

        String fileName = file.getName().toLowerCase();
        if (fileName.endsWith(".csv")) {
            importFromCsv(file);
        } else if (fileName.endsWith(".xlsx") || fileName.endsWith(".xls")) {
            importFromExcel(file);
        } else {
            throw new IllegalArgumentException("Поддерживаются только CSV, XLS, XLSX");
        }

        calculateAttendanceSessions();
    }

    private void importFromExcel(File file) {
        System.out.println("📄 Импорт из Excel: " + file.getName());
        EntityManager em = entityManagerProvider.get();
        em.getTransaction().begin();

        int counter = 0;
        int batchSize = 50;
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        try (FileInputStream fis = new FileInputStream(file)) {
            Workbook workbook = file.getName().endsWith(".xlsx") ? new XSSFWorkbook(fis) : new HSSFWorkbook(fis);
            Sheet sheet = workbook.getSheetAt(0);
            Iterator<Row> rowIterator = sheet.iterator();

            int skipped = 0;

            while (rowIterator.hasNext()) {
                Row row = rowIterator.next();

                // Пропускаем первые 2 строки
                if (skipped < 2) {
                    skipped++;
                    continue;
                }

                // Пропускаем заголовок (строка 3)
                if (skipped == 2) {
                    skipped++;
                    continue;
                }

                if (row.getPhysicalNumberOfCells() < 6) continue;

                try {
                    String personId = getCellValue(row.getCell(0)).replace("'", "").trim();
                    String fullName = getCellValue(row.getCell(1)).trim();
                    String location = getCellValue(row.getCell(2)).trim();
                    String timeStr = getCellValue(row.getCell(3)).trim();
                    String checkpoint = getCellValue(row.getCell(5)).trim(); // было cell(4), стало cell(5)

                    if (personId.isEmpty() || timeStr.isEmpty() || checkpoint.isEmpty()) continue;

                    LocalDateTime timestamp;
                    try {
                        timestamp = LocalDateTime.parse(timeStr, fmt);
                    } catch (Exception e) {
                        System.out.println("⛔ Неверный формат даты: " + timeStr);
                        continue;
                    }

                    String type = getEventType(checkpoint);
                    if (type == null) {
                        System.out.println("⚠️ Неизвестный тип события: " + checkpoint);
                        continue;
                    }

                    Event event = new Event();
                    event.setPersonId(personId);
                    event.setFullName(fullName);
                    event.setLocation(location);
                    event.setTimestamp(timestamp);
                    event.setEventType(type);

                    eventRepo.save(event);
                    counter++;

                    if (counter % batchSize == 0) {
                        em.flush();
                        em.clear();
                    }

                } catch (Exception ex) {
                    System.out.println("⚠️ Ошибка при обработке строки: " + ex.getMessage());
                }
            }

            em.getTransaction().commit();
            System.out.println("✅ Импортировано из Excel: " + counter);

        } catch (Exception e) {
            if (em.getTransaction().isActive()) em.getTransaction().rollback();
            throw new RuntimeException("Ошибка обработки Excel-файла", e);
        }
    }


    private void importFromCsv(File file) {
        System.out.println("📄 Импорт из CSV: " + file.getName());
        EntityManager em = entityManagerProvider.get();
        em.getTransaction().begin();

        int counter = 0;
        int batchSize = 50;
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().startsWith("Person ID;")) break;
            }

            CSVParser csvParser = CSVFormat.DEFAULT
                    .withDelimiter(';')
                    .withHeader("Person ID", "Name", "Department", "Time", "Attendance Check Point")
                    .withSkipHeaderRecord(true)
                    .withIgnoreEmptyLines(true)
                    .parse(reader);

            for (CSVRecord record : csvParser) {
                try {
                    if (record.size() < 5) continue;

                    String personId = record.get("Person ID").replace("'", "").trim();
                    String fullName = record.get("Name").trim();
                    String location = record.get("Department").trim();
                    String timeStr = record.get("Time").trim();
                    String checkpoint = record.get("Attendance Check Point").trim();

                    if (personId.isEmpty() || timeStr.isEmpty() || checkpoint.isEmpty()) continue;

                    LocalDateTime timestamp;
                    try {
                        timestamp = LocalDateTime.parse(timeStr, fmt);
                    } catch (Exception e) {
                        System.out.println("⛔ Неверный формат даты: " + timeStr);
                        continue;
                    }

                    String type = getEventType(checkpoint);
                    if (type == null) {
                        System.out.println("⚠️ Неизвестный тип события: " + checkpoint);
                        continue;
                    }

                    Event event = new Event();
                    event.setPersonId(personId);
                    event.setFullName(fullName);
                    event.setLocation(location);
                    event.setTimestamp(timestamp);
                    event.setEventType(type);

                    eventRepo.save(event);
                    counter++;

                    if (counter % batchSize == 0) {
                        em.flush();
                        em.clear();
                    }

                } catch (Exception ex) {
                    System.out.println("⚠️ Ошибка при обработке строки: " + ex.getMessage());
                }
            }

            em.getTransaction().commit();
            System.out.println("✅ Импортировано из CSV: " + counter);

        } catch (Exception e) {
            if (em.getTransaction().isActive()) em.getTransaction().rollback();
            throw new RuntimeException("Ошибка обработки CSV-файла", e);
        }
    }

    private String getCellValue(Cell cell) {
        if (cell == null) return "";
        int type = cell.getCellType();
        if (type == 1) return cell.getStringCellValue().trim(); // STRING
        else if (type == 0) { // NUMERIC
            if (DateUtil.isCellDateFormatted(cell)) {
                Date date = cell.getDateCellValue();
                LocalDateTime dt = date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
                return dt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            } else {
                return String.valueOf((long) cell.getNumericCellValue());
            }
        } else if (type == 2) return cell.getCellFormula(); // FORMULA
        else if (type == 4) return String.valueOf(cell.getBooleanCellValue()); // BOOLEAN
        return "";
    }

    private String getEventType(String checkpoint) {
        if (checkpoint == null) return null;
        String cp = checkpoint.trim().toLowerCase();

        if (cp.startsWith("entrance") || cp.contains(" entrance")) return "IN";
        if (cp.startsWith("exit") || cp.contains(" exit")) return "OUT";
        if (cp.contains("entrance door")) return "IN";
        if (cp.contains("exit door")) return "OUT";
        return null;
    }


    public void calculateAttendanceSessions() {
        EntityManager em = entityManagerProvider.get();
        em.getTransaction().begin();

        try {
            List<Event> events = eventRepo.all().order("timestamp").fetch();
            System.out.println("Fetched events count: " + events.size());

            Map<LocalDate, Map<String, List<Event>>> grouped = new HashMap<>();
            for (Event ev : events) {
                LocalDate date = ev.getTimestamp().toLocalDate();
                grouped.computeIfAbsent(date, d -> new HashMap<>())
                        .computeIfAbsent(ev.getPersonId(), id -> new ArrayList<>())
                        .add(ev);
            }

            int totalSessions = 0;

            for (Map.Entry<LocalDate, Map<String, List<Event>>> dateEntry : grouped.entrySet()) {
                LocalDate date = dateEntry.getKey();
                for (Map.Entry<String, List<Event>> personEntry : dateEntry.getValue().entrySet()) {
                    String personId = personEntry.getKey();
                    List<Event> personEvents = personEntry.getValue();
                    personEvents.sort(Comparator.comparing(Event::getTimestamp));

                    Optional<LocalDateTime> firstInOpt = personEvents.stream()
                            .filter(e -> "IN".equalsIgnoreCase(e.getEventType()))
                            .map(Event::getTimestamp)
                            .min(LocalDateTime::compareTo);
                    Optional<LocalDateTime> lastOutOpt = personEvents.stream()
                            .filter(e -> "OUT".equalsIgnoreCase(e.getEventType()))
                            .map(Event::getTimestamp)
                            .max(LocalDateTime::compareTo);

                    if (!firstInOpt.isPresent() || !lastOutOpt.isPresent()) {
                        System.out.println("⚠️ Пропуск: нет IN или OUT для " + personId + " на " + date);
                        continue;
                    }

                    LocalDateTime sessionStart = firstInOpt.get();
                    LocalDateTime sessionEnd = lastOutOpt.get();

                    if (sessionStart.isAfter(sessionEnd)) {
                        System.out.println("⚠️ sessionStart > sessionEnd, пропускаем " + personId + " на " + date);
                        continue;
                    }

                    long officeDurationMinutes = 0;
                    LocalDateTime currentIn = null;

                    for (Event ev : personEvents) {
                        LocalDateTime time = ev.getTimestamp();
                        if (time.isBefore(sessionStart) || time.isAfter(sessionEnd))
                            continue;

                        if ("IN".equalsIgnoreCase(ev.getEventType())) {
                            if (currentIn == null)
                                currentIn = time;
                        } else if ("OUT".equalsIgnoreCase(ev.getEventType())) {
                            if (currentIn != null) {
                                long minutes = Duration.between(currentIn, time).toMinutes();
                                if (minutes > 0)
                                    officeDurationMinutes += minutes;
                                currentIn = null;
                            }
                        }
                    }

                    long totalMinutes = Duration.between(sessionStart, sessionEnd).toMinutes();
                    long outMinutes = totalMinutes - officeDurationMinutes;

                    String officeDurStr = String.format("%02d:%02d", officeDurationMinutes / 60, officeDurationMinutes % 60);
                    String outDurStr = String.format("%02d:%02d", outMinutes / 60, outMinutes % 60);

                    // Отбираем события в пределах времени
                    List<Event> sessionEvents = personEvents.stream()
                            .filter(ev -> {
                                LocalDateTime t = ev.getTimestamp();
                                return !t.isBefore(sessionStart) && !t.isAfter(sessionEnd);
                            })
                            .collect(Collectors.toList());

                    AttendanceSession session = new AttendanceSession();
                    session.setPersonId(personId);
                    session.setFullName(personEvents.get(0).getFullName());
                    session.setSessionDate(date);
                    session.setSessionStart(sessionStart);
                    session.setSessionEnd(sessionEnd);
                    session.setOfficeDuration(officeDurStr);
                    session.setOutOfOfficeDuration(outDurStr);

                    // Новая логика: сохраняем только время начала сессии в формате "HH:mm"
                    if (sessionStart != null) {
                        String sessionStartTime = sessionStart.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
                        session.setSessionStartTime(sessionStartTime);
                    } else {
                        session.setSessionStartTime(null);
                    }

                    // Устанавливаем связь между сессией и событиями
                    session.setEvents(sessionEvents);
                    for (Event ev : sessionEvents) {
                        ev.setAttendanceSession(session);
                    }

                    sessionRepo.save(session); // сохранит и связанные события
                    totalSessions++;
                    System.out.println("✅ Сессия: " + personId + " за " + date + " | В офисе: " + officeDurStr + ", вне офиса: " + outDurStr);
                }
            }

            em.flush();
            em.getTransaction().commit();
            System.out.println("🎯 Готово. Всего сессий: " + totalSessions);
        } catch (Exception e) {
            e.printStackTrace();
            if (em.getTransaction().isActive()) {
                em.getTransaction().rollback();
            }
            throw new RuntimeException("❌ Ошибка при расчёте сессий", e);
        }
    }


}