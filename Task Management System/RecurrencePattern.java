import java.io.Serializable;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class RecurrencePattern implements Serializable {

    private static final long serialVersionUID = 1L;

    private final RecurrenceType type;
    private final int interval;
    private final LocalDate startDate;
    private final LocalDate endDate;
    private final Set<DayOfWeek> weekdays;
    private final Integer dayOfMonth;

    public RecurrencePattern(
        RecurrenceType type,
        int interval,
        LocalDate startDate,
        LocalDate endDate,
        Set<DayOfWeek> weekdays,
        Integer dayOfMonth
    ) {
        if (type == null) {
            throw new IllegalArgumentException("Recurrence type is required.");
        }
        if (interval <= 0) {
            throw new IllegalArgumentException("Recurrence interval must be a positive integer.");
        }
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("Recurrence start and end dates are required.");
        }
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("Recurrence end date cannot be before the start date.");
        }
        if (type == RecurrenceType.WEEKLY && (weekdays == null || weekdays.isEmpty())) {
            throw new IllegalArgumentException("Weekly recurrence requires at least one weekday.");
        }
        if (type == RecurrenceType.MONTHLY && (dayOfMonth == null || dayOfMonth < 1 || dayOfMonth > 31)) {
            throw new IllegalArgumentException("Monthly recurrence requires a day of month from 1 to 31.");
        }

        this.type = type;
        this.interval = interval;
        this.startDate = startDate;
        this.endDate = endDate;
        this.weekdays = weekdays == null ? new LinkedHashSet<>() : new LinkedHashSet<>(weekdays);
        this.dayOfMonth = dayOfMonth;
    }

    public RecurrenceType getType() {
        return type;
    }

    public int getInterval() {
        return interval;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public Set<DayOfWeek> getWeekdays() {
        return Collections.unmodifiableSet(weekdays);
    }

    public Integer getDayOfMonth() {
        return dayOfMonth;
    }

    public List<LocalDate> generateDueDates() {
        List<LocalDate> dueDates = new ArrayList<>();

        switch (type) {
            case DAILY, CUSTOM -> generateDailyLikeDates(dueDates);
            case WEEKLY -> generateWeeklyDates(dueDates);
            case MONTHLY -> generateMonthlyDates(dueDates);
            default -> throw new IllegalStateException("Unsupported recurrence type: " + type);
        }

        return dueDates;
    }

    private void generateDailyLikeDates(List<LocalDate> dueDates) {
        for (LocalDate currentDate = startDate; !currentDate.isAfter(endDate); currentDate = currentDate.plusDays(interval)) {
            dueDates.add(currentDate);
        }
    }

    private void generateWeeklyDates(List<LocalDate> dueDates) {
        LocalDate startWeek = startDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        for (LocalDate currentDate = startDate; !currentDate.isAfter(endDate); currentDate = currentDate.plusDays(1)) {
            if (!weekdays.contains(currentDate.getDayOfWeek())) {
                continue;
            }

            LocalDate currentWeek = currentDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            long weeksBetween = ChronoUnit.WEEKS.between(startWeek, currentWeek);
            if (weeksBetween % interval == 0) {
                dueDates.add(currentDate);
            }
        }
    }

    private void generateMonthlyDates(List<LocalDate> dueDates) {
        int currentDay = dayOfMonth == null ? startDate.getDayOfMonth() : dayOfMonth;
        LocalDate cursor = startDate.withDayOfMonth(1);

        while (!cursor.isAfter(endDate.withDayOfMonth(1))) {
            int safeDay = Math.min(currentDay, cursor.lengthOfMonth());
            LocalDate candidate = cursor.withDayOfMonth(safeDay);
            if (!candidate.isBefore(startDate) && !candidate.isAfter(endDate)) {
                dueDates.add(candidate);
            }
            cursor = cursor.plusMonths(interval);
        }
    }
}
