package com.fietsrouten.pois.model;

public class Poi {
    private long id;
    private String name;
    private String amenity;
    private double lat;
    private double lon;

    public Poi(long id, String name, String amenity, double lat, double lon) {
        this.id = id;
        this.name = name;
        this.amenity = amenity;
        this.lat = lat;
        this.lon = lon;
    }

    public long getId()       { return id; }
    public String getName()   { return name; }
    public String getAmenity(){ return amenity; }
    public double getLat()    { return lat; }
    public double getLon()    { return lon; }
}
