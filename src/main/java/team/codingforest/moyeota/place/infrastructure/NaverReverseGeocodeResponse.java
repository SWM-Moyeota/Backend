// place/infrastructure/NaverReverseGeocodeResponse.java
package team.codingforest.moyeota.place.infrastructure;

import java.util.List;

record NaverReverseGeocodeResponse(Status status, List<Result> results) {

    record Status(int code) { }

    record Result(String name, Region region, Land land) { }

    record Region(Area area1, Area area2, Area area3, Area area4) { }

    record Area(String name) { }

    record Land(String name, String number1, String number2, Addition addition0) { }

    record Addition(String value) { }
}