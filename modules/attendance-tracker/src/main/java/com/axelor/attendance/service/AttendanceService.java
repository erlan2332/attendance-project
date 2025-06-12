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

import javax.inject.Provider;
import javax.persistence.EntityManager;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Singleton
public class AttendanceService {

    private final AttendanceSessionRepository sessionRepo;
    private final EventRepository eventRepo;
    private final Provider<EntityManager> entityManagerProvider;

    @Inject
    public AttendanceService(AttendanceSessionRepository sessionRepo,
                             EventRepository eventRepo,
                             Provider<EntityManager> entityManagerProvider) {
        this.sessionRepo = sessionRepo;
        this.eventRepo = eventRepo;
        this.entityManagerProvider = entityManagerProvider;
    }

    public void importCsvAndProcess(EventImportWizard wizard) {
        MetaFile metaFile = wizard.getCsvData();
        if (metaFile == null) {
            throw new IllegalStateException("CSV файл не выбран.");
        }

        File file = MetaFiles.getPath(metaFile).toFile();
        System.out.println("Путь к файлу: " + (file != null ? file.getAbsolutePath() : "null"));

        if (file == null || !file.exists() || !file.canRead()) {
            throw new IllegalStateException("CSV файл не найден или недоступен для чтения: " + file);
        }

        EntityManager em = entityManagerProvider.get();
        em.getTransaction().begin();

        int counter = 0;
        int batchSize = 50;

        // Изменён форматтер – теперь ожидаем дату в виде "yyyy-MM-dd HH:mm:ss"
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        try (InputStream is = new FileInputStream(file);
             // Если ваш файл сохранён с BOM в UTF-8, стандартная кодировка UTF-8 должна подойти
             InputStreamReader isr = new InputStreamReader(is, StandardCharsets.UTF_8);
             BufferedReader reader = new BufferedReader(isr)) {

            String line;
            // Пропускаем строки до заголовка, который начинается с "Person ID;"
            while ((line = reader.readLine()) != null) {
                if (line.trim().startsWith("Person ID;")) {
                    break;
                }
            }

            CSVParser csvParser = CSVFormat.DEFAULT
                    .withDelimiter(';')
                    .withHeader("Person ID", "Name", "Department", "Time", "Attendance Check Point")
                    .withSkipHeaderRecord(true)
                    .withIgnoreEmptyLines(true)
                    .parse(reader);

            for (CSVRecord record : csvParser) {
                try {
                    String personId = record.get("Person ID").replace("'", "").trim();
                    String fullName = record.get("Name").trim();
                    String location = record.get("Department").trim();
                    String timeStr = record.get("Time").trim();
                    String checkpoint = record.get("Attendance Check Point").trim();

                    if (personId.isEmpty() || timeStr.isEmpty() || checkpoint.isEmpty()) continue;

                    LocalDateTime timestamp = LocalDateTime.parse(timeStr, fmt);

                    String type = "";
                    String lowerCheckpoint = checkpoint.toLowerCase();
                    if (lowerCheckpoint.startsWith("entrance")) {
                        type = "IN";
                    } else if (lowerCheckpoint.startsWith("exit")) {
                        type = "OUT";
                    }
                    if (type.isEmpty()) continue;

                    Event event = new Event();
                    event.setPersonId(personId);
                    event.setFullName(fullName);
                    event.setLocation(location);
                    event.setTimestamp(timestamp);
                    event.setEventType(type);

                    eventRepo.save(event);
                    counter++;

                    // Пакетное сохранение
                    if (counter % batchSize == 0) {
                        em.flush();
                        em.clear();
                    }

                } catch (Exception ex) {
                    System.out.println("Пропущена строка из-за ошибки: " + ex.getMessage());
                }
            }

            em.getTransaction().commit();
            System.out.println("Импортировано: " + counter);

        } catch (Exception e) {
            if (em.getTransaction().isActive()) {
                em.getTransaction().rollback();
            }
            throw new RuntimeException("❌ Ошибка обработки CSV", e);
        }
        calculateAttendanceSessions();
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
