/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ir.afraapps.praytime.posAlgo;

public class Equatorial {

  public double a; //right ascension (α) -also RA-, or hour angle (H) -also HA-
  public double b; //declination (δ)
  public double c; //distance to the earth(Δ) in km


  Equatorial() {
  }


  Equatorial(double sunRightAscension, double sunDeclination) {
    this.a = sunRightAscension;
    this.b = sunDeclination;
  }


  Equatorial(double sunRightAscension, double sunDeclination, double radius) {
    this.a = sunRightAscension;
    this.b = sunDeclination;
    this.c = radius;
  }


  public Horizontal Equ2Topocentric(double longitude, double latitude, double Height, double jd, double ΔT) {

    double ϕ = Math.toRadians(latitude);
    double ρsinϕPr = ρsinϕPrime(ϕ, Height);
    double ρCosϕPr = ρCosϕPrime(ϕ, Height);

    //Calculate the Sidereal time

    //double ΔT = AstroLib.calculateTimeDifference(jd);
    double theta = SolarPosition.calculateGreenwichSiderealTime(jd, ΔT);

    //Convert δ to radians
    double brad = Math.toRadians(b);

    double cosb = Math.cos(brad);
    //  4.26345151167726E-5
    //Calculate the Parallax
    double π = getHorizontalParallax(c);
    double sinπ = Math.sin(π);

    //Calculate the hour angle
    double H = Math.toRadians(AstroLib.limitDegrees(theta + longitude - a));
    double cosH = Math.cos(H);
    double sinH = Math.sin(H);

    //Calculate the adjustment in right ascension
    double Δα = MATH.atan2(-ρCosϕPr * sinπ * sinH, cosb - ρCosϕPr * sinπ * cosH);

    Horizontal horizontal;
    horizontal = new Horizontal();
    //  CAA2DCoordinate Topocentric;
    //    double αPrime =Math.toRadians(α)+Δα;
    double δPrime = MATH.atan2((Math.sin(brad) - ρsinϕPr * sinπ) * Math.cos(Δα), cosb - ρCosϕPr * sinπ * cosH);
    double HPrime = H - Δα;

    horizontal.Az = Math.toDegrees(MATH.atan2(Math.sin(HPrime), Math.cos(HPrime) * Math.sin(ϕ) - Math.tan(δPrime) * Math.cos(ϕ)) + Math.PI);
    horizontal.h = Math.toDegrees(MATH.asin(Math.sin(ϕ) * Math.sin(δPrime) + Math.cos(ϕ) * Math.cos(δPrime) * Math.cos(HPrime)));
    return horizontal;

  }


  double ρsinϕPrime(double ϕ, double Height) {

    double U = MATH.atan(0.99664719 * Math.tan(ϕ));
    return 0.99664719 * Math.sin(U) + (Height / 6378149 * Math.sin(ϕ));
  }


  double ρCosϕPrime(double ϕ, double Height) {
    //Convert from degress to radians
    double U = MATH.atan(0.99664719 * Math.tan(ϕ));
    return Math.cos(U) + (Height / 6378149 * Math.cos(ϕ));

  }


  double getHorizontalParallax(double RadiusVector) {
    return MATH.asin(6378.14 / RadiusVector);
  }

}
