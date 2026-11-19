package com.crmplatform.shared.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Postal address embedded in contacts, locations and projects. Country is ISO 3166-1 alpha-2. */
@Embeddable
public class Address {

    @Column(name = "address_line1", length = 200)
    private String line1;

    @Column(name = "address_line2", length = 200)
    private String line2;

    @Column(length = 100)
    private String city;

    @Column(length = 100)
    private String region;

    @Column(name = "postal_code", length = 20)
    private String postalCode;

    @Column(length = 2)
    private String country;

    protected Address() {
    }

    public Address(String line1, String line2, String city, String region, String postalCode, String country) {
        this.line1 = trim(line1);
        this.line2 = trim(line2);
        this.city = trim(city);
        this.region = trim(region);
        this.postalCode = trim(postalCode);
        this.country = country == null || country.isBlank() ? null : country.trim().toUpperCase();
    }

    public boolean isEmpty() {
        return Stream.of(line1, line2, city, region, postalCode, country).allMatch(v -> v == null || v.isBlank());
    }

    /** Single-line form used for geocoding. */
    public String oneLine() {
        return Stream.of(line1, line2, city, region, postalCode, country).filter(v -> v != null && !v.isBlank())
                .collect(Collectors.joining(", "));
    }

    private static String trim(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }

    public String getLine1() {
        return line1;
    }

    public String getLine2() {
        return line2;
    }

    public String getCity() {
        return city;
    }

    public String getRegion() {
        return region;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public String getCountry() {
        return country;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Address a && oneLine().equals(a.oneLine());
    }

    @Override
    public int hashCode() {
        return oneLine().hashCode();
    }
}
