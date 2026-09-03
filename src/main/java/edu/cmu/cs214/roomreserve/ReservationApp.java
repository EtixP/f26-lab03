package edu.cmu.cs214.roomreserve;

/**
 * Runs a short demo script against the handler.
 */
public class ReservationApp {

    public static void main(String[] args) {
        RequestHandler handler = new RequestHandler();
        String room = "WEH-5302";
        String date = "2026-09-11";

        System.out.println(handler.createBooking(room, date, "09:00", "10:00", "amal"));
        System.out.println(handler.createBooking(room, date, "10:00", "11:30", "bo"));
        System.out.println(handler.createBooking(room, date, "09:30", "10:30", "cass"));
        System.out.println();

        System.out.println(handler.listBookings(room, date));
        System.out.println();

        System.out.println(handler.rescheduleBooking(room, date, "10:00", "11:30", "13:00", "14:30"));
        System.out.println(handler.cancelBooking(room, date, "09:00", "10:00"));
        System.out.println(handler.cancelBooking(room, date, "16:00", "17:00"));
        System.out.println();

        System.out.println(handler.listBookings(room, date));
        System.out.println();

        System.out.println(handler.listBookings("GHC-4401", date));
        System.out.println(handler.createBooking("GHC-4401", date, "15:00", "16:00", "dee"));
        System.out.println(handler.createBooking("GHC-4401", date, "16:00", "17:00", "eli"));
        System.out.println(handler.listBookings("GHC-4401", date));
    }
}
