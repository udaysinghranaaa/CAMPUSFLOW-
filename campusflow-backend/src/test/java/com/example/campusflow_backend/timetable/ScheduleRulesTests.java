package com.example.campusflow_backend.timetable;
import org.junit.jupiter.api.Test;
import java.time.*;
import com.example.campusflow_backend.exception.InvalidRequestException;
import static org.assertj.core.api.Assertions.*;
class ScheduleRulesTests {
 @Test void halfOpenAdjacentIntervalsDoNotClash(){assertThat(ScheduleRules.overlaps(LocalTime.of(9,0),LocalTime.of(10,0),LocalTime.of(10,0),LocalTime.of(11,0))).isFalse();}
 @Test void overlappingIntervalsClash(){assertThat(ScheduleRules.overlaps(LocalTime.of(9,0),LocalTime.of(10,0),LocalTime.of(9,59),LocalTime.of(11,0))).isTrue();}
 @Test void invalidTimesAreRejected(){assertThatThrownBy(()->ScheduleRules.times(LocalTime.NOON,LocalTime.NOON)).isInstanceOf(InvalidRequestException.class);}
 @Test void invertedOrOversizeTermsAreRejected(){assertThatThrownBy(()->ScheduleRules.dates(LocalDate.of(2026,1,2),LocalDate.of(2026,1,1))).isInstanceOf(InvalidRequestException.class);assertThatThrownBy(()->ScheduleRules.dates(LocalDate.of(2026,1,1),LocalDate.of(2028,1,1))).isInstanceOf(InvalidRequestException.class);}
 @Test void mondayStartIncludesSundayOfSameWeek(){assertThat(ScheduleRules.monday(LocalDate.of(2026,10,25))).isEqualTo(LocalDate.of(2026,10,19));}
 @Test void coverageRequiresBothDayAndEntireTimeWindow(){var day=LocalDate.of(2026,10,19);assertThat(ScheduleRules.covered(1,day,LocalTime.of(8,0),LocalTime.of(18,0),LocalTime.of(9,0),LocalTime.of(10,0))).isTrue();assertThat(ScheduleRules.covered(2,day,LocalTime.of(8,0),LocalTime.of(18,0),LocalTime.of(9,0),LocalTime.of(10,0))).isFalse();assertThat(ScheduleRules.covered(1,day,LocalTime.of(8,0),LocalTime.of(18,0),LocalTime.of(7,0),LocalTime.of(10,0))).isFalse();}
 @Test void freeExplanationNeverPromisesPhysicalPresence(){assertThat(ScheduleReadRepository.explanation(TimetableTypes.AvailabilityState.FREE)).contains("does not guarantee physical");}
 @Test void campusDateUsesKolkataMidnight(){var service=new TimetableReadService(null,null,Clock.fixed(Instant.parse("2026-10-18T20:00:00Z"),ZoneOffset.UTC));assertThat(service.today()).isEqualTo(LocalDate.of(2026,10,19));}
}
