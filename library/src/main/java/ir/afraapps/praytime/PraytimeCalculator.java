package ir.afraapps.praytime;

import android.util.SparseArray;

import java.util.Calendar;
import java.util.Date;

import static ir.afraapps.praytime.StaticUtils.deg;
import static ir.afraapps.praytime.StaticUtils.dtr;
import static ir.afraapps.praytime.StaticUtils.fixHour;
import static ir.afraapps.praytime.StaticUtils.min;
import static ir.afraapps.praytime.StaticUtils.rtd;


public class PraytimeCalculator {

  // default times
  private final static SparseArray<Double> defaultTimes;

  static {
    defaultTimes = new SparseArray<>();
    defaultTimes.put(Time.IMSAK, 5d / 24);
    defaultTimes.put(Time.FAJR, 5d / 24);
    defaultTimes.put(Time.SUNRISE, 6d / 24);
    defaultTimes.put(Time.DHUHR, 12d / 24);
    defaultTimes.put(Time.ASR, 13d / 24);
    defaultTimes.put(Time.SUNSET, 18d / 24);
    defaultTimes.put(Time.MAGHRIB, 18d / 24);
    defaultTimes.put(Time.ISHA, 18d / 24);
  }

  private final MinuteOrAngleDouble imsak = min(10);
  private final MinuteOrAngleDouble dhuhr = min(0);
  private final CalculationMethod.AsrJuristics asr = CalculationMethod.AsrJuristics.Standard;
  private final CalculationMethod.HighLatMethods highLats = CalculationMethod.HighLatMethods.NightMiddle;
  private double timeZone;
  private double jDate;
  private Coordinate coordinate;
  private CalculationMethod method;


  public PraytimeCalculator(CalculationMethod method) {
    this.method = method;
  }


  public SparseArray<Date> calculate(Date date, Coordinate coordinate, double timeZone, boolean inDaylightTime) {
    this.coordinate = coordinate;
    this.timeZone = timeZone;
    if (inDaylightTime) {
      this.timeZone++;
    }
    Calendar calendar = Calendar.getInstance();
    calendar.setTime(date);
    int year = calendar.get(Calendar.YEAR);
    int month = calendar.get(Calendar.MONTH) + 1;
    int day = calendar.get(Calendar.DAY_OF_MONTH);
    jDate = julian(year, month, day) - this.coordinate.getLongitude() / (15d * 24d);

    // compute prayer times at given julian date
    SparseArray<Double> times = new SparseArray<>();

    times.put(Time.IMSAK,
      sunAngleTime(imsak, defaultTimes.get(Time.IMSAK), true));
    times.put(
      Time.FAJR,
      sunAngleTime(method.getFajr(),
        defaultTimes.get(Time.FAJR), true));
    times.put(
      Time.SUNRISE,
      sunAngleTime(riseSetAngle(),
        defaultTimes.get(Time.SUNRISE), true));
    times.put(Time.DHUHR, midDay(defaultTimes.get(Time.DHUHR)));
    times.put(Time.ASR,
      asrTime(asrFactor(), defaultTimes.get(Time.ASR)));
    times.put(
      Time.SUNSET,
      sunAngleTime(riseSetAngle(), defaultTimes.get(Time.SUNSET)));
    times.put(
      Time.MAGHRIB,
      sunAngleTime(method.getMaghrib(),
        defaultTimes.get(Time.MAGHRIB)));
    times.put(
      Time.ISHA,
      sunAngleTime(method.getIsha(),
        defaultTimes.get(Time.ISHA)));

    times = adjustTimes(times);

    // add midnight time
    times.put(
      Time.MIDNIGHT,
      (method.getMidnight() == CalculationMethod.MidnightType.Jafari) ? times
        .get(Time.SUNSET)
        + timeDiff(times.get(Time.SUNSET),
        times.get(Time.FAJR)) / 2 : times
        .get(Time.SUNSET)
        + timeDiff(times.get(Time.SUNSET),
        times.get(Time.SUNRISE)) / 2);

    SparseArray<Date> result = new SparseArray<>();
    calendar.set(Calendar.SECOND, 0);
    calendar.set(Calendar.MILLISECOND, 0);
    Clock clock;

    for (int i = 0, length = times.size(); i < length; i++) {
      clock = Clock.fromDouble(times.valueAt(i));
      calendar.set(Calendar.HOUR_OF_DAY, clock.getHour());
      calendar.set(Calendar.MINUTE, clock.getMinute());
      result.put(times.keyAt(i), new Date(calendar.getTimeInMillis()));
    }

    return result;
  }


