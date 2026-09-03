package edu.cmu.cs214.roomreserve;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RequestHandlerTest {

    private static final String ROOM = "WEH-5302";
    private static final String DATE = "2026-09-11";

    private RequestHandler handler;

    @BeforeEach
    void setUp() {
        handler = new RequestHandler();
    }

    @Test
    void createsABooking() {
        String response = handler.createBooking(ROOM, DATE, "09:00", "10:00", "amal");
        assertTrue(response.startsWith("OK"), response);
    }

    @Test
    void rejectsAnExactDuplicate() {
        handler.createBooking(ROOM, DATE, "09:00", "10:00", "amal");
        String response = handler.createBooking(ROOM, DATE, "09:00", "10:00", "bo");
        assertTrue(response.startsWith("ERROR"), response);
    }

    @Test
    void rejectsAnOverlappingBooking() {
        handler.createBooking(ROOM, DATE, "09:00", "10:00", "amal");
        String response = handler.createBooking(ROOM, DATE, "09:30", "10:30", "bo");
        assertTrue(response.startsWith("ERROR"), response);
    }

    @Test
    void cancelRemovesTheBooking() {
        handler.createBooking(ROOM, DATE, "09:00", "10:00", "amal");
        String response = handler.cancelBooking(ROOM, DATE, "09:00", "10:00");
        assertTrue(response.startsWith("OK"), response);
        assertTrue(handler.listBookings(ROOM, DATE).startsWith("no bookings"));
    }

    @Test
    void listShowsTheBookings() {
        handler.createBooking(ROOM, DATE, "09:00", "10:00", "amal");
        handler.createBooking(ROOM, DATE, "11:00", "12:00", "bo");
        String listing = handler.listBookings(ROOM, DATE);
        assertTrue(listing.contains("09:00 to 10:00"), listing);
        assertTrue(listing.contains("amal"), listing);
        assertTrue(listing.contains("11:00 to 12:00"), listing);
        assertTrue(listing.contains("bo"), listing);
    }

    @Test
    void rescheduleMovesABooking() {
        handler.createBooking(ROOM, DATE, "09:00", "10:00", "amal");
        String response = handler.rescheduleBooking(ROOM, DATE, "09:00", "10:00", "14:00", "15:00");
        assertTrue(response.startsWith("OK"), response);
        String listing = handler.listBookings(ROOM, DATE);
        assertTrue(listing.contains("14:00 to 15:00"), listing);
        assertTrue(!listing.contains("09:00 to 10:00"), listing);
    }
}