  public SparseArray<Date> calculateAzanTime(Date date, Coordinate coordinate, double timeZone, boolean inDaylightTime) {
    SparseArray<Date> azanTimes = calculate(date, coordinate, timeZone, inDaylightTime);
    azanTimes.remove(Time.IMSAK);
    azanTimes.remove(Time.SUNSET);
    azanTimes.remove(Time.SUNRISE);
    azanTimes.remove(Time.MIDNIGHT);
    return azanTimes;
  }


  public SparseArray<Date> calculateChantTime(Date date, Coordinate coordinate, double timeZone, boolean inDaylightTime) {
    SparseArray<Date> azanTimes = calculate(date, coordinate, timeZone, inDaylightTime);
    azanTimes.remove(Time.IMSAK);
    return azanTimes;
  }


  public SparseArray<Date> calculateBriefPraytime(Date date, Coordinate coordinate, double timeZone, boolean inDaylightTime) {
    SparseArray<Date> azanTimes = calculate(date, coordinate, timeZone, inDaylightTime);
    azanTimes.remove(Time.IMSAK);
    azanTimes.remove(Time.ASR);
    azanTimes.remove(Time.ISHA);
    return azanTimes;
  }


  // compute mid-day time
  private double midDay(double time) {
    double eqt = sunPosition(jDate + time).getEquation();
    return fixHour(12 - eqt); // noon
  }


  // compute the time at which sun reaches a specific angle below horizon
  private double sunAngleTime(MinuteOrAngleDouble angle, double time,
                              boolean ccw) {
    // TODO: I must enable below line!
    // if (angle.isMin()) throw new
    // IllegalArgumentException("angle argument must be degree, not minute!");
    double decl = sunPosition(jDate + time).getDeclination();
    double noon = dtr(midDay(time));
    double t = Math.acos((-Math.sin(dtr(angle.getValue())) - Math.sin(decl)
      * Math.sin(dtr(coordinate.getLatitude())))
      / (Math.cos(decl) * Math.cos(dtr(coordinate.getLatitude())))) / 15d;
    return rtd(noon + (ccw ? -t : t));
  }


  private double sunAngleTime(MinuteOrAngleDouble angle, double time) {
    return sunAngleTime(angle, time, false);
  }


  // compute asr time
  private double asrTime(double factor, double time) {
    double decl = sunPosition(jDate + time).getDeclination();
    double angle = -Math.atan(1 / (factor + Math.tan(dtr(coordinate
      .getLatitude()) - decl)));
    return sunAngleTime(deg(rtd(angle)), time);
  }

  // compute declination angle of sun and equation of time
  // Ref: http://aa.usno.navy.mil/faq/docs/SunApprox.php
  private DeclEqt sunPosition(double jd) {
    double D = jd - 2451545d;
    double g = (357.529 + 0.98560028 * D) % 360;
    double q = (280.459 + 0.98564736 * D) % 360;
    double L = (q + 1.915 * Math.sin(dtr(g)) + 0.020 * Math
      .sin(dtr(2d * g))) % 360;

    // weird!
    // double R = 1.00014 - 0.01671 * Math.cos(dtr(g)) - 0.00014 *
    // Math.cos(dtr(2d * g));

    double e = 23.439 - 0.00000036 * D;

    double RA = rtd(Math.atan2(Math.cos(dtr(e)) * Math.sin(dtr(L)),
      Math.cos(dtr(L)))) / 15d;
    double eqt = q / 15d - fixHour(RA);
    double decl = Math.asin(Math.sin(dtr(e)) * Math.sin(dtr(L)));

    return new DeclEqt(decl, eqt);
  }

  // convert Gregorian date to Julian day
  // Ref: Astronomical Algorithms by Jean Meeus
  private double julian(int year, int month, int day) {
    if (month <= 2) {
      year -= 1;
      month += 12;
    }
    double A = Math.floor((double) year / 100);
    double B = 2 - A + Math.floor(A / 4);

    double JD = Math.floor(365.25 * (year + 4716))
      + Math.floor(30.6001 * (month + 1)) + day + B - 1524.5;
    return JD;
  }

  // adjust times
  private SparseArray<Double> adjustTimes(SparseArray<Double> times) {
    SparseArray<Double> result = new SparseArray<>();
    for (int i = 0, length = times.size(); i < length; i++) {
      result.put(times.keyAt(i),
        times.valueAt(i) + timeZone - coordinate.getLongitude() / 15d);
    }

    if (highLats != CalculationMethod.HighLatMethods.None) {
      result = adjustHighLats(result);
    }

    if (imsak.isMin()) {
      result.put(Time.IMSAK,
        result.get(Time.FAJR) - imsak.getValue() / 60);
    }
    if (method.getMaghrib().isMin()) {
      result.put(Time.MAGHRIB, result.get(Time.SUNSET)
        + method.getMaghrib().getValue() / 60d);
    }
    if (method.getIsha().isMin()) {
      result.put(Time.ISHA, result.get(Time.MAGHRIB)
        + method.getIsha().getValue() / 60d);
    }
    result.put(Time.DHUHR,
      result.get(Time.DHUHR) + dhuhr.getValue() / 60d);

    return result;
  }


  // Section 2!! (Compute Prayer Time in JS code)
  //

  // get asr shadow factor
  private double asrFactor() {
    return asr == CalculationMethod.AsrJuristics.Hanafi ? 2d : 1d;
  }

  // return sun angle for sunset/sunrise
  private MinuteOrAngleDouble riseSetAngle() {
    // var earthRad = 6371009; // in meters
    // var angle = DMath.arccos(earthRad/(earthRad+ elv));
    double angle = 0.0347 * Math.sqrt(coordinate.getElevation()); // an
    // approximation
    return deg(0.833 + angle);
  }

  // adjust times for locations in higher latitudes
  private SparseArray<Double> adjustHighLats(SparseArray<Double> times) {
    double nightTime = timeDiff(times.get(Time.SUNSET),
      times.get(Time.SUNRISE));

    times.put(
      Time.IMSAK,
      adjustHLTime(times.get(Time.IMSAK),
        times.get(Time.SUNRISE), imsak.getValue(),
        nightTime, true));

    times.put(
      Time.FAJR,
      adjustHLTime(times.get(Time.FAJR), times
          .get(Time.SUNRISE), method.getFajr().getValue(),
        nightTime, true));

    times.put(
      Time.ISHA,
      adjustHLTime(times.get(Time.ISHA), times
          .get(Time.SUNSET), method.getIsha().getValue(),
        nightTime));

    times.put(
      Time.MAGHRIB,
      adjustHLTime(times.get(Time.MAGHRIB), times
          .get(Time.SUNSET), method.getMaghrib().getValue(),
        nightTime));

    return times;
  }

  // adjust a time for higher latitudes
  private double adjustHLTime(double time, double bbase, double angle,
                              double night, boolean ccw) {
    double portion = nightPortion(angle, night);
    double timeDiff = ccw ? timeDiff(time, bbase) : timeDiff(bbase, time);

    if (Double.isNaN(time) || timeDiff > portion)
      time = bbase + (ccw ? -portion : portion);
    return time;
  }

  private double adjustHLTime(double time, double bbase, double angle,
                              double night) {
    return adjustHLTime(time, bbase, angle, night, false);
  }

  // the night portion used for adjusting times in higher latitudes
  private double nightPortion(double angle, double night) {
    double portion = 1d / 2d;
    if (highLats == CalculationMethod.HighLatMethods.AngleBased) {
      portion = 1d / 60d * angle;
    }
    if (highLats == CalculationMethod.HighLatMethods.OneSeventh) {
      portion = 1 / 7;
    }
    return portion * night;
  }


  // compute the difference between two times
  private double timeDiff(double time1, double time2) {
    return fixHour(time2 - time1);
  }


  //
  // Misc Functions
  //
  //

  private class DeclEqt {

    private final double declination;
    private final double equation;


    public DeclEqt(double declination, double equation) {
      super();
      this.declination = declination;
      this.equation = equation;
    }


    public double getDeclination() {
      return declination;
    }


    public double getEquation() {
      return equation;
    }
  }

}
